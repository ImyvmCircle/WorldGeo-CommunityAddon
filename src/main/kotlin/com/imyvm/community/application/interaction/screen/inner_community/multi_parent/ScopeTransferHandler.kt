package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.common.getEligibleGrantRecipients
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.ScopeTransferConfirmationData
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text

fun runTransferScopeFromGlobalMenu(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        {
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
            if (adminCheck.isDenied()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
        }
    ) {
        val region = community.getRegion()
        if (region == null) {
            player.closeHandledScreen()
            player.sendMessage(Translator.tr("community.modification.error.no_region"))
            return@executeWithPermission
        }
        if (region.geometryScope.size <= 1) {
            player.closeHandledScreen()
            player.sendMessage(Translator.tr("community.scope_transfer.error.last_scope"))
            return@executeWithPermission
        }
        CommunityMenuOpener.open(player) { syncId ->
            CommunityRegionScopeMenu(
                syncId = syncId,
                playerExecutor = player,
                community = community,
                geographicFunctionType = GeographicFunctionType.SCOPE_TRANSFER,
                runBack = runBack
            )
        }
    }
}

fun runTransferScopeToTarget(
    player: ServerPlayerEntity,
    sourceCommunity: Community,
    scope: GeoScope,
    targetCommunity: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val existingPending = WorldGeoCommunityAddon.pendingOperations[sourceCommunity.regionNumberId]
    if (existingPending != null) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("community.modification.confirmation.pending"))
        return
    }

    val targetRegion = targetCommunity.getRegion()
    if (targetRegion == null) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("community.scope_transfer.error.target_no_region"))
        return
    }

    val eligibleOnlineMembers = getEligibleGrantRecipients(targetCommunity, player.server)
    if (eligibleOnlineMembers.isEmpty()) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("community.scope_transfer.error.no_eligible_online"))
        return
    }

    val sourceRegionId = sourceCommunity.regionNumberId ?: return
    val targetRegionId = targetCommunity.regionNumberId ?: return

    player.closeHandledScreen()

    val scopeArea = RegionDataApi.getScopeArea(scope) ?: 0.0
    val areaDisplay = String.format("%.2f", scopeArea)
    val sourceName = sourceCommunity.generateCommunityMark()
    val targetName = targetCommunity.generateCommunityMark()
    val scopeName = scope.scopeName

    addPendingOperation(
        regionId = sourceRegionId,
        type = PendingOperationType.TRANSFER_SCOPE_CONFIRMATION,
        expireMinutes = 5,
        transferData = ScopeTransferConfirmationData(
            sourceRegionNumberId = sourceRegionId,
            scopeName = scopeName,
            executorUUID = player.uuid,
            targetRegionNumberId = targetRegionId
        )
    )

    // Notify the initiator that the request has been sent
    listOf(
        Translator.tr("community.scope_transfer.confirm.header"),
        Translator.tr("community.scope_transfer.confirm.scope", scopeName),
        Translator.tr("community.scope_transfer.confirm.area", areaDisplay),
        Translator.tr("community.scope_transfer.confirm.source", sourceName),
        Translator.tr("community.scope_transfer.confirm.target", targetName),
        Translator.tr("community.scope_transfer.sent"),
    ).filterNotNull().forEach { player.sendMessage(it) }

    sendCancelButtonToInitiator(player, sourceRegionId, scopeName)

    // Notify all eligible online target community members with accept/decline buttons
    eligibleOnlineMembers.forEach { targetMember ->
        sendGrantRequestToTargetMember(targetMember, sourceRegionId, scopeName, sourceName, targetName)
    }
}

private fun sendCancelButtonToInitiator(
    player: ServerPlayerEntity,
    sourceRegionId: Int,
    scopeName: String
) {
    val quotedScopeName = quoteScopeName(scopeName)

    val cancelButton = Text.literal("§c§l[取消赠予]§r")
        .styled { style ->
            style.withClickEvent(ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/commun cancel_territory_grant $sourceRegionId $quotedScopeName"
            )).withHoverEvent(HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("§c撤销领土赠予请求")
            ))
        }

    val msg = Text.empty()
        .append(cancelButton)

    player.sendMessage(msg)
}

private fun sendGrantRequestToTargetMember(
    targetMember: ServerPlayerEntity,
    sourceRegionId: Int,
    scopeName: String,
    sourceName: String,
    targetName: String
) {
    val quotedScopeName = quoteScopeName(scopeName)

    targetMember.sendMessage(
        Translator.tr("community.scope_transfer.incoming", sourceName, scopeName, targetName)
    )

    val acceptButton = Text.literal("§a§l[接受]§r")
        .styled { style ->
            style.withClickEvent(ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/commun accept_territory_grant $sourceRegionId $quotedScopeName"
            )).withHoverEvent(HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("§a接受领土赠予")
            ))
        }

    val declineButton = Text.literal("§c§l[拒绝]§r")
        .styled { style ->
            style.withClickEvent(ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/commun decline_territory_grant $sourceRegionId $quotedScopeName"
            )).withHoverEvent(HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("§c拒绝领土赠予")
            ))
        }

    val promptMessage = Text.empty()
        .append(Text.literal("§e§l[领土赠予请求]§r §e请在 §c§l5分钟§r§e 内决定："))
        .append(acceptButton)
        .append(Text.literal(" "))
        .append(declineButton)

    targetMember.sendMessage(promptMessage)
}

private fun quoteScopeName(scopeName: String): String =
    if (!scopeName.all { it.isLetterOrDigit() && it.code < 128 }) "\"$scopeName\"" else scopeName
