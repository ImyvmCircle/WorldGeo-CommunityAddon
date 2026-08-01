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

data class CommunityFiscalState(
    var activePolicy: CommunityFiscalPolicy = CommunityFiscalPolicy.NEOLIBERALISM,
    var pendingPolicy: CommunityFiscalPolicySwitch? = null,
    var memberObservations: HashMap<UUID, CommunityFiscalObservation> = hashMapOf(),
    var settledWeekKeys: MutableSet<String> = mutableSetOf()
)
