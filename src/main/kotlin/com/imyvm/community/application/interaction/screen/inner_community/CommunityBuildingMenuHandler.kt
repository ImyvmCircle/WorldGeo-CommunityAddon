package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.event.getPendingOperation
import com.imyvm.community.application.event.removePendingOperationPersisted
import com.imyvm.community.application.event.restorePendingOperation
import com.imyvm.community.application.helper.CommunityBackgroundTasks
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
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import java.util.Collections

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
    runCatching {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityBuildingMenu(syncId, player, community, runBackGrandfather)
        }
    }.onFailure { error ->
        WorldGeoCommunityAddon.logger.error("Failed to open community building menu for ${community.regionNumberId}", error)
        player.sendSystemMessage(Translator.tr("community.building.menu.open_failed", error.message ?: error::class.java.simpleName))
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
    val pending = CommunityBuildingService.createPendingSelectionData(community, player.uuid, draft.baseBlockId, cost.getOrThrow())
    if (pending.isFailure) {
        player.sendSystemMessage(Translator.tr("community.building.error.save", pending.exceptionOrNull()?.message ?: "error"))
        runOpenCommunityBuildingEditor(player, community, draft, runBack)
        return
    }
    startBuildingConfirmation(player, community, pending.getOrThrow())
    CommunityBuildingService.clearDraft(player.uuid)
}

fun runRemoveCommunityBuildingEntry(player: ServerPlayer, community: Community, baseBlockId: String, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    val pending = CommunityBuildingService.createPendingRemovalData(community, player.uuid, baseBlockId)
    if (pending.isFailure) {
        player.sendSystemMessage(Translator.tr("community.building.confirm.failed", pending.exceptionOrNull()?.message ?: "error"))
        return
    }
    startBuildingConfirmation(player, community, pending.getOrThrow())
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
    startBuildingConfirmation(player, community, CommunityBuildingService.createPendingCapacityData(community, player.uuid, buyUnits, cost.getOrThrow()).getOrThrow())
}

private fun adminPermission(player: ServerPlayer, community: Community) =
    CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_BUILDING).let { adminCheck ->
        if (adminCheck.isDenied()) adminCheck
        else CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_BUILDING)
    }

fun runConfirmCommunityBuildingOperation(player: ServerPlayer, community: Community): Int {
    val regionId = community.regionNumberId
    if (regionId != null && activeBuildingOperations.contains(regionId)) {
        player.sendSystemMessage(Translator.tr("community.building.confirm.running", community.generateCommunityMark()))
        return 0
    }
    val operation = getPendingOperation(regionId, PendingOperationType.BUILDING_CONFIRMATION)
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
    if (!activeBuildingOperations.add(data.regionNumberId)) {
        player.sendSystemMessage(Translator.tr("community.building.confirm.running", community.generateCommunityMark()))
        return 0
    }
    removePendingOperationPersisted(data.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION)
    player.closeContainer()
    val description = buildingOperationDescription(data)
    player.sendSystemMessage(Translator.tr("community.building.confirm.started", community.generateCommunityMark(), description, CommunityBuildingService.formatMoney(data.cost)))
    val server = player.level().server
    val executorUuid = player.uuid
    val work = when (data.action) {
        "select" -> CommunityBuildingService.prepareSelectionCheckpointAsync(community, requireNotNull(data.baseBlockId))
        "remove" -> CommunityBuildingService.prepareRemovalCheckpointAsync(community, requireNotNull(data.baseBlockId))
        "capacity" -> CommunityBackgroundTasks.supply { Result.success("") }
        else -> CommunityBackgroundTasks.supply { Result.failure(IllegalStateException("unknown building operation")) }
    }
    work.whenComplete { checkpointResult, error ->
        server.execute {
            activeBuildingOperations.remove(data.regionNumberId)
            val online = server.playerList.getPlayer(executorUuid)
            val prepared = if (error != null) Result.failure(error) else checkpointResult
            val targetCommunity = CommunityDatabase.getCommunityById(data.regionNumberId)
            if (targetCommunity == null) {
                online?.sendSystemMessage(Translator.tr("community.building.confirm.failed", "community not found"))
                return@execute
            }
            val lateReject = validatePendingExecution(operation, targetCommunity, executorUuid, operation.expireAt.coerceAtMost(System.currentTimeMillis()))
            if (lateReject == PendingExecutionRejectReason.COMMUNITY_ORPHANED || lateReject == PendingExecutionRejectReason.COMMUNITY_REVOKED) {
                online?.sendSystemMessage(rejectMessage(lateReject))
                return@execute
            }
            val result = prepared.fold(
                onSuccess = { checkpoint ->
                    when (data.action) {
                        "select" -> CommunityBuildingService.upsertEntryWithCheckpoint(targetCommunity, requireNotNull(data.baseBlockId), checkpoint).map { data.cost }
                        "remove" -> CommunityBuildingService.removeEntryWithCheckpoint(targetCommunity, requireNotNull(data.baseBlockId), checkpoint).map { 0L }
                        "capacity" -> CommunityBuildingService.buyCapacity(targetCommunity, data.buyUnits)
                        else -> Result.failure(IllegalStateException("unknown building operation"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
            if (result.isSuccess) {
                online?.sendSystemMessage(Translator.tr("community.building.confirm.success", buildingOperationDescription(data), CommunityBuildingService.formatMoney(result.getOrThrow())))
                return@execute
            }
            restorePendingOperation(data.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION, operation)
            runCatching { CommunityDatabase.save() }
            online?.sendSystemMessage(Translator.tr("community.building.confirm.failed", result.exceptionOrNull()?.message ?: "error"))
        }
    }
    return 1
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

private val activeBuildingOperations = Collections.synchronizedSet(mutableSetOf<Int>())

private fun startBuildingConfirmation(player: ServerPlayer, community: Community, data: BuildingConfirmationData) {
    val existing = getPendingOperation(community.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION)
    if (existing != null) {
        if (existing.expireAt > System.currentTimeMillis()) {
            player.closeContainer()
            player.sendSystemMessage(Translator.tr("community.building.confirm.pending", community.regionNumberId?.toString() ?: ""))
            return
        }
        removePendingOperationPersisted(data.regionNumberId, PendingOperationType.BUILDING_CONFIRMATION)
    }
    addPendingOperation(
        regionId = data.regionNumberId,
        type = PendingOperationType.BUILDING_CONFIRMATION,
        expireMinutes = 5,
        buildingData = data
    )
    player.closeContainer()
    player.sendSystemMessage(Translator.tr("community.building.confirm.sent", buildingOperationDescription(data), CommunityBuildingService.formatMoney(data.cost)))
    sendInteractiveBuildingConfirmation(player, community)
}

private fun sendInteractiveBuildingConfirmation(player: ServerPlayer, community: Community) {
    val communityId = community.regionNumberId ?: return
    val confirmButton = Translator.tr("community.building.confirm.button.confirm").copy().withStyle { style ->
        style.withClickEvent(ClickEvent.RunCommand("/community building confirm $communityId"))
            .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.building.confirm.button.confirm.hover")))
    }
    val cancelButton = Translator.tr("community.building.confirm.button.cancel").copy().withStyle { style ->
        style.withClickEvent(ClickEvent.RunCommand("/community building cancel $communityId"))
            .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.building.confirm.button.cancel.hover")))
    }
    player.sendSystemMessage(
        Component.empty()
            .append(Translator.tr("community.building.confirm.prompt"))
            .append(confirmButton)
            .append(Component.literal(" "))
            .append(cancelButton)
    )
}

private fun buildingOperationDescription(data: BuildingConfirmationData): String = when (data.action) {
    "select" -> Translator.tr("community.building.operation.select", data.baseBlockId ?: "-").string
    "remove" -> Translator.tr("community.building.operation.remove", data.baseBlockId ?: "-").string
    "capacity" -> Translator.tr("community.building.operation.capacity", data.buyUnits.toString()).string
    else -> data.action
}

private fun rejectMessage(reason: PendingExecutionRejectReason) = when (reason) {
    PendingExecutionRejectReason.EXPIRED -> Translator.tr("community.building.confirm.expired")
    PendingExecutionRejectReason.EXECUTOR_CHANGED -> Translator.tr("community.building.confirm.executor_changed")
    PendingExecutionRejectReason.COMMUNITY_ORPHANED,
    PendingExecutionRejectReason.COMMUNITY_REVOKED -> Translator.tr("community.building.confirm.invalid_community")
}
