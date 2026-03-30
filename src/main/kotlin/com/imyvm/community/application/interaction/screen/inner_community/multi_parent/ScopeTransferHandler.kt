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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Component

fun runTransferScopeFromGlobalMenu(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
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
            player.closeContainer()
            player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
            return@executeWithPermission
        }
        if (region.geometryScope.size <= 1) {
            player.closeContainer()
            player.sendSystemMessage(Translator.tr("community.scope_transfer.error.last_scope"))
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
    player: ServerPlayer,
    sourceCommunity: Community,
    scope: GeoScope,
    targetCommunity: Community,
    runBack: (ServerPlayer) -> Unit
) {
    val existingPending = WorldGeoCommunityAddon.pendingOperations[sourceCommunity.regionNumberId]
    if (existingPending != null) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.pending"))
        return
    }

    val targetRegion = targetCommunity.getRegion()
    if (targetRegion == null) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("community.scope_transfer.error.target_no_region"))
        return
    }

    val eligibleOnlineMembers = getEligibleGrantRecipients(targetCommunity, player.level().server)
    if (eligibleOnlineMembers.isEmpty()) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("community.scope_transfer.error.no_eligible_online"))
        return
    }

    val sourceRegionId = sourceCommunity.regionNumberId ?: return
    val targetRegionId = targetCommunity.regionNumberId ?: return

    player.closeContainer()

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
    ).filterNotNull().forEach { player.sendSystemMessage(it) }

    sendCancelButtonToInitiator(player, sourceRegionId, scopeName)

    // Notify all eligible online target community members with accept/decline buttons
    eligibleOnlineMembers.forEach { targetMember ->
        sendGrantRequestToTargetMember(targetMember, sourceRegionId, scopeName, sourceName, targetName)
    }
}

private fun sendCancelButtonToInitiator(
    player: ServerPlayer,
    sourceRegionId: Int,
    scopeName: String
) {
    val quotedScopeName = quoteScopeName(scopeName)

    val cancelButton = Component.literal("§c§l[取消赠予]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun cancel_territory_grant $sourceRegionId $quotedScopeName")).withHoverEvent(HoverEvent.ShowText(Component.literal("§c撤销领土赠予请求")
            ))
        }

    val msg = Component.empty()
        .append(cancelButton)

    player.sendSystemMessage(msg)
}

private fun sendGrantRequestToTargetMember(
    targetMember: ServerPlayer,
    sourceRegionId: Int,
    scopeName: String,
    sourceName: String,
    targetName: String
) {
    val quotedScopeName = quoteScopeName(scopeName)

    targetMember.sendSystemMessage(
        Translator.tr("community.scope_transfer.incoming", sourceName, scopeName, targetName)
    )

    val acceptButton = Component.literal("§a§l[接受]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun accept_territory_grant $sourceRegionId $quotedScopeName")).withHoverEvent(HoverEvent.ShowText(Component.literal("§a接受领土赠予")
            ))
        }

    val declineButton = Component.literal("§c§l[拒绝]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun decline_territory_grant $sourceRegionId $quotedScopeName")).withHoverEvent(HoverEvent.ShowText(Component.literal("§c拒绝领土赠予")
            ))
        }

    val promptMessage = Component.empty()
        .append(Component.literal("§e§l[领土赠予请求]§r §e请在 §c§l5分钟§r§e 内决定："))
        .append(acceptButton)
        .append(Component.literal(" "))
        .append(declineButton)

    targetMember.sendSystemMessage(promptMessage)
}

private fun quoteScopeName(scopeName: String): String =
    if (!scopeName.all { it.isLetterOrDigit() && it.code < 128 }) "\"$scopeName\"" else scopeName
