package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.event.getPendingOperation
import com.imyvm.community.application.event.removePendingOperationPersisted
import com.imyvm.community.application.event.restorePendingOperation
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.townbuilding.CommunityBuildingDraft
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.BuildingConfirmationData
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingExecutionRejectReason
import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.validatePendingExecution
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingCandidateListMenu
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingEditorMenu
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingMenu
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingPoolMenu
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingStyleListMenu
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer

fun runSendCommunityBuildingAdministrationSummary(player: ServerPlayer, community: Community) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    player.closeContainer()
    CommunityBuildingService.sendAdministrationSummary(player, community)
}

fun runOpenCommunityBuildingPoolMenu(player: ServerPlayer, page: Int, runBack: (ServerPlayer) -> Unit) {
    if (!net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
        player.sendSystemMessage(Translator.tr("command.community.permission.op_required"))
        return
    }
    CommunityMenuOpener.open(player) { syncId -> CommunityBuildingPoolMenu(syncId, player, page, runBack) }
}

fun runOpenCommunityBuildingMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    val permission = CommunityPermissionPolicy.canViewCommunity(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    CommunityMenuOpener.open(player) { syncId ->
        CommunityBuildingMenu(syncId, player, community, runBackGrandfather)
    }
}

fun runOpenCommunityBuildingStyleList(player: ServerPlayer, community: Community, adminMode: Boolean, page: Int, runBack: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityBuildingStyleListMenu(syncId, player, community, adminMode, page, runBack)
    }
}

fun runOpenCommunityBuildingCandidates(player: ServerPlayer, community: Community, page: Int, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    CommunityMenuOpener.open(player) { syncId ->
        CommunityBuildingCandidateListMenu(syncId, player, community, page, runBack)
    }
}

fun runOpenCommunityBuildingEditor(player: ServerPlayer, community: Community, draft: CommunityBuildingDraft, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    CommunityBuildingService.setDraft(player.uuid, draft)
    CommunityMenuOpener.open(player) { syncId ->
        CommunityBuildingEditorMenu(syncId, player, community, runBack)
    }
}

fun runSaveCommunityBuildingDraft(player: ServerPlayer, community: Community, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    val draft = CommunityBuildingService.getDraft(player.uuid) ?: return
    val cost = CommunityBuildingService.previewSelectionCost(community, draft.baseBlockId)
    if (cost.isFailure) {
        player.sendSystemMessage(Translator.tr("community.building.error.save", cost.exceptionOrNull()?.message ?: "error"))
        runOpenCommunityBuildingEditor(player, community, draft, runBack)
        return
    }
    startBuildingConfirmation(player, community, BuildingConfirmationData(community.regionNumberId ?: return, player.uuid, "select", draft.baseBlockId, 0, cost.getOrThrow()))
    CommunityBuildingService.clearDraft(player.uuid)
}

fun runRemoveCommunityBuildingEntry(player: ServerPlayer, community: Community, baseBlockId: String, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    startBuildingConfirmation(player, community, BuildingConfirmationData(community.regionNumberId ?: return, player.uuid, "remove", baseBlockId, 0, 0L))
}

fun runBuyCommunityBuildingCapacity(player: ServerPlayer, community: Community, buyUnits: Int, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    val cost = runCatching { CommunityBuildingService.calculateCapacityPurchaseCost(community.buildingState.capacityUnits, buyUnits) }
    if (cost.isFailure) {
        player.sendSystemMessage(Translator.tr("community.building.error.capacity", cost.exceptionOrNull()?.message ?: "error"))
        runOpenCommunityBuildingMenu(player, community, runBack)
        return
    }
    startBuildingConfirmation(player, community, BuildingConfirmationData(community.regionNumberId ?: return, player.uuid, "capacity", null, buyUnits, cost.getOrThrow()))
}

private fun adminPermission(player: ServerPlayer, community: Community) =
    CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_BUILDING).let { adminCheck ->
        if (adminCheck.isDenied()) adminCheck
        else CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_BUILDING)
    }

fun runConfirmCommunityBuildingOperation(player: ServerPlayer, community: Community): Int {
    val operation = getPendingOperation(community.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION)
    val data = operation?.buildingData
    if (operation == null || data == null) {
        player.sendSystemMessage(Translator.tr("community.building.confirm.not_found"))
        return 0
    }
    val reject = validatePendingExecution(operation, community, player.uuid)
    if (reject != null) {
        player.sendSystemMessage(rejectMessage(reject))
        removePendingOperationPersisted(data.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION)
        return 0
    }
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return 0
    }
    removePendingOperationPersisted(data.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION)
    val result = when (data.action) {
        "select" -> CommunityBuildingService.upsertEntry(community, requireNotNull(data.baseBlockId), 0, 0L, emptyList()).map { data.cost }
        "remove" -> CommunityBuildingService.removeEntry(community, requireNotNull(data.baseBlockId)).map { 0L }
        "capacity" -> CommunityBuildingService.buyCapacity(community, data.buyUnits)
        else -> Result.failure(IllegalStateException("unknown building operation"))
    }
    if (result.isSuccess) {
        player.sendSystemMessage(Translator.tr("community.building.confirm.success", data.action, CommunityBuildingService.formatMoney(result.getOrThrow())))
        return 1
    }
    restorePendingOperation(data.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION, operation)
    runCatching { CommunityDatabase.save() }
    player.sendSystemMessage(Translator.tr("community.building.confirm.failed", result.exceptionOrNull()?.message ?: "error"))
    return 0
}

fun runCancelCommunityBuildingOperation(player: ServerPlayer, community: Community): Int {
    val operation = getPendingOperation(community.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION)
    val data = operation?.buildingData
    if (operation == null || data == null) {
        player.sendSystemMessage(Translator.tr("community.building.confirm.not_found"))
        return 0
    }
    if (data.executorUUID != player.uuid) {
        player.sendSystemMessage(Translator.tr("community.building.confirm.executor_changed"))
        return 0
    }
    removePendingOperationPersisted(data.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION)
    player.sendSystemMessage(Translator.tr("community.building.confirm.cancelled"))
    return 1
}

private fun startBuildingConfirmation(player: ServerPlayer, community: Community, data: BuildingConfirmationData) {
    if (getPendingOperation(community.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION) != null) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.pending"))
        return
    }
    addPendingOperation(
        regionId = data.regionNumberId,
        type = PendingOperationType.BUILDING_CONFIRMATION,
        expireMinutes = 5,
        buildingData = data
    )
    player.closeContainer()
    player.sendSystemMessage(Translator.tr("community.building.confirm.sent", data.action, CommunityBuildingService.formatMoney(data.cost)))
    player.sendSystemMessage(Translator.tr("community.building.confirm.commands", community.generateCommunityMark()))
}

private fun rejectMessage(reason: PendingExecutionRejectReason) = when (reason) {
    PendingExecutionRejectReason.EXPIRED -> Translator.tr("community.building.confirm.expired")
    PendingExecutionRejectReason.EXECUTOR_CHANGED -> Translator.tr("community.building.confirm.executor_changed")
    PendingExecutionRejectReason.COMMUNITY_ORPHANED,
    PendingExecutionRejectReason.COMMUNITY_REVOKED -> Translator.tr("community.building.confirm.invalid_community")
}
