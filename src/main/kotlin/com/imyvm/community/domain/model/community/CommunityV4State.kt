package com.imyvm.community.domain.model.community

import java.util.UUID

data class BuildingRewardLedger(
    var claimedBlockPlaceCount: Long = 0L,
    var claimedAmount: Long = 0L,
    var lastClaimedPeriodId: String? = null
)

data class CommunityPlot(
    val subSpaceId: Long,
    var name: String,
    var ownerUUID: UUID? = null,
    var salePrice: Long? = null,
    var saleDisabled: Boolean = false,
    var arena: Boolean = false,
    var advertising: Boolean = false,
    var lastPriceRefreshMillis: Long = 0L,
    var cachedPrice: Long = 0L
)

data class CommunityTitle(
    val slot: Int,
    var titleKey: String,
    var ownerUUID: UUID,
    var purchasedAt: Long,
    var active: Boolean = true,
    var effectKey: String? = null,
    var effectAmplifier: Int = 0
)

data class CommunityPolicyState(
    var activePolicyKey: String = "default",
    var pendingPolicyKey: String? = null,
    var pendingEffectivePeriodId: String? = null,
    var lastChangedPeriodId: String? = null
)

data class TaxWelfareSettlement(
    val settlementId: String,
    val periodId: String,
    val createdAt: Long,
    val totalAssetsAtFreeze: Long,
    val taxAmount: Long,
    val welfareAmount: Long,
    var status: TaxWelfareSettlementStatus = TaxWelfareSettlementStatus.PENDING,
    var failureReason: String? = null,
    var retryCount: Int = 0,
    var nextRetryAt: Long = 0L
)

enum class TaxWelfareSettlementStatus(val value: Int) {
    PENDING(0),
    APPLIED(1),
    FAILED(2);

    companion object {
        fun fromValue(value: Int): TaxWelfareSettlementStatus =
            entries.firstOrNull { it.value == value } ?: FAILED
    }
}
