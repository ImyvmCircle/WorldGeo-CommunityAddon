package com.imyvm.community.domain.model.fiscal

import java.util.UUID

enum class CommunityFiscalPolicy(val incomeTax: Boolean, val welfare: Boolean) {
    NEOLIBERALISM(true, false),
    VISIBLE_HAND(true, true),
    HEAVEN_ON_EARTH(false, true),
    ANARCHISM(false, false)
}

data class CommunityFiscalPolicySwitch(
    val policy: CommunityFiscalPolicy,
    val effectiveWeekKey: String,
    val cooldownUntilWeekKey: String,
    val switchedAtMillis: Long
)

data class CommunityFiscalObservation(
    val weekKey: String,
    var firstBalance: Long,
    var firstObservedAtMillis: Long,
    var lastBalance: Long,
    var lastObservedAtMillis: Long
)

enum class CommunityFiscalLineStatus {
    FROZEN,
    SUBMITTED,
    SUCCEEDED,
    UNPAID_INSUFFICIENT_BALANCE
}

enum class CommunityFiscalSettlementStatus {
    FROZEN,
    TAX_SUBMITTED,
    TAX_TREASURY_APPLIED,
    WELFARE_TREASURY_APPLIED,
    COMPLETED
}

data class CommunityFiscalTaxSettlementLine(
    val playerUuid: UUID,
    val taxableIncrease: Long,
    val taxAmount: Long,
    val firstBalance: Long,
    val lastBalance: Long,
    val completeObservation: Boolean,
    var transactionId: UUID? = null,
    var shortId: String? = null,
    var status: CommunityFiscalLineStatus = CommunityFiscalLineStatus.FROZEN
)

data class CommunityFiscalWelfareSettlementLine(
    val playerUuid: UUID,
    val theoreticalAmount: Long,
    val actualAmount: Long,
    var transactionId: UUID? = null,
    var shortId: String? = null,
    var status: CommunityFiscalLineStatus = CommunityFiscalLineStatus.FROZEN
)

data class CommunityFiscalSettlement(
    val weekKey: String,
    val frozenAtMillis: Long,
    val policy: CommunityFiscalPolicy,
    val taxLines: MutableList<CommunityFiscalTaxSettlementLine> = mutableListOf(),
    val welfareLines: MutableList<CommunityFiscalWelfareSettlementLine> = mutableListOf(),
    val theoreticalWelfareTotal: Long = 0L,
    val spendableTreasury: Long = 0L,
    var status: CommunityFiscalSettlementStatus = CommunityFiscalSettlementStatus.FROZEN
)

data class CommunityFiscalState(
    var activePolicy: CommunityFiscalPolicy = CommunityFiscalPolicy.NEOLIBERALISM,
    var pendingPolicy: CommunityFiscalPolicySwitch? = null,
    var policyCooldownUntilWeekKey: String = "",
    var memberObservations: HashMap<UUID, CommunityFiscalObservation> = hashMapOf(),
    var settledWeekKeys: MutableSet<String> = mutableSetOf(),
    var settlements: MutableList<CommunityFiscalSettlement> = mutableListOf()
)
