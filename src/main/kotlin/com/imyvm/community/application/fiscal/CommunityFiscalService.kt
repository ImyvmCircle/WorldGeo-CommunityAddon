package com.imyvm.community.application.fiscal

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.account.mutateTreasury
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.fiscal.CommunityFiscalLineStatus
import com.imyvm.community.domain.model.fiscal.CommunityFiscalObservation
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicySwitch
import com.imyvm.community.domain.model.fiscal.CommunityFiscalSettlement
import com.imyvm.community.domain.model.fiscal.CommunityFiscalSettlementStatus
import com.imyvm.community.domain.model.fiscal.CommunityFiscalTaxSettlementLine
import com.imyvm.community.domain.model.fiscal.CommunityFiscalWelfareSettlementLine
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.infra.economy.EconomyWalletAdapter
import com.imyvm.iwg.domain.CompleteNaturalPeriodTransition
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTimelineType
import com.imyvm.iwg.inter.api.RegionDataApi
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import java.util.function.Consumer

object CommunityFiscalService {
    const val WELFARE_PROTECTED_TREASURY = 1_000_000L
    const val WELFARE_MAX_TAXABLE_INCREASE = 120_000L
    const val WELFARE_MIN_BUILDING_REWARD = 60_000L

    private val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")

    fun register() {
        RegionDataApi.registerCompleteNaturalPeriodTransitionCallback(Consumer { transition ->
            val server = WorldGeoCommunityAddon.server ?: return@Consumer
            server.execute { settleTransition(transition) }
        })
        AccountSubsystem.onReady { runtime ->
            runtime.server.execute {
                recoverOpenSettlements(runtime)
                settleLatestClosedProductionWeek()
            }
        }
    }

    fun recordLoginObservation(playerUuid: UUID, playerName: String) {
        val server = WorldGeoCommunityAddon.server ?: return
        val weekKey = RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK]?.let(::periodLedgerKey) ?: return
        val balance = runCatching { EconomyWalletAdapter().withWallet(server, playerUuid, playerName) { balance() } }.getOrNull() ?: return
        var changed = false
        for (community in CommunityDatabase.communities) {
            if (!formalMembers(community).contains(playerUuid)) continue
            recordObservation(community, playerUuid, weekKey, balance, System.currentTimeMillis())
            changed = true
        }
        if (changed) CommunityDatabase.save()
    }

    fun settleLatestClosedProductionWeek(): Result<CommunityFiscalSettlementSummary> {
        val weekKey = latestClosedProductionPeriod(NaturalPeriodKind.WEEK) ?: return Result.failure(IllegalStateException("closed production week unavailable"))
        return settleWeek(periodLedgerKey(weekKey))
    }

    fun settleWeek(weekKey: String): Result<CommunityFiscalSettlementSummary> {
        val runtime = AccountSubsystem.runtimeOrNull() ?: return Result.failure(IllegalStateException("account subsystem unavailable"))
        return runCatching {
            var frozen = 0
            var submittedTax = 0
            var taxTotal = 0L
            var welfareTotal = 0L
            for (community in CommunityDatabase.communities) {
                if (community.regionNumberId == null) continue
                activatePolicyForWeek(community, weekKey)
                val settlement = community.fiscalState.settlements.firstOrNull { it.weekKey == weekKey }
                    ?: freezeSettlement(community, weekKey).also {
                        community.fiscalState.settlements.add(it)
                        frozen++
                    }
                driveSettlement(runtime, community, settlement)
                submittedTax += settlement.taxLines.count { it.status == CommunityFiscalLineStatus.SUBMITTED || it.status == CommunityFiscalLineStatus.SUCCEEDED }
                taxTotal = Math.addExact(taxTotal, settlement.taxLines.filter { it.status == CommunityFiscalLineStatus.SUCCEEDED }.fold(0L) { acc, line -> Math.addExact(acc, line.taxAmount) })
                welfareTotal = Math.addExact(welfareTotal, settlement.welfareLines.filter { it.status != CommunityFiscalLineStatus.UNPAID_INSUFFICIENT_BALANCE }.fold(0L) { acc, line -> Math.addExact(acc, line.actualAmount) })
                if (settlement.status == CommunityFiscalSettlementStatus.COMPLETED) community.fiscalState.settledWeekKeys.add(weekKey)
                trimSettlements(community)
            }
            CommunityDatabase.save()
            CommunityFiscalSettlementSummary(frozen, submittedTax, taxTotal, welfareTotal)
        }
    }

    fun settlementHistory(community: Community): List<CommunityFiscalSettlement> =
        community.fiscalState.settlements.sortedByDescending { it.weekKey }

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

    fun schedulePolicyForCurrentWeek(community: Community, policy: CommunityFiscalPolicy): Result<Pair<Long, String>> {
        val current = RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK]
            ?: return Result.failure(IllegalStateException("current week unavailable"))
        val next = nextPeriodKey(current.timelineId, NaturalPeriodKind.WEEK, current.periodId)
            ?: return Result.failure(IllegalStateException("next week unavailable"))
        val cooldown = nextPeriodKey(next.timelineId, NaturalPeriodKind.WEEK, next.periodId)
            ?: return Result.failure(IllegalStateException("cooldown week unavailable"))
        return schedulePolicy(community, policy, periodLedgerKey(current), periodLedgerKey(next), periodLedgerKey(cooldown))
            .map { it to periodLedgerKey(next) }
    }

    fun schedulePolicy(community: Community, policy: CommunityFiscalPolicy, currentWeekKey: String, nextWeekKey: String, cooldownUntilWeekKey: String): Result<Long> {
        val cost = PricingConfig.FISCAL_POLICY_SWITCH_COST.value
        if (community.fiscalState.pendingPolicy != null) return Result.failure(IllegalStateException("policy switch already pending"))
        if (community.fiscalState.policyCooldownUntilWeekKey.isNotBlank() && compareWeekIds(currentWeekKey, community.fiscalState.policyCooldownUntilWeekKey) < 0) {
            return Result.failure(IllegalStateException("policy switch is cooling down until ${community.fiscalState.policyCooldownUntilWeekKey}"))
        }
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
            community.fiscalState.pendingPolicy = CommunityFiscalPolicySwitch(policy, nextWeekKey, cooldownUntilWeekKey, System.currentTimeMillis())
            CommunityDatabase.save()
            cost
        }
    }

    fun activatePolicyForWeek(community: Community, weekKey: String) {
        val pending = community.fiscalState.pendingPolicy ?: return
        if (pending.effectiveWeekKey == weekKey) {
            community.fiscalState.activePolicy = pending.policy
            community.fiscalState.policyCooldownUntilWeekKey = pending.cooldownUntilWeekKey
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
        return formalMembers(community).map { playerUuid ->
            val observation = community.fiscalState.memberObservations[playerUuid]?.takeIf { it.weekKey == weekKey }
            if (observation == null) return@map CommunityTaxLine(playerUuid, weekKey, 0L, 0L, 0L, 0L, false)
            if (observation.firstObservedAtMillis == observation.lastObservedAtMillis) return@map CommunityTaxLine(playerUuid, weekKey, 0L, 0L, observation.firstBalance, observation.lastBalance, false)
            val taxable = (observation.lastBalance - observation.firstBalance).coerceAtLeast(0L)
            CommunityTaxLine(playerUuid, weekKey, taxable, taxAmount(taxable), observation.firstBalance, observation.lastBalance, true)
        }
    }

    fun planWelfare(community: Community, weekKey: String, buildingRewards: Map<UUID, Long>): CommunityWelfarePlan {
        if (!community.fiscalState.activePolicy.welfare) return CommunityWelfarePlan(emptyList(), 0L, 0L)
        val theoretical = formalMembers(community).mapNotNull { playerUuid ->
            val observation = community.fiscalState.memberObservations[playerUuid]?.takeIf { it.weekKey == weekKey } ?: return@mapNotNull null
            if (observation.firstObservedAtMillis == observation.lastObservedAtMillis) return@mapNotNull null
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

    private fun settleTransition(transition: CompleteNaturalPeriodTransition) {
        if (transition.previous.kind == NaturalPeriodKind.WEEK) {
            settleWeek(periodLedgerKey(transition.previous))
                .onFailure { WorldGeoCommunityAddon.logger.error("Failed to settle fiscal week ${transition.previous.periodId}", it) }
        }
    }

    private fun recoverOpenSettlements(runtime: AccountSubsystem.Runtime) {
        CommunityDatabase.communities.forEach { community ->
            community.fiscalState.settlements
                .filter { it.status != CommunityFiscalSettlementStatus.COMPLETED }
                .forEach { driveSettlement(runtime, community, it) }
        }
    }

    private fun freezeSettlement(community: Community, weekKey: String): CommunityFiscalSettlement {
        val taxLines = planCommunityTax(community, weekKey).map {
            CommunityFiscalTaxSettlementLine(it.playerUuid, it.taxableIncrease, it.taxAmount, it.firstBalance, it.lastBalance, it.completeObservation)
        }.toMutableList()
        val rewards = community.buildingState.playerWeekLedgers
            .filterValues { it.weekPeriodId == weekKey }
            .mapValues { it.value.settledAmount }
        val welfarePlan = planWelfare(community, weekKey, rewards)
        val welfareLines = welfarePlan.lines.map {
            CommunityFiscalWelfareSettlementLine(it.playerUuid, it.theoreticalAmount, it.actualAmount)
        }.toMutableList()
        return CommunityFiscalSettlement(
            weekKey,
            System.currentTimeMillis(),
            community.fiscalState.activePolicy,
            taxLines,
            welfareLines,
            welfarePlan.theoreticalTotal,
            welfarePlan.spendableTreasury
        )
    }

    private fun driveSettlement(runtime: AccountSubsystem.Runtime, community: Community, settlement: CommunityFiscalSettlement) {
        settlement.taxLines.filter { it.taxAmount > 0L && it.status == CommunityFiscalLineStatus.FROZEN }.forEach { line ->
            submitTax(runtime, community, settlement, line)
        }
        reconcileKnownTransactions(runtime, settlement)
        tryApplyTaxTreasury(community, settlement)
        tryApplyWelfare(runtime, community, settlement)
    }

    private fun reconcileKnownTransactions(runtime: AccountSubsystem.Runtime, settlement: CommunityFiscalSettlement) {
        for (line in settlement.taxLines.filter { it.status == CommunityFiscalLineStatus.SUBMITTED && it.transactionId != null }) {
            val state = runtime.store.find(line.transactionId!!).join() ?: continue
            when (state.status) {
                AccountTransactionStatus.SUCCEEDED -> line.status = CommunityFiscalLineStatus.SUCCEEDED
                AccountTransactionStatus.RESOLVED -> line.status = CommunityFiscalLineStatus.UNPAID_INSUFFICIENT_BALANCE
                else -> Unit
            }
        }
        for (line in settlement.welfareLines.filter { it.status == CommunityFiscalLineStatus.SUBMITTED && it.transactionId != null }) {
            val state = runtime.store.find(line.transactionId!!).join() ?: continue
            when (state.status) {
                AccountTransactionStatus.SUCCEEDED -> line.status = CommunityFiscalLineStatus.SUCCEEDED
                AccountTransactionStatus.RESOLVED -> line.status = CommunityFiscalLineStatus.UNPAID_INSUFFICIENT_BALANCE
                else -> Unit
            }
        }
    }

    private fun submitTax(runtime: AccountSubsystem.Runtime, community: Community, settlement: CommunityFiscalSettlement, line: CommunityFiscalTaxSettlementLine) {
        val transaction = fiscalTransaction(community, settlement.weekKey, line.playerUuid, line.taxAmount, AccountDirection.DEBIT, "tax")
        line.transactionId = transaction.transactionId
        line.shortId = transaction.shortId
        line.status = CommunityFiscalLineStatus.SUBMITTED
        settlement.status = CommunityFiscalSettlementStatus.TAX_SUBMITTED
        CommunityDatabase.save()
        runtime.service.submit(transaction) { state ->
            when (state.status) {
                AccountTransactionStatus.SUCCEEDED -> line.status = CommunityFiscalLineStatus.SUCCEEDED
                AccountTransactionStatus.RESOLVED -> line.status = CommunityFiscalLineStatus.UNPAID_INSUFFICIENT_BALANCE
                else -> return@submit
            }
            driveSettlement(runtime, community, settlement)
            CommunityDatabase.save()
        }
    }

    private fun tryApplyTaxTreasury(community: Community, settlement: CommunityFiscalSettlement) {
        if (settlement.status.ordinal >= CommunityFiscalSettlementStatus.TAX_TREASURY_APPLIED.ordinal) return
        if (settlement.taxLines.any { it.taxAmount > 0L && it.status == CommunityFiscalLineStatus.SUBMITTED }) return
        val total = settlement.taxLines.filter { it.status == CommunityFiscalLineStatus.SUCCEEDED }.fold(0L) { acc, line -> Math.addExact(acc, line.taxAmount) }
        if (total > 0L) {
            mutateTreasury(community, total, ResourceDirection.CREDIT, "fiscal", "community:fiscal-tax:${community.regionNumberId}:${settlement.weekKey}", "fiscal-tax", settlement.weekKey, "community.treasury.desc.fiscal_tax", listOf(settlement.weekKey)).getOrThrow()
        }
        settlement.status = CommunityFiscalSettlementStatus.TAX_TREASURY_APPLIED
        CommunityDatabase.save()
    }

    private fun tryApplyWelfare(runtime: AccountSubsystem.Runtime, community: Community, settlement: CommunityFiscalSettlement) {
        if (settlement.status.ordinal < CommunityFiscalSettlementStatus.TAX_TREASURY_APPLIED.ordinal) return
        if (settlement.status.ordinal < CommunityFiscalSettlementStatus.WELFARE_TREASURY_APPLIED.ordinal) {
            val total = settlement.welfareLines.fold(0L) { acc, line -> Math.addExact(acc, line.actualAmount) }
            if (total > 0L) {
                mutateTreasury(community, total, ResourceDirection.DEBIT, "fiscal", "community:fiscal-welfare:${community.regionNumberId}:${settlement.weekKey}", "fiscal-welfare", settlement.weekKey, "community.treasury.desc.fiscal_welfare", listOf(settlement.weekKey)).getOrThrow()
            }
            settlement.status = CommunityFiscalSettlementStatus.WELFARE_TREASURY_APPLIED
            CommunityDatabase.save()
        }
        settlement.welfareLines.filter { it.actualAmount > 0L && it.status == CommunityFiscalLineStatus.FROZEN }.forEach { line ->
            submitWelfare(runtime, community, settlement, line)
        }
        if (settlement.welfareLines.none { it.actualAmount > 0L && it.status == CommunityFiscalLineStatus.SUBMITTED }) {
            settlement.status = CommunityFiscalSettlementStatus.COMPLETED
            community.fiscalState.settledWeekKeys.add(settlement.weekKey)
            CommunityDatabase.save()
        }
    }

    private fun submitWelfare(runtime: AccountSubsystem.Runtime, community: Community, settlement: CommunityFiscalSettlement, line: CommunityFiscalWelfareSettlementLine) {
        val transaction = fiscalTransaction(community, settlement.weekKey, line.playerUuid, line.actualAmount, AccountDirection.CREDIT, "welfare")
        line.transactionId = transaction.transactionId
        line.shortId = transaction.shortId
        line.status = CommunityFiscalLineStatus.SUBMITTED
        CommunityDatabase.save()
        runtime.service.submit(transaction) { state ->
            when (state.status) {
                AccountTransactionStatus.SUCCEEDED -> line.status = CommunityFiscalLineStatus.SUCCEEDED
                AccountTransactionStatus.RESOLVED -> line.status = CommunityFiscalLineStatus.UNPAID_INSUFFICIENT_BALANCE
                else -> return@submit
            }
            tryApplyWelfare(runtime, community, settlement)
            CommunityDatabase.save()
        }
    }

    private fun fiscalTransaction(community: Community, weekKey: String, playerUuid: UUID, amount: Long, direction: AccountDirection, kind: String): AccountTransaction {
        val external = "fiscal:$kind:${community.regionNumberId}:$weekKey:$playerUuid"
        val id = UUID.nameUUIDFromBytes(external.toByteArray(StandardCharsets.UTF_8))
        return AccountTransaction(
            id,
            id.toString().replace("-", "").take(12).uppercase(Locale.ROOT),
            System.currentTimeMillis(),
            weekKey,
            playerUuid,
            null,
            amount,
            direction,
            "FISCAL_${kind.uppercase(Locale.ROOT)}",
            external
        )
    }

    private fun trimSettlements(community: Community) {
        while (community.fiscalState.settlements.size > MAX_SETTLEMENTS) {
            community.fiscalState.settlements.removeAt(0)
        }
    }

    private fun latestClosedProductionPeriod(kind: NaturalPeriodKind): NaturalPeriodKey? {
        val timeline = RegionDataApi.getAvailableNaturalPeriodTimelines()
            .filter { it.type == NaturalPeriodTimelineType.PRODUCTION }
            .maxByOrNull { it.sequence } ?: return null
        val range = RegionDataApi.getAvailableNaturalPeriodRange(timeline.timelineId, kind) ?: return null
        return if (timeline.closed) range.latest else previousPeriodKey(range.latest)
    }

    private fun nextPeriodKey(timelineId: String, kind: NaturalPeriodKind, periodId: String): NaturalPeriodKey? = runCatching {
        NaturalPeriodKey(timelineId, kind, when {
            periodId.startsWith("test:${kind.name.lowercase(Locale.ROOT)}:") -> {
                val prefix = "test:${kind.name.lowercase(Locale.ROOT)}:"
                prefix + (periodId.removePrefix(prefix).toLong() + 1L)
            }
            kind == NaturalPeriodKind.HOUR -> LocalDateTime.parse(periodId, HOUR_FORMATTER).plusHours(1).format(HOUR_FORMATTER)
            kind == NaturalPeriodKind.WEEK -> formatWeek(parseWeekStart(periodId).plusWeeks(1))
            else -> return null
        })
    }.getOrNull()

    private fun previousPeriodKey(key: NaturalPeriodKey): NaturalPeriodKey? = runCatching {
        NaturalPeriodKey(key.timelineId, key.kind, when {
            key.periodId.startsWith("test:${key.kind.name.lowercase(Locale.ROOT)}:") -> {
                val prefix = "test:${key.kind.name.lowercase(Locale.ROOT)}:"
                val previous = key.periodId.removePrefix(prefix).toLong() - 1L
                if (previous < 0L) return null
                prefix + previous
            }
            key.kind == NaturalPeriodKind.HOUR -> LocalDateTime.parse(key.periodId, HOUR_FORMATTER).minusHours(1).format(HOUR_FORMATTER)
            key.kind == NaturalPeriodKind.WEEK -> formatWeek(parseWeekStart(key.periodId).minusWeeks(1))
            else -> return null
        })
    }.getOrNull()

    private fun compareWeekIds(left: String, right: String): Int = when {
        left.startsWith("test:") && right.startsWith("test:") -> left.substringAfterLast(':').toLong().compareTo(right.substringAfterLast(':').toLong())
        left.contains(':') || right.contains(':') -> left.compareTo(right)
        else -> parseWeekStart(left).compareTo(parseWeekStart(right))
    }

    private fun parseWeekStart(periodId: String): LocalDate = LocalDate.parse("$periodId-1", DateTimeFormatter.ISO_WEEK_DATE)

    private fun formatWeek(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        return String.format(Locale.ROOT, "%04d-W%02d", date.get(weekFields.weekBasedYear()), date.get(weekFields.weekOfWeekBasedYear()))
    }

    private fun periodLedgerKey(key: NaturalPeriodKey): String = "${key.timelineId}:${key.periodId}"

    private fun formalMembers(community: Community): List<UUID> = community.member.entries
        .filter { (_, account) -> account.basicRoleType == MemberRoleType.OWNER || account.basicRoleType == MemberRoleType.ADMIN || account.basicRoleType == MemberRoleType.MEMBER }
        .map { it.key }

    private const val MAX_SETTLEMENTS = 64
}

data class CommunityFiscalSettlementSummary(
    val frozenCommunities: Int,
    val submittedTaxTransactions: Int,
    val appliedTaxTotal: Long,
    val welfareTotal: Long
)

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
