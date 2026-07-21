package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.CommunityPlot
import com.imyvm.community.domain.model.community.CommunityTitle
import com.imyvm.community.domain.model.community.TaxWelfareSettlement
import com.imyvm.community.domain.model.community.TaxWelfareSettlementStatus
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.PricingConfig
import com.imyvm.iwg.inter.api.RegionDataApi
import java.util.UUID
import kotlin.math.max

object CommunityV4Service {
    fun refreshDevelopmentFromWorldGeo(community: Community): Long {
        val regionId = community.regionNumberId ?: return community.developmentBlockPlaceTotal
        val region = RegionDataApi.getRegion(regionId) ?: return community.developmentBlockPlaceTotal
        val current = RegionDataApi.getRegionPlayerStats(region).blockPlaceCount
        if (current > community.developmentBlockPlaceTotal) community.developmentBlockPlaceTotal = current
        return community.developmentBlockPlaceTotal
    }

    fun claimBuildingReward(community: Community, playerUUID: UUID, periodId: String, blockValue: Long): Long {
        val ledger = community.buildingRewardLedgers.getOrPut(playerUUID) { com.imyvm.community.domain.model.community.BuildingRewardLedger() }
        val total = refreshDevelopmentFromWorldGeo(community)
        val unclaimed = (total - ledger.claimedBlockPlaceCount).coerceAtLeast(0L)
        val limit = CommunityConfig.BUILDING_REWARD_DEFAULT_BLOCK_LIMIT.value.toLong()
        val payableBlocks = unclaimed.coerceAtMost(limit)
        val amount = payableBlocks * blockValue
        if (amount <= 0L) return 0L
        ledger.claimedBlockPlaceCount += payableBlocks
        ledger.claimedAmount += amount
        ledger.lastClaimedPeriodId = periodId
        community.communityIncome.add(
            Turnover(amount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.building_reward", listOf(playerUUID.toString()))
        )
        return amount
    }

    fun upsertPlot(community: Community, subSpaceId: Long, name: String): CommunityPlot {
        val existing = community.plots.firstOrNull { it.subSpaceId == subSpaceId }
        if (existing != null) {
            existing.name = name
            return existing
        }
        val plot = CommunityPlot(subSpaceId = subSpaceId, name = name)
        community.plots.add(plot)
        return plot
    }

    fun calculatePlotPrice(community: Community, subSpaceId: Long, area: Double?, nowMillis: Long = System.currentTimeMillis()): Long {
        val areaValue = area ?: RegionDataApi.getSubSpaceById(subSpaceId)?.third?.let { subSpace ->
            RegionDataApi.getSubSpaceSnapshot(
                RegionDataApi.getSubSpaceById(subSpaceId)!!.first,
                RegionDataApi.getSubSpaceById(subSpaceId)!!.second,
                subSpace
            ).area
        } ?: 0.0
        val regionWeight = community.getTotalAssets() / 1000L
        val price = max(0L, (areaValue * PricingConfig.PLOT_AREA_PRICE_PER_BLOCK.value).toLong() + regionWeight)
        val plot = community.plots.firstOrNull { it.subSpaceId == subSpaceId }
        if (plot != null) {
            plot.cachedPrice = price
            plot.lastPriceRefreshMillis = nowMillis
        }
        return price
    }

    fun buyTitle(community: Community, playerUUID: UUID, slot: Int, titleKey: String, effectKey: String? = null): CommunityTitle {
        community.titles.removeAll { it.ownerUUID == playerUUID && it.slot == slot }
        val title = CommunityTitle(slot, titleKey, playerUUID, System.currentTimeMillis(), effectKey = effectKey)
        community.titles.add(title)
        return title
    }

    fun schedulePolicy(community: Community, newPolicyKey: String, currentPeriodId: String, effectivePeriodId: String): Boolean {
        if (community.policy.lastChangedPeriodId == currentPeriodId) return false
        community.policy.pendingPolicyKey = newPolicyKey
        community.policy.pendingEffectivePeriodId = effectivePeriodId
        community.policy.lastChangedPeriodId = currentPeriodId
        community.expenditures.add(
            Turnover(PricingConfig.POLICY_SWITCH_COST.value, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.policy_switch", listOf(newPolicyKey))
        )
        return true
    }

    fun applyDuePolicy(community: Community, periodId: String): Boolean {
        val pending = community.policy.pendingPolicyKey ?: return false
        if (community.policy.pendingEffectivePeriodId != periodId) return false
        community.policy.activePolicyKey = pending
        community.policy.pendingPolicyKey = null
        community.policy.pendingEffectivePeriodId = null
        return true
    }

    fun createTaxWelfareSettlement(
        community: Community,
        periodId: String,
        settlementId: String = "${community.regionNumberId ?: 0}:$periodId"
    ): TaxWelfareSettlement {
        val total = community.getTotalAssets()
        val tax = (total * CommunityConfig.TAX_WELFARE_TAX_RATE.value).toLong().coerceAtLeast(0L)
        val welfare = (community.member.size * CommunityConfig.TAX_WELFARE_PER_MEMBER.value).coerceAtLeast(0L)
        return TaxWelfareSettlement(
            settlementId = settlementId,
            periodId = periodId,
            createdAt = System.currentTimeMillis(),
            totalAssetsAtFreeze = total,
            taxAmount = tax,
            welfareAmount = welfare
        )
    }

    fun freezeTaxWelfare(community: Community, periodId: String): TaxWelfareSettlement {
        val existing = community.taxWelfareSettlements.firstOrNull { it.periodId == periodId }
        if (existing != null) return existing
        val settlement = createTaxWelfareSettlement(community, periodId)
        community.taxWelfareSettlements.add(settlement)
        return settlement
    }

    fun applyTaxWelfare(community: Community, settlement: TaxWelfareSettlement): Boolean {
        if (settlement.status == TaxWelfareSettlementStatus.APPLIED) return true
        return try {
            if (settlement.taxAmount > 0L) {
                community.expenditures.add(Turnover(settlement.taxAmount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.tax", listOf(settlement.periodId)))
            }
            if (settlement.welfareAmount > 0L) {
                community.communityIncome.add(Turnover(settlement.welfareAmount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.welfare", listOf(settlement.periodId)))
            }
            settlement.status = TaxWelfareSettlementStatus.APPLIED
            settlement.failureReason = null
            true
        } catch (e: Exception) {
            settlement.status = TaxWelfareSettlementStatus.FAILED
            settlement.failureReason = e.message ?: e::class.java.simpleName
            settlement.retryCount++
            settlement.nextRetryAt = System.currentTimeMillis() + CommunityConfig.TAX_WELFARE_RETRY_DELAY_SECONDS.value * 1000L
            false
        }
    }
}
