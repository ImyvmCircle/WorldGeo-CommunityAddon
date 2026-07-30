package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.transaction.CombinationStepFact
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import com.imyvm.community.infra.TeleportDailyState
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

internal data class TeleportPlan(
    val operationId: UUID,
    val regionId: Int,
    val playerUuid: UUID,
    val playerName: String,
    val scopeName: String,
    val dayKey: String,
    val usageCountAtFreeze: Int,
    val cost: Long,
    val createdAtMillis: Long
) {
    fun encode(): String {
        val encodedName = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(playerName.toByteArray(StandardCharsets.UTF_8))
        val encodedScope = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(scopeName.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            TELEPORT_EVIDENCE_PREFIX, operationId, regionId, playerUuid,
            encodedScope, dayKey, usageCountAtFreeze, cost, createdAtMillis, encodedName
        ).joinToString("|")
    }

    companion object {
        fun decode(value: String): TeleportPlan? {
            val fields = value.split('|')
            if (fields.size != 10 || fields[0] != TELEPORT_EVIDENCE_PREFIX) return null
            return runCatching {
                TeleportPlan(
                    UUID.fromString(fields[1]),
                    fields[2].toInt(),
                    UUID.fromString(fields[3]),
                    String(Base64.getUrlDecoder().decode(fields[9]), StandardCharsets.UTF_8),
                    String(Base64.getUrlDecoder().decode(fields[4]), StandardCharsets.UTF_8),
                    fields[5],
                    fields[6].toInt(),
                    fields[7].toLong(),
                    fields[8].toLong()
                )
            }.getOrNull()
        }
    }
}

fun registerTeleportAccountRecovery() {
    AccountSubsystem.onReady { runtime -> scanTeleportRecovery(runtime, null) }
}

internal fun persistTeleportPlan(
    player: ServerPlayer,
    regionId: Int,
    scopeName: String,
    usageCountAtFreeze: Int,
    cost: Long,
    onPersisted: (TeleportPlan) -> Unit
) {
    val runtime = AccountSubsystem.runtimeOrNull() ?: run {
        WorldGeoCommunityAddon.logger.warn("Account subsystem not ready; teleport rejected for ${player.name.string}")
        TeleportDailyState.release(player.uuid, regionId)
        return
    }
    val plan = TeleportPlan(
        UUID.randomUUID(), regionId, player.uuid, player.gameProfile.name,
        scopeName, TeleportDailyState.currentDayKey(), usageCountAtFreeze, cost, System.currentTimeMillis()
    )
    runtime.sharedStore.append(teleportStep(plan, PLAN_STEP, PLAN_STEP, CombinationStepStatus.DETERMINED))
        .whenComplete { _, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to persist teleport plan ${plan.operationId}", error)
                    TeleportDailyState.release(player.uuid, regionId)
                    runtime.server.playerList.getPlayer(player.uuid)?.sendSystemMessage(
                        Translator.tr("community.teleport.execution.error.persist_failed")
                    )
                } else {
                    onPersisted(plan)
                }
            }
        }
}

internal fun cancelTeleportPlan(playerUuid: UUID, plan: TeleportPlan) {
    val runtime = AccountSubsystem.runtimeOrNull() ?: run {
        TeleportDailyState.release(playerUuid, plan.regionId)
        return
    }
    appendTeleportStep(
        runtime,
        teleportStep(plan, PLAN_STEP, PLAN_STEP, CombinationStepStatus.COMPENSATED),
        plan
    ) { TeleportDailyState.release(playerUuid, plan.regionId) }
}

internal fun driveTeleportAfterPlanPersisted(
    runtime: AccountSubsystem.Runtime,
    plan: TeleportPlan,
    onDebitSucceeded: () -> Unit
) {
    if (plan.cost == 0L) {
        onDebitSucceeded()
        return
    }
    val transaction = teleportTransaction(plan)
    runtime.service.submit(transaction) { state ->
        when (state.status) {
            AccountTransactionStatus.SUCCEEDED -> appendTeleportStep(
                runtime,
                teleportStep(plan, DEBIT_STEP, "money", CombinationStepStatus.SUCCEEDED, transaction.shortId),
                plan
            ) { onDebitSucceeded() }
            AccountTransactionStatus.RESOLVED -> {
                runtime.server.playerList.getPlayer(plan.playerUuid)?.let { player ->
                    player.sendSystemMessage(
                        Translator.tr(
                            "community.teleport.execution.error.insufficient_balance",
                            String.format("%.2f", plan.cost / 100.0),
                            "0.00"
                        )
                    )
                    player.closeContainer()
                }
                appendTeleportStep(
                    runtime,
                    teleportStep(plan, DEBIT_STEP, "money", CombinationStepStatus.COMPENSATED, transaction.shortId),
                    plan
                ) { closeTeleportPlan(runtime, plan, compensated = true) }
            }
            else -> Unit
        }
    }.exceptionally { error ->
        WorldGeoCommunityAddon.logger.error("Failed to submit teleport debit ${plan.operationId}", error)
        null
    }
}

internal fun recordTeleportCallStarted(
    runtime: AccountSubsystem.Runtime,
    plan: TeleportPlan,
    completed: () -> Unit
) {
    appendTeleportStep(
        runtime,
        teleportStep(plan, EXECUTE_STEP, "worldgeo", CombinationStepStatus.CALL_STARTED),
        plan,
        completed
    )
}

internal fun recordTeleportSucceeded(runtime: AccountSubsystem.Runtime, plan: TeleportPlan) {
    appendTeleportStep(
        runtime,
        teleportStep(plan, EXECUTE_STEP, "worldgeo", CombinationStepStatus.SUCCEEDED),
        plan
    ) { closeTeleportPlan(runtime, plan, compensated = false) }
}

internal fun issueAutoRefund(runtime: AccountSubsystem.Runtime, plan: TeleportPlan) {
    if (plan.cost == 0L) {
        closeTeleportPlan(runtime, plan, compensated = true)
        return
    }
    val refundTransaction = teleportRefundTransaction(plan)
    runtime.service.submit(refundTransaction) { state ->
        when (state.status) {
            AccountTransactionStatus.SUCCEEDED -> appendTeleportStep(
                runtime,
                teleportStep(plan, REFUND_STEP, "money", CombinationStepStatus.SUCCEEDED, refundTransaction.shortId),
                plan
            ) { closeTeleportPlan(runtime, plan, compensated = true) }
            AccountTransactionStatus.RESOLVED ->
                WorldGeoCommunityAddon.logger.error(
                    "Teleport refund unexpectedly resolved for ${plan.operationId}; count slot retained"
                )
            else -> Unit
        }
    }.exceptionally { error ->
        WorldGeoCommunityAddon.logger.error("Failed to submit teleport refund ${plan.operationId}", error)
        null
    }
}

private fun closeTeleportPlan(
    runtime: AccountSubsystem.Runtime,
    plan: TeleportPlan,
    compensated: Boolean
) {
    if (compensated) TeleportDailyState.release(plan.playerUuid, plan.regionId)
    val closeStatus = if (compensated) CombinationStepStatus.COMPENSATED else CombinationStepStatus.SUCCEEDED
    appendTeleportStep(
        runtime,
        teleportStep(plan, PLAN_STEP, PLAN_STEP, closeStatus),
        plan
    ) {}
}

private fun scanTeleportRecovery(runtime: AccountSubsystem.Runtime, token: String?) {
    runtime.sharedStore.scanUnresolvedOperations(token, RECOVERY_PAGE_SIZE)
        .whenComplete { page, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to scan unresolved teleport operations", error)
                    return@execute
                }
                page.operationIds.forEach { operationId -> recoverTeleportOperation(runtime, operationId) }
                if (page.nextToken != null && page.operationIds.size == RECOVERY_PAGE_SIZE) {
                    scanTeleportRecovery(runtime, page.nextToken)
                }
            }
        }
}

private fun recoverTeleportOperation(runtime: AccountSubsystem.Runtime, operationId: UUID) {
    runtime.sharedStore.scanOperation(operationId, null, MAX_OPERATION_FACTS)
        .whenComplete { facts, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to read teleport operation $operationId", error)
                    return@execute
                }
                val steps = facts.items.filterIsInstance<CombinationStepFact>()
                val plan = steps.mapNotNull { it.evidence?.let(TeleportPlan::decode) }
                    .firstOrNull() ?: return@execute
                if (plan.operationId != operationId) return@execute
                driveTeleportRecovery(runtime, plan, steps)
            }
        }
}

private fun driveTeleportRecovery(
    runtime: AccountSubsystem.Runtime,
    plan: TeleportPlan,
    steps: List<CombinationStepFact>
) {
    val latest = steps.groupBy { it.stepKey }
        .mapValues { (_, v) -> v.maxByOrNull { it.recordedAtMillis }!! }
    val planStatus = latest[PLAN_STEP]?.status ?: CombinationStepStatus.DETERMINED
    if (planStatus.isTerminal()) return

    val debitStatus = latest[DEBIT_STEP]?.status
    val executeStatus = latest[EXECUTE_STEP]?.status
    val refundStatus = latest[REFUND_STEP]?.status

    when {
        refundStatus == CombinationStepStatus.SUCCEEDED ->
            closeTeleportPlan(runtime, plan, compensated = true)

        executeStatus == CombinationStepStatus.SUCCEEDED ->
            closeTeleportPlan(runtime, plan, compensated = false)

        debitStatus == CombinationStepStatus.SUCCEEDED ->
            issueAutoRefund(runtime, plan)

        debitStatus != null && debitStatus.isTerminal() ->
            closeTeleportPlan(runtime, plan, compensated = true)

        debitStatus == null && plan.cost == 0L && executeStatus != null ->
            issueAutoRefund(runtime, plan)

        else ->
            closeTeleportPlan(runtime, plan, compensated = true)
    }
}

private fun appendTeleportStep(
    runtime: AccountSubsystem.Runtime,
    fact: CombinationStepFact,
    plan: TeleportPlan,
    completed: () -> Unit = {}
) {
    runtime.sharedStore.append(fact).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                WorldGeoCommunityAddon.logger.error("Failed to advance teleport ${plan.operationId}", error)
            } else {
                completed()
            }
        }
    }
}

internal fun teleportTransaction(plan: TeleportPlan): AccountTransaction {
    val id = stableTeleportId(plan, "account-debit")
    return AccountTransaction(
        id, id.toString().replace("-", "").take(12), plan.createdAtMillis,
        "teleport:${plan.regionId}", plan.playerUuid, plan.playerName, plan.cost,
        AccountDirection.DEBIT, "teleport", debitReference(plan)
    )
}

private fun teleportRefundTransaction(plan: TeleportPlan): AccountTransaction {
    val id = stableTeleportId(plan, "account-refund")
    return AccountTransaction(
        id, id.toString().replace("-", "").take(12), plan.createdAtMillis,
        "teleport-refund:${plan.regionId}", plan.playerUuid, plan.playerName, plan.cost,
        AccountDirection.CREDIT, "teleport-refund", refundReference(plan)
    )
}

internal fun teleportStep(
    plan: TeleportPlan,
    stepKey: String,
    resource: String,
    status: CombinationStepStatus,
    evidence: String? = null
) = CombinationStepFact(
    stableTeleportId(plan, "step:$stepKey:${status.name}"),
    plan.regionId,
    plan.createdAtMillis,
    plan.operationId,
    stepKey,
    resource,
    when (stepKey) {
        PLAN_STEP -> planReference(plan)
        DEBIT_STEP -> debitReference(plan)
        EXECUTE_STEP -> executeReference(plan)
        REFUND_STEP -> refundReference(plan)
        else -> planReference(plan)
    },
    status,
    evidence ?: if (status == CombinationStepStatus.DETERMINED) plan.encode() else null
)

private fun stableTeleportId(plan: TeleportPlan, purpose: String): UUID =
    UUID.nameUUIDFromBytes("teleport:${plan.operationId}:$purpose".toByteArray(StandardCharsets.UTF_8))

private fun planReference(plan: TeleportPlan) = "community:teleport:plan:${plan.operationId}"
private fun debitReference(plan: TeleportPlan) = "community:teleport:debit:${plan.operationId}"
private fun executeReference(plan: TeleportPlan) = "community:teleport:execute:${plan.operationId}"
private fun refundReference(plan: TeleportPlan) = "community:teleport:refund:${plan.operationId}"

const val TELEPORT_EVIDENCE_PREFIX = "teleport:v1"
private const val PLAN_STEP = "teleport-plan"
private const val DEBIT_STEP = "teleport-account-debit"
private const val EXECUTE_STEP = "teleport-worldgeo-execute"
private const val REFUND_STEP = "teleport-account-refund"
private const val RECOVERY_PAGE_SIZE = 128
private const val MAX_OPERATION_FACTS = 32
