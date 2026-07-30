package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.transaction.CombinationStepFact
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.util.Translator
import com.mojang.authlib.GameProfile
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

internal data class ApplicationRefundPlan(
    val operationId: UUID,
    val regionId: Int,
    val playerUuid: UUID,
    val playerName: String,
    val amount: Long,
    val refusedAtMillis: Long
) {
    fun encode(): String {
        val name = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(playerName.toByteArray(StandardCharsets.UTF_8))
        return listOf(REFUND_EVIDENCE_PREFIX, operationId, regionId, playerUuid, amount, refusedAtMillis, name)
            .joinToString("|")
    }

    companion object {
        fun create(regionId: Int, profile: GameProfile, amount: Long, refusedAtMillis: Long): ApplicationRefundPlan {
            val operationId = stableRefundId(
                "operation:$regionId:${profile.id}:$amount:$refusedAtMillis"
            )
            return ApplicationRefundPlan(
                operationId, regionId, profile.id, profile.name, amount, refusedAtMillis
            )
        }

        fun decode(value: String): ApplicationRefundPlan? {
            val fields = value.split('|')
            if (fields.size != 7 || fields[0] != REFUND_EVIDENCE_PREFIX) return null
            return runCatching {
                ApplicationRefundPlan(
                    UUID.fromString(fields[1]),
                    fields[2].toInt(),
                    UUID.fromString(fields[3]),
                    String(Base64.getUrlDecoder().decode(fields[6]), StandardCharsets.UTF_8),
                    fields[4].toLong(),
                    fields[5].toLong()
                )
            }.getOrNull()
        }
    }
}

fun registerApplicationRefundRecovery() {
    AccountSubsystem.onReady { runtime ->
        runCatching { CommunityDatabase.communities.toList() }
            .onFailure { WorldGeoCommunityAddon.logger.error("Failed to inspect application refunds", it) }
            .getOrDefault(emptyList())
            .forEach { community ->
                val regionId = community.regionNumberId ?: return@forEach
                community.member.forEach { (uuid, account) ->
                    if (account.basicRoleType == MemberRoleType.REFUSED && account.joinFeePaid > 0L) {
                        submitApplicationRefund(
                            runtime,
                            ApplicationRefundPlan.create(
                                regionId,
                                GameProfile(uuid, runtime.identities.find(uuid)?.trustedName ?: uuid.toString()),
                                account.joinFeePaid,
                                account.joinedTime
                            )
                        )
                    }
                }
            }
    }
}

internal fun submitApplicationRefund(profile: GameProfile, regionId: Int, amount: Long, refusedAtMillis: Long) {
    val runtime = AccountSubsystem.runtimeOrNull() ?: return
    submitApplicationRefund(runtime, ApplicationRefundPlan.create(regionId, profile, amount, refusedAtMillis))
}

private fun submitApplicationRefund(runtime: AccountSubsystem.Runtime, plan: ApplicationRefundPlan) {
    val facts = listOf(
        applicationRefundStep(plan, CREDIT_STEP, CombinationStepStatus.DETERMINED),
        applicationRefundStep(plan, CLEAR_STEP, CombinationStepStatus.DETERMINED)
    )
    appendRefundFact(runtime, facts[0]) {
        appendRefundFact(runtime, facts[1]) {
            val transaction = applicationRefundTransaction(plan)
            runtime.service.submit(transaction) { state ->
                when (state.status) {
                    AccountTransactionStatus.SUCCEEDED -> appendRefundFact(
                        runtime,
                        applicationRefundStep(
                            plan, CREDIT_STEP, CombinationStepStatus.SUCCEEDED, transaction.shortId
                        )
                    ) { clearFrozenRefund(runtime, plan) }
                    AccountTransactionStatus.RESOLVED -> resolveRejectedRefund(runtime, plan, transaction.shortId)
                    else -> Unit
                }
            }.exceptionally { error ->
                WorldGeoCommunityAddon.logger.error("Failed to submit application refund ${plan.operationId}", error)
                null
            }
        }
    }
}

private fun clearFrozenRefund(runtime: AccountSubsystem.Runtime, plan: ApplicationRefundPlan) {
    val account = CommunityDatabase.getCommunityById(plan.regionId)?.member?.get(plan.playerUuid) ?: return
    if (account.basicRoleType != MemberRoleType.REFUSED || account.joinFeePaid != plan.amount) return
    account.joinFeePaid = 0L
    try {
        CommunityDatabase.save()
    } catch (error: Exception) {
        account.joinFeePaid = plan.amount
        WorldGeoCommunityAddon.logger.error("Failed to clear application refund ${plan.operationId}", error)
        return
    }
    appendRefundFact(
        runtime,
        applicationRefundStep(plan, CLEAR_STEP, CombinationStepStatus.SUCCEEDED)
    ) {
        runtime.server.playerList.getPlayer(plan.playerUuid)?.sendSystemMessage(
            Translator.tr("community.join.refund", plan.amount / 100.0)
        )
    }
}

private fun resolveRejectedRefund(runtime: AccountSubsystem.Runtime, plan: ApplicationRefundPlan, evidence: String) {
    appendRefundFact(
        runtime,
        applicationRefundStep(plan, CREDIT_STEP, CombinationStepStatus.COMPENSATED, evidence)
    ) {
        appendRefundFact(
            runtime,
            applicationRefundStep(plan, CLEAR_STEP, CombinationStepStatus.COMPENSATED)
        ) {
            WorldGeoCommunityAddon.logger.error(
                "Application refund ${plan.operationId} was rejected; frozen amount retained"
            )
        }
    }
}

private fun appendRefundFact(
    runtime: AccountSubsystem.Runtime,
    fact: CombinationStepFact,
    completed: () -> Unit
) {
    runtime.sharedStore.append(fact).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                WorldGeoCommunityAddon.logger.error("Failed to persist application refund ${fact.operationId}", error)
            } else {
                completed()
            }
        }
    }
}

internal fun applicationRefundTransaction(plan: ApplicationRefundPlan): AccountTransaction {
    val id = stableRefundId("account-credit:${plan.operationId}")
    return AccountTransaction(
        id,
        id.toString().replace("-", "").take(12),
        plan.refusedAtMillis,
        "application-refund:${plan.regionId}",
        plan.playerUuid,
        plan.playerName,
        plan.amount,
        AccountDirection.CREDIT,
        "application-refund",
        refundReference(plan)
    )
}

internal fun applicationRefundStep(
    plan: ApplicationRefundPlan,
    stepKey: String,
    status: CombinationStepStatus,
    evidence: String? = null
) = CombinationStepFact(
    stableRefundId("step:${plan.operationId}:$stepKey:${status.name}"),
    plan.regionId,
    plan.refusedAtMillis,
    plan.operationId,
    stepKey,
    if (stepKey == CREDIT_STEP) "money" else "community",
    if (stepKey == CREDIT_STEP) refundReference(plan) else clearReference(plan),
    status,
    evidence ?: if (status == CombinationStepStatus.DETERMINED) plan.encode() else null
)

private fun refundReference(plan: ApplicationRefundPlan) =
    "community:application-refund:credit:${plan.operationId}"
private fun clearReference(plan: ApplicationRefundPlan) =
    "community:application-refund:clear:${plan.operationId}"
private fun stableRefundId(value: String): UUID =
    UUID.nameUUIDFromBytes("application-refund:$value".toByteArray(StandardCharsets.UTF_8))

private const val REFUND_EVIDENCE_PREFIX = "application-refund:v1"
private const val CREDIT_STEP = "application-refund-account-credit"
private const val CLEAR_STEP = "application-refund-clear-frozen-amount"
