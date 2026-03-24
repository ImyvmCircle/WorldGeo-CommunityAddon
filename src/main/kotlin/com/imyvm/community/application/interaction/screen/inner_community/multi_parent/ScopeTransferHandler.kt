package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.ScopeTransferConfirmationData
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.ScopeTransferTargetListMenu
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

    val sourceRegionId = sourceCommunity.regionNumberId ?: return
    val targetRegionId = targetCommunity.regionNumberId ?: return

    player.closeHandledScreen()

    val scopeArea = RegionDataApi.getScopeArea(scope) ?: 0.0
    val areaDisplay = String.format("%.2f", scopeArea)
    val sourceName = sourceCommunity.generateCommunityMark()
    val targetName = targetCommunity.generateCommunityMark()
    val scopeName = scope.scopeName

    listOf(
        Translator.tr("community.scope_transfer.confirm.header")
            ?: Text.literal("§e§l====== 转移辖区确认 ======"),
        Translator.tr("community.scope_transfer.confirm.scope", scopeName)
            ?: Text.literal("§f§l辖区：§r §b$scopeName"),
        Translator.tr("community.scope_transfer.confirm.area", areaDisplay)
            ?: Text.literal("§f§l辖区面积：§r §b$areaDisplay m²"),
        Translator.tr("community.scope_transfer.confirm.source", sourceName)
            ?: Text.literal("§f§l来源聚落：§r §e$sourceName"),
        Translator.tr("community.scope_transfer.confirm.target", targetName)
            ?: Text.literal("§f§l目标聚落：§r §a$targetName"),
        Translator.tr("community.scope_transfer.confirm.prompt")
            ?: Text.literal("§e§l[!]§r §e请确认将此辖区转移给目标聚落，此操作不可撤销。"),
    ).forEach { player.sendMessage(it) }

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

    sendInteractiveScopeTransferConfirmation(player, sourceRegionId, scopeName)
}

private fun sendInteractiveScopeTransferConfirmation(
    player: ServerPlayerEntity,
    sourceRegionId: Int,
    scopeName: String
) {
    val quotedScopeName = if (!scopeName.all { it.isLetterOrDigit() && it.code < 128 }) "\"$scopeName\"" else scopeName

    val confirmButton = Text.literal("§a§l[CONFIRM]§r")
        .styled { style ->
            style.withClickEvent(ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/commun confirm_transfer_scope $sourceRegionId $quotedScopeName"
            )).withHoverEvent(HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("§aClick to confirm transfer")
            ))
        }

    val cancelButton = Text.literal("§c§l[CANCEL]§r")
        .styled { style ->
            style.withClickEvent(ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/commun cancel_transfer_scope $sourceRegionId $quotedScopeName"
            )).withHoverEvent(HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("§cClick to cancel")
            ))
        }

    val promptMessage = Text.empty()
        .append(Text.literal("§e§l[ACTION REQUIRED]§r §ePlease confirm within §c§l5 minutes§r§e: "))
        .append(confirmButton)
        .append(Text.literal(" "))
        .append(cancelButton)

    player.sendMessage(promptMessage)
}
