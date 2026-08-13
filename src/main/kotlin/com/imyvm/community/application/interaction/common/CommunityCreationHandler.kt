package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.event.movePendingOperation
import com.imyvm.community.application.event.removePendingOperationPersisted
import com.imyvm.community.application.interaction.common.helper.calculateCreationCost
import com.imyvm.community.application.interaction.common.helper.checkPlayerMembershipCreation
import com.imyvm.community.application.interaction.common.helper.generateCreationConfirmationMessage
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.CreationConfirmationData
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.transaction.CombinationStepFact
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import com.imyvm.community.application.event.getPendingOperation
import com.imyvm.community.application.event.removePendingOperation
import java.nio.charset.StandardCharsets
import java.util.UUID

fun onCreateCommunityRequest(
    player: ServerPlayer,
    communityType: String,
    communityName: String,
    requestedShapeType: GeoShapeType? = null
): Int {
    if (!checkPlayerMembershipCreation(player, communityType)) return 0
    if (!communityType.equals("manor", ignoreCase = true) && !communityType.equals("realm", ignoreCase = true)) {
        player.sendSystemMessage(Translator.tr("community.create.error.invalid_type"))
        return 0
    }

    val existingPending = WorldGeoCommunityAddon.pendingOperations.values.find {
        it.type in setOf(
            PendingOperationType.CREATE_COMMUNITY_CONFIRMATION,
            PendingOperationType.CREATE_COMMUNITY_EXECUTION
        ) &&
        it.creationData?.creatorUUID == player.uuid
    }
    if (existingPending != null) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.pending"))
        return 0
    }

    if (requestedShapeType != null && ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)) {
        if (PlayerInteractionApi.setSelectionShape(player, requestedShapeType) == 0) return 0
    }

    val shapeType = when (val hs = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape) {
        is HypotheticalShape.Normal -> hs.shapeType
        else -> requestedShapeType ?: GeoShapeType.RECTANGLE
    }

    val region = PlayerInteractionApi.createAndGetRegion(player, communityName, idMark = 2)
    if (region == null) {
        player.sendSystemMessage(Translator.tr("community.create.region.error"))
        return 0
    }

    val isManor = communityType.equals("manor", ignoreCase = true)
    val costResult = calculateCreationCost(region, isManor)

    val regionNumberId = region.numberID
    val actualShapeType = region.geometryScope.firstOrNull()?.geoShape?.geoShapeType ?: shapeType

    val confirmationMessages = generateCreationConfirmationMessage(
        communityName = communityName,
        geoShapeType = actualShapeType,
        isManor = isManor,
        costResult = costResult
    )
    confirmationMessages.forEach { msg ->
        player.sendSystemMessage(msg)
    }

    try {
        addPendingOperation(
            regionId = regionNumberId,
            type = PendingOperationType.CREATE_COMMUNITY_CONFIRMATION,
            expireMinutes = 5,
            creationData = CreationConfirmationData(
                communityName = communityName,
                communityType = communityType,
                shapeName = actualShapeType.name,
                regionNumberId = regionNumberId,
                creatorUUID = player.uuid,
                totalCost = costResult.totalCost
            )
        )

        sendInteractiveConfirmation(player, regionNumberId)
    } catch (e: Exception) {
        removePendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)
        deleteCreationRegion(regionNumberId, player)
        WorldGeoCommunityAddon.logger.error("Failed to prepare community creation confirmation for region $regionNumberId", e)
        player.sendSystemMessage(Translator.tr("community.create.confirmation.failed"))
        return 0
    }

    return 1
}


fun onConfirmCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    val pendingOp = getPendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)

    if (pendingOp == null) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_found"))
        return 0
    }

    val creationData = pendingOp.creationData
    if (creationData == null || creationData.creatorUUID != player.uuid) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_yours"))
        return 0
    }

    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.expired"))
        return 0
    }

    val runtime = AccountSubsystem.runtimeOrNull()
    if (runtime == null) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.failed"))
        return 0
    }

    val execution = try {
        movePendingOperation(
            regionNumberId,
            PendingOperationType.CREATE_COMMUNITY_CONFIRMATION,
            PendingOperationType.CREATE_COMMUNITY_EXECUTION
        )
    } catch (error: Exception) {
        WorldGeoCommunityAddon.logger.error("Failed to persist community creation execution for region $regionNumberId", error)
        player.sendSystemMessage(Translator.tr("community.create.confirmation.failed"))
        return 0
    }

    submitCommunityCreationDebit(runtime, execution, player.gameProfile.name)
    return 1
}

fun registerCommunityCreationAccountRecovery() {
    AccountSubsystem.onReady { runtime ->
        WorldGeoCommunityAddon.pendingOperations.values
            .filter { it.type == PendingOperationType.CREATE_COMMUNITY_EXECUTION && it.creationData != null }
            .toList()
            .forEach { resumeCommunityCreation(runtime, it) }
    }
}

private fun resumeCommunityCreation(runtime: AccountSubsystem.Runtime, execution: PendingOperation) {
    val data = execution.creationData ?: return
    if (CommunityDatabase.communities.any { it.regionNumberId == data.regionNumberId }) {
        finishCommunityCreationState(runtime, execution)
        return
    }
    runtime.sharedStore.findLatestOperationStep(communityCreationOperationId(execution), REFUND_STEP)
        .whenComplete { refundStep, error ->
            runtime.server.execute {
                when {
                    error != null -> WorldGeoCommunityAddon.logger.error(
                        "Failed to inspect community creation recovery for region ${data.regionNumberId}", error
                    )
                    refundStep?.status == CombinationStepStatus.NEEDS_OP -> Unit
                    refundStep?.status == CombinationStepStatus.COMPENSATED -> cleanupCommunityCreation(runtime, execution)
                    refundStep != null -> submitCommunityCreationRefund(runtime, execution)
                    else -> submitCommunityCreationDebit(runtime, execution, null)
                }
            }
        }
}

private fun submitCommunityCreationDebit(
    runtime: AccountSubsystem.Runtime,
    execution: PendingOperation,
    subjectName: String?
) {
    val data = execution.creationData ?: return
    appendCommunityCreationStep(runtime, execution, DEBIT_STEP, CombinationStepStatus.DETERMINED)
        .whenComplete { _, factError ->
            runtime.server.execute {
                if (factError != null) {
                    WorldGeoCommunityAddon.logger.error(
                        "Failed to determine community creation debit for region ${data.regionNumberId}", factError
                    )
                    return@execute
                }
                val transaction = communityCreationTransaction(execution, AccountDirection.DEBIT, subjectName)
                runtime.service.submit(transaction) { state ->
                    when (state.status) {
                        AccountTransactionStatus.SUCCEEDED -> {
                            val currentPlayer = runtime.server.playerList.getPlayer(data.creatorUUID)
                            currentPlayer?.sendSystemMessage(
                                Translator.tr("community.create.money.checked", data.totalCost / 100.0)
                            )
                            appendCommunityCreationStep(
                                runtime, execution, DEBIT_STEP, CombinationStepStatus.SUCCEEDED,
                                transaction.shortId
                            ).whenComplete { _, successError ->
                                runtime.server.execute {
                                    if (successError != null) {
                                        WorldGeoCommunityAddon.logger.error(
                                            "Failed to record community creation debit for region ${data.regionNumberId}",
                                            successError
                                        )
                                    } else {
                                        beginCommunityCreationState(runtime, execution, currentPlayer)
                                    }
                                }
                            }
                        }
                        AccountTransactionStatus.RESOLVED -> {
                            appendCommunityCreationStep(
                                runtime, execution, DEBIT_STEP, CombinationStepStatus.COMPENSATED,
                                transaction.shortId
                            ).whenComplete { _, resolvedError ->
                                runtime.server.execute {
                                    if (resolvedError != null) {
                                        WorldGeoCommunityAddon.logger.error(
                                            "Failed to record rejected community creation debit for region ${data.regionNumberId}",
                                            resolvedError
                                        )
                                    } else {
                                        runtime.server.playerList.getPlayer(data.creatorUUID)?.sendSystemMessage(
                                            Translator.tr("community.create.money.error", data.totalCost / 100.0)
                                        )
                                        cleanupCommunityCreation(runtime, execution)
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }.exceptionally { submitError ->
                    WorldGeoCommunityAddon.logger.error(
                        "Failed to submit community creation debit for region ${data.regionNumberId}", submitError
                    )
                    null
                }
            }
        }
}

private fun beginCommunityCreationState(
    runtime: AccountSubsystem.Runtime,
    execution: PendingOperation,
    player: ServerPlayer?
) {
    val data = execution.creationData ?: return
    appendCommunityCreationStep(runtime, execution, STATE_STEP, CombinationStepStatus.CALL_STARTED)
        .whenComplete { _, factError ->
            runtime.server.execute {
                if (factError != null) {
                    WorldGeoCommunityAddon.logger.error(
                        "Failed to start community creation state for region ${data.regionNumberId}", factError
                    )
                    return@execute
                }
                if (getPendingOperation(data.regionNumberId, PendingOperationType.CREATE_COMMUNITY_EXECUTION) == null) {
                    return@execute
                }
                if (CommunityDatabase.communities.any { it.regionNumberId == data.regionNumberId }) {
                    finishCommunityCreationState(runtime, execution)
                    return@execute
                }

                var community: Community? = null
                var branchType: PendingOperationType? = null
                try {
                    community = initialRequest(
                        player, data.creatorUUID, data.communityName, data.communityType,
                        data.regionNumberId, data.totalCost
                    )
                    branchType = handleRequestBranches(player, data.communityType, data.regionNumberId)
                    CommunityDatabase.save()
                    finishCommunityCreationState(runtime, execution)
                } catch (error: Exception) {
                    community?.let(CommunityDatabase::removeCommunity)
                    branchType?.let { removePendingOperation(data.regionNumberId, it) }
                    try {
                        CommunityDatabase.save()
                    } catch (rollbackError: Exception) {
                        error.addSuppressed(rollbackError)
                        WorldGeoCommunityAddon.logger.error(
                            "Failed to roll back community creation state for region ${data.regionNumberId}", error
                        )
                        return@execute
                    }
                    WorldGeoCommunityAddon.logger.error(
                        "Failed to create community state for region ${data.regionNumberId}", error
                    )
                    player?.sendSystemMessage(Translator.tr("community.create.confirmation.failed"))
                    submitCommunityCreationRefund(runtime, execution)
                }
            }
        }
}

private fun finishCommunityCreationState(runtime: AccountSubsystem.Runtime, execution: PendingOperation) {
    val data = execution.creationData ?: return
    appendCommunityCreationStep(runtime, execution, STATE_STEP, CombinationStepStatus.SUCCEEDED)
        .whenComplete { _, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error(
                        "Failed to record community creation state for region ${data.regionNumberId}", error
                    )
                    return@execute
                }
                try {
                    removePendingOperationPersisted(data.regionNumberId, PendingOperationType.CREATE_COMMUNITY_EXECUTION)
                } catch (cleanupError: Exception) {
                    WorldGeoCommunityAddon.logger.error(
                        "Failed to finalize community creation for region ${data.regionNumberId}", cleanupError
                    )
                }
            }
        }
}

private fun submitCommunityCreationRefund(runtime: AccountSubsystem.Runtime, execution: PendingOperation) {
    val data = execution.creationData ?: return
    appendCommunityCreationStep(runtime, execution, REFUND_STEP, CombinationStepStatus.DETERMINED)
        .whenComplete { _, factError ->
            runtime.server.execute {
                if (factError != null) {
                    WorldGeoCommunityAddon.logger.error(
                        "Failed to determine community creation refund for region ${data.regionNumberId}", factError
                    )
                    return@execute
                }
                val transaction = communityCreationTransaction(execution, AccountDirection.CREDIT, null)
                runtime.service.submit(transaction) { state ->
                    when (state.status) {
                        AccountTransactionStatus.SUCCEEDED -> appendCommunityCreationStep(
                            runtime, execution, REFUND_STEP, CombinationStepStatus.COMPENSATED,
                            transaction.shortId
                        ).whenComplete { _, refundError ->
                            runtime.server.execute {
                                if (refundError != null) {
                                    WorldGeoCommunityAddon.logger.error(
                                        "Failed to record community creation refund for region ${data.regionNumberId}",
                                        refundError
                                    )
                                } else {
                                    cleanupCommunityCreation(runtime, execution)
                                }
                            }
                        }
                        AccountTransactionStatus.RESOLVED -> appendCommunityCreationStep(
                            runtime, execution, REFUND_STEP, CombinationStepStatus.NEEDS_OP,
                            transaction.shortId
                        )
                        else -> Unit
                    }
                }.exceptionally { submitError ->
                    WorldGeoCommunityAddon.logger.error(
                        "Failed to submit community creation refund for region ${data.regionNumberId}", submitError
                    )
                    null
                }
            }
        }
}

private fun cleanupCommunityCreation(runtime: AccountSubsystem.Runtime, execution: PendingOperation) {
    val data = execution.creationData ?: return
    if (!deleteCreationRegion(data.regionNumberId, runtime.server.playerList.getPlayer(data.creatorUUID))) return
    try {
        removePendingOperationPersisted(data.regionNumberId, PendingOperationType.CREATE_COMMUNITY_EXECUTION)
    } catch (error: Exception) {
        WorldGeoCommunityAddon.logger.error(
            "Failed to clean community creation execution for region ${data.regionNumberId}", error
        )
    }
}

private fun appendCommunityCreationStep(
    runtime: AccountSubsystem.Runtime,
    execution: PendingOperation,
    stepKey: String,
    status: CombinationStepStatus,
    evidence: String? = null
) = runtime.sharedStore.append(communityCreationStep(execution, stepKey, status, evidence))

internal fun communityCreationOperationId(execution: PendingOperation): UUID =
    stableCommunityCreationId("operation", execution)

internal fun communityCreationTransaction(
    execution: PendingOperation,
    direction: AccountDirection,
    subjectName: String?
): AccountTransaction {
    val data = requireNotNull(execution.creationData)
    val purpose = if (direction == AccountDirection.DEBIT) "debit" else "refund"
    val transactionId = stableCommunityCreationId("account:$purpose", execution)
    return AccountTransaction(
        transactionId = transactionId,
        shortId = transactionId.toString().replace("-", "").take(12),
        createdAtMillis = (execution.expireAt - CONFIRMATION_MILLIS).coerceAtLeast(0L),
        periodKey = "community-create:${data.regionNumberId}",
        subjectUuid = data.creatorUUID,
        subjectName = subjectName,
        amount = data.totalCost,
        direction = direction,
        source = "community_creation",
        externalReference = "community:create:$purpose:${data.regionNumberId}:${data.creatorUUID}:${execution.expireAt}",
        previousTransactionId = if (direction == AccountDirection.CREDIT) {
            stableCommunityCreationId("account:debit", execution)
        } else {
            null
        }
    )
}

internal fun communityCreationStep(
    execution: PendingOperation,
    stepKey: String,
    status: CombinationStepStatus,
    evidence: String? = null
): CombinationStepFact {
    val data = requireNotNull(execution.creationData)
    return CombinationStepFact(
        factId = stableCommunityCreationId("step:$stepKey:${status.name}", execution),
        regionId = data.regionNumberId,
        recordedAtMillis = (execution.expireAt - CONFIRMATION_MILLIS).coerceAtLeast(0L),
        operationId = communityCreationOperationId(execution),
        stepKey = stepKey,
        resource = if (stepKey == STATE_STEP) "community" else "money",
        externalReference = if (stepKey == STATE_STEP) {
            "community:create:state:${data.regionNumberId}:${data.creatorUUID}:${execution.expireAt}"
        } else {
            communityCreationTransaction(
                execution,
                if (stepKey == DEBIT_STEP) AccountDirection.DEBIT else AccountDirection.CREDIT,
                null
            ).externalReference
        },
        status = status,
        evidence = evidence
    )
}

private fun stableCommunityCreationId(purpose: String, execution: PendingOperation): UUID {
    val data = requireNotNull(execution.creationData)
    return UUID.nameUUIDFromBytes(
        "community:create:$purpose:${data.regionNumberId}:${data.creatorUUID}:${execution.expireAt}"
            .toByteArray(StandardCharsets.UTF_8)
    )
}

private const val DEBIT_STEP = "account-debit"
private const val STATE_STEP = "community-state"
private const val REFUND_STEP = "account-refund"
private const val CONFIRMATION_MILLIS = 5 * 60 * 1000L


fun onCancelCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    return cancelCommunityCreation(player, regionNumberId)
}

private fun cancelCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    val pendingOp = getPendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)

    if (pendingOp == null || pendingOp.type != PendingOperationType.CREATE_COMMUNITY_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_found"))
        return 0
    }

    val creationData = pendingOp.creationData
    if (creationData == null || creationData.creatorUUID != player.uuid) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_yours"))
        return 0
    }

    if (!deleteCreationRegion(regionNumberId, player)) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.failed"))
        return 0
    }

    removePendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)

    player.sendSystemMessage(Translator.tr("community.create.confirmation.cancelled"))
    return 1
}

internal fun deleteCreationRegion(regionNumberId: Int, player: ServerPlayer? = null): Boolean {
    val region = RegionDataApi.getRegion(regionNumberId) ?: return true

    if (player != null) {
        try {
            PlayerInteractionApi.deleteRegion(player, region)
            if (RegionDataApi.getRegion(regionNumberId) == null) return true
            WorldGeoCommunityAddon.logger.warn("Core API did not remove creation region $regionNumberId; falling back to RegionDatabase")
        } catch (e: Exception) {
            WorldGeoCommunityAddon.logger.warn("Core API failed to remove creation region $regionNumberId; falling back to RegionDatabase", e)
        }
    }

    return try {
        RegionDatabase.removeRegion(region)
        RegionDatabase.save()
        WorldGeoCommunityAddon.logger.info("Deleted creation region $regionNumberId by RegionDatabase fallback")
        true
    } catch (e: Exception) {
        WorldGeoCommunityAddon.logger.error("Failed to delete creation region $regionNumberId", e)
        false
    }
}

private fun initialRequest(
    player: ServerPlayer?,
    creatorUUID: UUID,
    name: String,
    communityType: String,
    regionNumberId: Int,
    creationCost: Long
): Community {
    val community = Community(
        regionNumberId = regionNumberId,
        member = hashMapOf(creatorUUID to MemberAccount(
            joinedTime = System.currentTimeMillis(),
            basicRoleType = MemberRoleType.OWNER
        )),
        joinPolicy = CommunityJoinPolicy.OPEN,
        status = if (communityType.equals("manor", ignoreCase = true)) {
            CommunityStatus.PENDING_MANOR
        } else {
            CommunityStatus.RECRUITING_REALM
        },
        creationCost = creationCost
    )

    CommunityDatabase.addCommunity(community)
    player?.sendSystemMessage(Translator.tr("community.create.request.initial.success", name, community.regionNumberId))
    return community
}

private fun handleRequestBranches(player: ServerPlayer?, communityType: String, regionNumberId: Int): PendingOperationType? {
    if (communityType.equals("manor", ignoreCase = true)) {
        player?.sendSystemMessage(Translator.tr("community.create.request.sent"))
        addPendingOperation(
            regionId = regionNumberId,
            type = PendingOperationType.AUDITING_COMMUNITY_REQUEST,
            expireHours = CommunityConfig.AUDITING_EXPIRE_HOURS.value
        )
        if (player != null) notifyOPsAndOwnerAboutCreationRequest(player, regionNumberId)
        return PendingOperationType.AUDITING_COMMUNITY_REQUEST
    } else if (communityType.equals("realm", ignoreCase = true)) {
        player?.sendSystemMessage(Translator.tr("community.create.request.recruitment", CommunityConfig.MIN_NUMBER_MEMBER_REALM.value))
        addPendingOperation(
            regionId = regionNumberId,
            type = PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT,
            expireHours = CommunityConfig.REALM_REQUEST_EXPIRE_HOURS.value
        )
        return PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT
    }
    return null
}

internal fun notifyOPsAndOwnerAboutCreationRequest(creator: ServerPlayer, regionNumberId: Int) {
    val message = Translator.tr(
        "community.create.notification.new_request",
        creator.name.string,
        regionNumberId
    )
    
    creator.level().server.playerList.players.forEach { player ->
        if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()) || player.uuid == creator.uuid) {
            player.sendSystemMessage(message)
        }
    }
}

private fun sendInteractiveConfirmation(player: ServerPlayer, regionNumberId: Int) {
    val confirmButton = Translator.tr("community.create.confirmation.button.confirm")
        .copy()
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun confirm_creation $regionNumberId"))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.create.confirmation.button.confirm.hover")))
        }

    val cancelButton = Translator.tr("community.create.confirmation.button.cancel")
        .copy()
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun cancel_creation $regionNumberId"))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.create.confirmation.button.cancel.hover")))
        }

    val promptMessage = Component.empty()
        .append(Translator.tr("community.create.confirmation.interactive_prompt"))
        .append(confirmButton)
        .append(Component.literal(" "))
        .append(cancelButton)

    player.sendSystemMessage(promptMessage)
}
