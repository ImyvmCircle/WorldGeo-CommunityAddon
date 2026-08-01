package com.imyvm.community.application.fiscal

import com.imyvm.community.application.account.mutateTreasury
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.fiscal.CommunityFiscalObservation
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import java.util.UUID

object CommunityFiscalService {
    const val WELFARE_PROTECTED_TREASURY = 1_000_000L
    const val WELFARE_MAX_TAXABLE_INCREASE = 120_000L
    const val WELFARE_MIN_BUILDING_REWARD = 60_000L

    fun recordObservation(community: Community, playerUuid: UUID, weekKey: String, balance: Long, observedAtMillis: Long) {
        require(balance >= 0L) { "balance must not be negative" }
        val existing = community.fiscalState.memberObservations[playerUuid]
        if (existing == null || existing.weekKey != weekKey) {
            community.fiscalState.memberObservations[playerUuid] = CommunityFiscalObservation(weekKey, balance, observedAtMillis, balance, observedAtMillis)
        } else if (observedAtMillis < existing.firstObservedAtMillis) {
            existing.firstBalance = balance
            existing.firstObservedAtMillis = observedAtMillis
        } else if (observedAtMillis >= existing.lastObservedAtMillis) {
            existing.lastBalance = balance
            existing.lastObservedAtMillis = observedAtMillis
        }
    }

    fun schedulePolicy(community: Community, policy: CommunityFiscalPolicy, currentWeekKey: String, nextWeekKey: String, cooldownUntilWeekKey: String): Result<Long> {
        val cost = PricingConfig.FISCAL_POLICY_SWITCH_COST.value
        if (community.fiscalState.pendingPolicy != null) return Result.failure(IllegalStateException("policy switch already pending"))
        if (community.getTotalAssets() < cost) return Result.failure(IllegalStateException("insufficient treasury"))
        return mutateTreasury(
            community,
            cost,
            ResourceDirection.DEBIT,
            "fiscal",
            "community:fiscal-policy:${community.regionNumberId}:$currentWeekKey:$policy",
            "fiscal-policy-switch",
            policy.name,
            "community.treasury.desc.fiscal_policy_switch",
            listOf(policy.name)
        ).map {
            community.fiscalState.pendingPolicy = com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicySwitch(policy, nextWeekKey, cooldownUntilWeekKey, System.currentTimeMillis())
            CommunityDatabase.save()
            cost
        }
    }

    fun activatePolicyForWeek(community: Community, weekKey: String) {
        val pending = community.fiscalState.pendingPolicy ?: return
        if (pending.effectiveWeekKey == weekKey) {
            community.fiscalState.activePolicy = pending.policy
            community.fiscalState.pendingPolicy = null
        }
    }

    fun taxAmount(taxableIncrease: Long): Long {
        require(taxableIncrease >= 0L) { "taxable increase must not be negative" }
        val first = minOf(taxableIncrease, 50_000L)
        val second = minOf((taxableIncrease - 50_000L).coerceAtLeast(0L), 150_000L)
        val third = (taxableIncrease - 200_000L).coerceAtLeast(0L)
        return (first + second * 5L + third * 10L) / 100L
    }

    fun planCommunityTax(community: Community, weekKey: String): List<CommunityTaxLine> {
        if (!community.fiscalState.activePolicy.incomeTax) return emptyList()
        return formalMembers(community).mapNotNull { playerUuid ->
            val observation = community.fiscalState.memberObservations[playerUuid]?.takeIf { it.weekKey == weekKey } ?: return@mapNotNull null
            if (observation.firstObservedAtMillis == observation.lastObservedAtMillis) return@mapNotNull CommunityTaxLine(playerUuid, weekKey, 0L, 0L, observation.firstBalance, observation.lastBalance, false)
            val taxable = (observation.lastBalance - observation.firstBalance).coerceAtLeast(0L)
            CommunityTaxLine(playerUuid, weekKey, taxable, taxAmount(taxable), observation.firstBalance, observation.lastBalance, true)
        }
    }

    fun planWelfare(community: Community, weekKey: String, buildingRewards: Map<UUID, Long>): CommunityWelfarePlan {
        if (!community.fiscalState.activePolicy.welfare) return CommunityWelfarePlan(emptyList(), 0L, 0L)
        val theoretical = formalMembers(community).mapNotNull { playerUuid ->
            val observation = community.fiscalState.memberObservations[playerUuid]?.takeIf { it.weekKey == weekKey } ?: return@mapNotNull null
            val taxable = (observation.lastBalance - observation.firstBalance).coerceAtLeast(0L)
            val building = buildingRewards[playerUuid] ?: 0L
            if (taxable < WELFARE_MAX_TAXABLE_INCREASE && building >= WELFARE_MIN_BUILDING_REWARD) playerUuid to (building / 10L) else null
        }.toMap()
        val total = theoretical.values.fold(0L) { acc, value -> Math.addExact(acc, value) }
        val spendable = (community.getTotalAssets() - WELFARE_PROTECTED_TREASURY).coerceAtLeast(0L)
        if (total == 0L || spendable <= 0L) return CommunityWelfarePlan(theoretical.map { CommunityWelfareLine(it.key, it.value, 0L) }, total, spendable)
        val actual = theoretical.map { (uuid, amount) ->
            CommunityWelfareLine(uuid, amount, if (spendable >= total) amount else amount * spendable / total)
        }
        return CommunityWelfarePlan(actual, total, spendable)
    }

    private fun formalMembers(community: Community): List<UUID> = community.member.entries
        .filter { (_, account) -> account.basicRoleType == MemberRoleType.OWNER || account.basicRoleType == MemberRoleType.ADMIN || account.basicRoleType == MemberRoleType.MEMBER }
        .map { it.key }
}

data class CommunityTaxLine(
    val playerUuid: UUID,
    val weekKey: String,
    val taxableIncrease: Long,
    val taxAmount: Long,
    val firstBalance: Long,
    val lastBalance: Long,
    val completeObservation: Boolean
)

data class CommunityWelfareLine(
    val playerUuid: UUID,
    val theoreticalAmount: Long,
    val actualAmount: Long
)

data class CommunityWelfarePlan(
    val lines: List<CommunityWelfareLine>,
    val theoreticalTotal: Long,
    val spendableTreasury: Long
)
