package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.checkAndPromoteRecruitingRealm
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.transaction.CombinationStepFact
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.infra.economy.EconomyWalletAdapter
import com.imyvm.community.util.Translator
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

internal data class ApplicationJoinPlan(
    val operationId: UUID,
    val regionId: Int,
    val playerUuid: UUID,
    val playerName: String,
    val amount: Long,
    val joinedAtMillis: Long
) {
    fun encode(): String {
        val name = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(playerName.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            APPLICATION_JOIN_EVIDENCE_PREFIX, operationId, regionId, playerUuid,
            amount, joinedAtMillis, name
        ).joinToString("|")
    }

    companion object {
        fun decode(value: String): ApplicationJoinPlan? {
            val fields = value.split('|')
            if (fields.size != 7 || fields[0] != APPLICATION_JOIN_EVIDENCE_PREFIX) return null
            return runCatching {
                ApplicationJoinPlan(
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

private val activeApplications = mutableSetOf<Pair<Int, UUID>>()

fun registerApplicationJoinAccountRecovery() {
    AccountSubsystem.onReady { runtime -> scanApplicationJoinRecovery(runtime, null) }
}

internal fun submitApplicationJoin(player: ServerPlayer, community: Community, amount: Long): Boolean {
    val regionId = community.regionNumberId ?: return false
    val runtime = AccountSubsystem.runtimeOrNull() ?: return false
    val key = regionId to player.uuid
    if (!activeApplications.add(key)) return false
    val plan = ApplicationJoinPlan(
        UUID.randomUUID(), regionId, player.uuid, player.gameProfile.name,
        amount, System.currentTimeMillis()
    )
    player.closeContainer()
    persistApplicationJoinPlan(runtime, plan) { driveApplicationJoin(runtime, plan) }
    return true
}

private fun scanApplicationJoinRecovery(runtime: AccountSubsystem.Runtime, token: String?) {
    runtime.sharedStore.scanUnresolvedOperations(token, APPLICATION_JOIN_RECOVERY_PAGE_SIZE)
        .whenComplete { page, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to scan unresolved application joins", error)
                    return@execute
                }
                page.operationIds.forEach { recoverApplicationJoin(runtime, it) }
                if (page.nextToken != null && page.operationIds.size == APPLICATION_JOIN_RECOVERY_PAGE_SIZE) {
                    scanApplicationJoinRecovery(runtime, page.nextToken)
                }
            }
        }
}

private fun recoverApplicationJoin(runtime: AccountSubsystem.Runtime, operationId: UUID) {
    runtime.sharedStore.scanOperation(operationId, null, APPLICATION_JOIN_MAX_FACTS)
        .whenComplete { page, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to read application join $operationId", error)
                    return@execute
                }
                val plan = page.items.asSequence()
                    .filterIsInstance<CombinationStepFact>()
                    .mapNotNull { it.evidence?.let(ApplicationJoinPlan::decode) }
                    .firstOrNull() ?: return@execute
                if (plan.operationId != operationId) return@execute
                activeApplications.add(plan.regionId to plan.playerUuid)
                persistApplicationJoinPlan(runtime, plan) { driveApplicationJoin(runtime, plan) }
            }
        }
}

private fun persistApplicationJoinPlan(
    runtime: AccountSubsystem.Runtime,
    plan: ApplicationJoinPlan,
    completed: () -> Unit
) {
    appendApplicationJoinFact(
        runtime,
        applicationJoinStep(plan, APPLICATION_JOIN_DEBIT_STEP, CombinationStepStatus.DETERMINED)
    ) {
        appendApplicationJoinFact(
            runtime,
            applicationJoinStep(plan, APPLICATION_JOIN_STATE_STEP, CombinationStepStatus.DETERMINED),
            completed
        )
    }
}

private fun driveApplicationJoin(runtime: AccountSubsystem.Runtime, plan: ApplicationJoinPlan) {
    runtime.sharedStore.scanOperation(plan.operationId, null, APPLICATION_JOIN_MAX_FACTS)
        .whenComplete { page, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to inspect application join ${plan.operationId}", error)
                    return@execute
                }
                val latest = LinkedHashMap<String, CombinationStepFact>()
                page.items.filterIsInstance<CombinationStepFact>().forEach { latest[it.stepKey] = it }
                val debit = latest[APPLICATION_JOIN_DEBIT_STEP] ?: return@execute
                val state = latest[APPLICATION_JOIN_STATE_STEP] ?: return@execute
                val refund = latest[APPLICATION_JOIN_REFUND_STEP]
                when {
                    refund?.status == CombinationStepStatus.COMPENSATED ->
                        activeApplications.remove(plan.regionId to plan.playerUuid)
                    refund?.status == CombinationStepStatus.NEEDS_OP -> Unit
                    refund != null && !refund.status.isTerminal() -> submitApplicationJoinRefund(runtime, plan)
                    debit.status == CombinationStepStatus.COMPENSATED -> compensateUnusedApplicationState(runtime, plan, state)
                    debit.status != CombinationStepStatus.SUCCEEDED -> submitApplicationJoinDebit(runtime, plan)
                    state.status == CombinationStepStatus.SUCCEEDED -> finishApplicationJoin(runtime, plan)
                    state.status == CombinationStepStatus.COMPENSATED -> beginApplicationJoinRefund(runtime, plan)
                    else -> beginApplicationJoinState(runtime, plan)
                }
            }
        }
}

private fun submitApplicationJoinDebit(runtime: AccountSubsystem.Runtime, plan: ApplicationJoinPlan) {
    val transaction = applicationJoinTransaction(plan, AccountDirection.DEBIT)
    runtime.service.submit(transaction) { accountState ->
        when (accountState.status) {
            AccountTransactionStatus.SUCCEEDED -> advanceApplicationJoin(
                runtime, plan,
                applicationJoinStep(
                    plan, APPLICATION_JOIN_DEBIT_STEP, CombinationStepStatus.SUCCEEDED,
                    transaction.shortId
                )
            )
            AccountTransactionStatus.RESOLVED -> advanceApplicationJoin(
                runtime, plan,
                applicationJoinStep(
                    plan, APPLICATION_JOIN_DEBIT_STEP, CombinationStepStatus.COMPENSATED,
                    transaction.shortId
                )
            )
            else -> Unit
        }
    }.exceptionally { error ->
        WorldGeoCommunityAddon.logger.error("Failed to submit application join debit ${plan.operationId}", error)
        null
    }
}

private fun compensateUnusedApplicationState(
    runtime: AccountSubsystem.Runtime,
    plan: ApplicationJoinPlan,
    state: CombinationStepFact
) {
    if (state.status.isTerminal()) {
        activeApplications.remove(plan.regionId to plan.playerUuid)
        runtime.server.playerList.getPlayer(plan.playerUuid)?.let { player ->
            player.sendSystemMessage(
                Translator.tr(
                    "community.join.error.insufficient_assets",
                    plan.amount / 100.0,
                    EconomyWalletAdapter.balance(player) / 100.0
                )
            )
        }
        return
    }
    advanceApplicationJoin(
        runtime, plan,
        applicationJoinStep(plan, APPLICATION_JOIN_STATE_STEP, CombinationStepStatus.COMPENSATED)
    )
}

private fun beginApplicationJoinState(runtime: AccountSubsystem.Runtime, plan: ApplicationJoinPlan) {
    appendApplicationJoinFact(
        runtime,
        applicationJoinStep(plan, APPLICATION_JOIN_STATE_STEP, CombinationStepStatus.CALL_STARTED)
    ) {
        val community = CommunityDatabase.getCommunityById(plan.regionId)
        val existing = community?.member?.get(plan.playerUuid)
        if (community == null || (existing != null && !matchesApplication(existing, plan))) {
            beginApplicationJoinRefund(runtime, plan)
            return@appendApplicationJoinFact
        }
        if (existing == null) {
            community.member[plan.playerUuid] = MemberAccount(
                joinedTime = plan.joinedAtMillis,
                basicRoleType = MemberRoleType.APPLICANT,
                joinFeePaid = plan.amount
            )
            try {
                CommunityDatabase.save()
            } catch (error: Exception) {
                community.member.remove(plan.playerUuid)
                WorldGeoCommunityAddon.logger.error("Failed to save application join ${plan.operationId}", error)
                beginApplicationJoinRefund(runtime, plan)
                return@appendApplicationJoinFact
            }
        }
        advanceApplicationJoin(
            runtime, plan,
            applicationJoinStep(plan, APPLICATION_JOIN_STATE_STEP, CombinationStepStatus.SUCCEEDED)
        )
    }
}

private fun matchesApplication(account: MemberAccount, plan: ApplicationJoinPlan): Boolean =
    account.basicRoleType == MemberRoleType.APPLICANT &&
        !account.isInvited &&
        account.joinedTime == plan.joinedAtMillis &&
        account.joinFeePaid == plan.amount

private fun beginApplicationJoinRefund(runtime: AccountSubsystem.Runtime, plan: ApplicationJoinPlan) {
    appendApplicationJoinFact(
        runtime,
        applicationJoinStep(plan, APPLICATION_JOIN_REFUND_STEP, CombinationStepStatus.DETERMINED)
    ) {
        appendApplicationJoinFact(
            runtime,
            applicationJoinStep(plan, APPLICATION_JOIN_STATE_STEP, CombinationStepStatus.COMPENSATED)
        ) { submitApplicationJoinRefund(runtime, plan) }
    }
}

private fun submitApplicationJoinRefund(runtime: AccountSubsystem.Runtime, plan: ApplicationJoinPlan) {
    val transaction = applicationJoinTransaction(plan, AccountDirection.CREDIT)
    runtime.service.submit(transaction) { state ->
        when (state.status) {
            AccountTransactionStatus.SUCCEEDED -> advanceApplicationJoin(
                runtime, plan,
                applicationJoinStep(
                    plan, APPLICATION_JOIN_REFUND_STEP, CombinationStepStatus.COMPENSATED,
                    transaction.shortId
                )
            )
            AccountTransactionStatus.RESOLVED -> advanceApplicationJoin(
                runtime, plan,
                applicationJoinStep(
                    plan, APPLICATION_JOIN_REFUND_STEP, CombinationStepStatus.NEEDS_OP,
                    transaction.shortId
                )
            )
            else -> Unit
        }
    }.exceptionally { error ->
        WorldGeoCommunityAddon.logger.error("Failed to submit application join refund ${plan.operationId}", error)
        null
    }
}

private fun finishApplicationJoin(runtime: AccountSubsystem.Runtime, plan: ApplicationJoinPlan) {
    if (!activeApplications.remove(plan.regionId to plan.playerUuid)) return
    val community = CommunityDatabase.getCommunityById(plan.regionId) ?: return
    val player = runtime.server.playerList.getPlayer(plan.playerUuid) ?: return
    player.sendSystemMessage(
        community.getRegion()?.let { Translator.tr("community.join.applied", it.name, plan.regionId) }
            ?: Component.empty()
    )
    player.sendSystemMessage(Translator.tr("community.join.payment.deducted", plan.amount / 100.0))
    val communityName = community.getRegion()?.name ?: "Community #${plan.regionId}"
    val notification = Translator.tr(
        "community.notification.application_received", player.name.string, communityName
    ) ?: Component.literal("${player.name.string} has applied to join $communityName")
    notifyOfficials(community, runtime.server, notification, player)
    checkAndPromoteRecruitingRealm(community)
}

private fun advanceApplicationJoin(
    runtime: AccountSubsystem.Runtime,
    plan: ApplicationJoinPlan,
    fact: CombinationStepFact
) {
    appendApplicationJoinFact(runtime, fact) { driveApplicationJoin(runtime, plan) }
}

private fun appendApplicationJoinFact(
    runtime: AccountSubsystem.Runtime,
    fact: CombinationStepFact,
    completed: () -> Unit
) {
    runtime.sharedStore.append(fact).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                WorldGeoCommunityAddon.logger.error("Failed to advance application join ${fact.operationId}", error)
            } else {
                completed()
            }
        }
    }
}

internal fun applicationJoinTransaction(
    plan: ApplicationJoinPlan,
    direction: AccountDirection
): AccountTransaction {
    val purpose = if (direction == AccountDirection.DEBIT) "debit" else "refund"
    val id = stableApplicationJoinId(plan, "account:$purpose")
    return AccountTransaction(
        id, id.toString().replace("-", "").take(12), plan.joinedAtMillis,
        "community-application:${plan.regionId}", plan.playerUuid, plan.playerName,
        plan.amount, direction, "community-application", applicationJoinReference(plan, purpose),
        if (direction == AccountDirection.CREDIT) stableApplicationJoinId(plan, "account:debit") else null
    )
}

internal fun applicationJoinStep(
    plan: ApplicationJoinPlan,
    stepKey: String,
    status: CombinationStepStatus,
    evidence: String? = null
) = CombinationStepFact(
    stableApplicationJoinId(plan, "step:$stepKey:${status.name}"),
    plan.regionId, plan.joinedAtMillis, plan.operationId, stepKey,
    if (stepKey == APPLICATION_JOIN_STATE_STEP) "community" else "money",
    when (stepKey) {
        APPLICATION_JOIN_DEBIT_STEP -> applicationJoinReference(plan, "debit")
        APPLICATION_JOIN_REFUND_STEP -> applicationJoinReference(plan, "refund")
        else -> "community:application:state:${plan.operationId}"
    },
    status,
    evidence ?: if (status == CombinationStepStatus.DETERMINED) plan.encode() else null
)

private fun stableApplicationJoinId(plan: ApplicationJoinPlan, purpose: String): UUID =
    UUID.nameUUIDFromBytes(
        "community-application:${plan.operationId}:$purpose".toByteArray(StandardCharsets.UTF_8)
    )
private fun applicationJoinReference(plan: ApplicationJoinPlan, purpose: String) =
    "community:application:$purpose:${plan.operationId}"

private const val APPLICATION_JOIN_EVIDENCE_PREFIX = "community-application:v1"
private const val APPLICATION_JOIN_DEBIT_STEP = "application-account-debit"
private const val APPLICATION_JOIN_STATE_STEP = "application-community-state"
private const val APPLICATION_JOIN_REFUND_STEP = "application-account-refund"
private const val APPLICATION_JOIN_RECOVERY_PAGE_SIZE = 128
private const val APPLICATION_JOIN_MAX_FACTS = 32
