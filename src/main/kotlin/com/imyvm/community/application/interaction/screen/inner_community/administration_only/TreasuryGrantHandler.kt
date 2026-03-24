package com.imyvm.community.application.interaction.screen.inner_community.administration_only

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.TreasuryGrantConfirmationData
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.TreasuryGrantAmountMenu
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text

fun runOpenTreasuryGrantAmountMenu(
    player: ServerPlayerEntity,
    sourceCommunity: Community,
    targetCommunity: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        TreasuryGrantAmountMenu(syncId, player, sourceCommunity, targetCommunity, runBack)
    }
}

fun runGrantCoinsToTarget(
    player: ServerPlayerEntity,
    sourceCommunity: Community,
    targetCommunity: Community,
    amount: Long,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val existingPending = WorldGeoCommunityAddon.pendingOperations[sourceCommunity.regionNumberId]
    if (existingPending != null) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("community.modification.confirmation.pending"))
        return
    }

    val currentAssets = sourceCommunity.getTotalAssets()
    if (currentAssets < amount) {
        player.closeHandledScreen()
        val amountFormatted = "%.2f".format(amount / 100.0)
        val assetsFormatted = "%.2f".format(currentAssets / 100.0)
        player.sendMessage(Translator.tr("community.treasury_grant.error.insufficient_assets", amountFormatted, assetsFormatted))
        return
    }

    val eligibleOnlineMembers = getEligibleTreasuryGrantRecipients(targetCommunity, player.server)
    if (eligibleOnlineMembers.isEmpty()) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("community.treasury_grant.error.no_eligible_online"))
        return
    }

    val sourceRegionId = sourceCommunity.regionNumberId ?: return
    val targetRegionId = targetCommunity.regionNumberId ?: return

    player.closeHandledScreen()

    val amountFormatted = "%.2f".format(amount / 100.0)
    val sourceName = sourceCommunity.generateCommunityMark()
    val targetName = targetCommunity.generateCommunityMark()

    addPendingOperation(
        regionId = sourceRegionId,
        type = PendingOperationType.TREASURY_GRANT_CONFIRMATION,
        expireMinutes = 5,
        treasuryGrantData = TreasuryGrantConfirmationData(
            sourceRegionNumberId = sourceRegionId,
            targetRegionNumberId = targetRegionId,
            executorUUID = player.uuid,
            amount = amount
        )
    )

    listOf(
        Translator.tr("community.treasury_grant.confirm.header"),
        Translator.tr("community.treasury_grant.confirm.amount", amountFormatted),
        Translator.tr("community.treasury_grant.confirm.source", sourceName),
        Translator.tr("community.treasury_grant.confirm.target", targetName),
        Translator.tr("community.treasury_grant.sent"),
    ).filterNotNull().forEach { player.sendMessage(it) }

    sendCancelButtonToInitiator(player, sourceRegionId)

    eligibleOnlineMembers.forEach { targetMember ->
        sendGrantRequestToTargetMember(targetMember, sourceRegionId, amountFormatted, sourceName, targetName)
    }
}

fun onAcceptTreasuryGrant(player: ServerPlayerEntity, sourceRegionId: Int): Int {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[sourceRegionId]
    if (pendingOp == null || pendingOp.type != PendingOperationType.TREASURY_GRANT_CONFIRMATION) {
        player.sendMessage(Translator.tr("community.treasury_grant.confirmation.not_found"))
        return 0
    }

    val grantData = pendingOp.treasuryGrantData
    if (grantData == null) {
        player.sendMessage(Translator.tr("community.treasury_grant.confirmation.not_found"))
        return 0
    }

    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendMessage(Translator.tr("community.treasury_grant.confirmation.expired"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    val targetCommunity = CommunityDatabase.getCommunityById(grantData.targetRegionNumberId)
    if (targetCommunity == null) {
        player.sendMessage(Translator.tr("community.treasury_grant.error.target_not_found"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    if (!isEligibleTreasuryGrantRecipient(player, targetCommunity)) {
        player.sendMessage(Translator.tr("community.treasury_grant.confirmation.not_eligible"))
        return 0
    }

    val sourceCommunity = CommunityDatabase.getCommunityById(sourceRegionId)
    if (sourceCommunity == null) {
        player.sendMessage(Translator.tr("community.modification.error.community_not_found"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    val currentAssets = sourceCommunity.getTotalAssets()
    if (currentAssets < grantData.amount) {
        val amountFormatted = "%.2f".format(grantData.amount / 100.0)
        val assetsFormatted = "%.2f".format(currentAssets / 100.0)
        player.sendMessage(Translator.tr("community.treasury_grant.error.insufficient_assets", amountFormatted, assetsFormatted))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    val now = System.currentTimeMillis()
    val sourceMark = sourceCommunity.generateCommunityMark()
    val targetMark = targetCommunity.generateCommunityMark()
    sourceCommunity.expenditures.add(Turnover(grantData.amount, now, TurnoverSource.COMMUNITY_GRANT, "community.treasury.desc.grant_out", listOf(targetMark)))
    targetCommunity.communityIncome.add(Turnover(grantData.amount, now, TurnoverSource.COMMUNITY_GRANT, "community.treasury.desc.grant_in", listOf(sourceMark)))

    WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
    CommunityDatabase.save()

    val amountFormatted = "%.2f".format(grantData.amount / 100.0)

    player.sendMessage(Translator.tr("community.treasury_grant.success", amountFormatted, sourceMark, targetMark))

    val notification = Translator.tr(
        "community.notification.treasury_granted",
        amountFormatted,
        sourceMark,
        targetMark,
        player.name.string
    ) ?: Text.literal("§6§l[国库赠予]§r §e$sourceMark §e赠予了 §a§l$targetMark §e共 §6§l\$$amountFormatted§r §e（接受者：§d§l${player.name.string}§r§e）")

    notifyFormalMembers(sourceCommunity, player.server, notification)
    notifyFormalMembers(targetCommunity, player.server, notification)

    return 1
}

fun onDeclineTreasuryGrant(player: ServerPlayerEntity, sourceRegionId: Int): Int {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[sourceRegionId]
    if (pendingOp == null || pendingOp.type != PendingOperationType.TREASURY_GRANT_CONFIRMATION) {
        player.sendMessage(Translator.tr("community.treasury_grant.confirmation.not_found"))
        return 0
    }

    val grantData = pendingOp.treasuryGrantData
    if (grantData == null) {
        player.sendMessage(Translator.tr("community.treasury_grant.confirmation.not_found"))
        return 0
    }

    val targetCommunity = CommunityDatabase.getCommunityById(grantData.targetRegionNumberId)
    if (targetCommunity != null && !isEligibleTreasuryGrantRecipient(player, targetCommunity)) {
        player.sendMessage(Translator.tr("community.treasury_grant.confirmation.not_eligible"))
        return 0
    }

    WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
    player.sendMessage(Translator.tr("community.treasury_grant.confirmation.declined"))

    player.server.playerManager.getPlayer(grantData.executorUUID)?.sendMessage(
        Translator.tr("community.treasury_grant.confirmation.declined_notify", player.name.string)
    )
    return 1
}

fun onCancelTreasuryGrant(player: ServerPlayerEntity, sourceRegionId: Int): Int {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[sourceRegionId]
    if (pendingOp == null || pendingOp.type != PendingOperationType.TREASURY_GRANT_CONFIRMATION) {
        player.sendMessage(Translator.tr("community.treasury_grant.confirmation.not_found"))
        return 0
    }

    val grantData = pendingOp.treasuryGrantData
    if (grantData == null || grantData.executorUUID != player.uuid) {
        player.sendMessage(Translator.tr("community.modification.confirmation.not_yours"))
        return 0
    }

    WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
    player.sendMessage(Translator.tr("community.treasury_grant.confirmation.cancelled"))

    val targetCommunity = CommunityDatabase.getCommunityById(grantData.targetRegionNumberId)
    if (targetCommunity != null) {
        val cancelMsg = Translator.tr("community.treasury_grant.confirmation.cancelled_notify", player.name.string)
        getEligibleTreasuryGrantRecipients(targetCommunity, player.server).forEach { it.sendMessage(cancelMsg) }
    }
    return 1
}

fun isEligibleTreasuryGrantRecipient(player: ServerPlayerEntity, community: Community): Boolean {
    val account = community.member[player.uuid] ?: return false
    return when (account.basicRoleType) {
        MemberRoleType.OWNER -> true
        MemberRoleType.ADMIN -> account.adminPrivileges?.isEnabled(AdminPrivilege.GRANT_COINS_FROM_TREASURY) == true
        else -> false
    }
}

fun getEligibleTreasuryGrantRecipients(community: Community, server: MinecraftServer): List<ServerPlayerEntity> {
    return server.playerManager.playerList.filter { isEligibleTreasuryGrantRecipient(it, community) }
}

private fun notifyFormalMembers(community: Community, server: MinecraftServer, message: Text?) {
    if (message == null) return
    community.member.keys.forEach { uuid ->
        server.playerManager.getPlayer(uuid)?.sendMessage(message)
    }
}

private fun sendCancelButtonToInitiator(player: ServerPlayerEntity, sourceRegionId: Int) {
    val cancelButton = Text.literal("§c§l[取消赠予]§r")
        .styled { style ->
            style.withClickEvent(ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/commun cancel_treasury_grant $sourceRegionId"
            )).withHoverEvent(HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("§c撤销国库赠予请求")
            ))
        }

    player.sendMessage(Text.empty().append(cancelButton))
}

private fun sendGrantRequestToTargetMember(
    targetMember: ServerPlayerEntity,
    sourceRegionId: Int,
    amountFormatted: String,
    sourceName: String,
    targetName: String
) {
    targetMember.sendMessage(
        Translator.tr("community.treasury_grant.incoming", sourceName, amountFormatted, targetName)
    )

    val acceptButton = Text.literal("§a§l[接受]§r")
        .styled { style ->
            style.withClickEvent(ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/commun accept_treasury_grant $sourceRegionId"
            )).withHoverEvent(HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("§a接受国库赠予")
            ))
        }

    val declineButton = Text.literal("§c§l[拒绝]§r")
        .styled { style ->
            style.withClickEvent(ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/commun decline_treasury_grant $sourceRegionId"
            )).withHoverEvent(HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("§c拒绝国库赠予")
            ))
        }

    val promptMessage = Text.empty()
        .append(Text.literal("§6§l[国库赠予请求]§r §e请在 §c§l5分钟§r§e 内决定："))
        .append(acceptButton)
        .append(Text.literal(" "))
        .append(declineButton)

    targetMember.sendMessage(promptMessage)
}
