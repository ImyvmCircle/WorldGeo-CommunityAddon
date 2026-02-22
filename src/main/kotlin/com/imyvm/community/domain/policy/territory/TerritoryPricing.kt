package com.imyvm.community.domain.policy.territory

import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityPricingConfig
import com.imyvm.iwg.domain.component.PermissionKey

data class PricingConfiguration(
    val freeArea: Double,
    val pricePerUnit: Long,
    val unitSize: Double,
    val refundRate: Double
)

data class CreationCostResult(
    val baseCost: Long,
    val areaCost: Long,
    val totalCost: Long,
    val area: Double
)

data class ModificationCostResult(
    val areaChange: Double,
    val areaBefore: Double,
    val areaAfter: Double,
    val cost: Long,
    val isIncrease: Boolean
)

object TerritoryPricing {
    
    fun getPricingConfig(isManor: Boolean): PricingConfiguration {
        return PricingConfiguration(
            freeArea = if (isManor) CommunityConfig.MANOR_FREE_AREA.value else CommunityConfig.REALM_FREE_AREA.value,
            pricePerUnit = if (isManor) CommunityConfig.MANOR_AREA_PRICE_PER_UNIT.value else CommunityConfig.REALM_AREA_PRICE_PER_UNIT.value,
            unitSize = if (isManor) CommunityConfig.MANOR_AREA_UNIT_SIZE.value else CommunityConfig.REALM_AREA_UNIT_SIZE.value,
            refundRate = CommunityConfig.AREA_REFUND_RATE.value
        )
    }
    
    fun calculateCreationCost(area: Double, isManor: Boolean): CreationCostResult {
        val baseCost = if (isManor) CommunityConfig.PRICE_MANOR.value else CommunityConfig.PRICE_REALM.value
        val config = getPricingConfig(isManor)
        
        val areaCost: Long = if (area <= config.freeArea) {
            0L
        } else {
            ((area - config.freeArea) / config.unitSize * config.pricePerUnit).toLong()
        }
        
        return CreationCostResult(
            baseCost = baseCost,
            areaCost = areaCost,
            totalCost = baseCost + areaCost,
            area = area
        )
    }
    
    fun calculateModificationCost(areaChange: Double, currentTotalArea: Double, isManor: Boolean): ModificationCostResult {
        val config = getPricingConfig(isManor)
        
        val isIncrease = areaChange > 0
        val newTotalArea = currentTotalArea + areaChange
        
        val cost: Long = if (isIncrease) {
            (areaChange / config.unitSize * config.pricePerUnit).toLong()
        } else {
            val areaDecreased = -areaChange
            val areaAfterDecrease = currentTotalArea - areaDecreased
            
            if (areaAfterDecrease >= config.freeArea) {
                (areaDecreased / config.unitSize * config.pricePerUnit * config.refundRate).toLong()
            } else {
                val refundableArea = if (currentTotalArea > config.freeArea) {
                    currentTotalArea - config.freeArea
                } else {
                    0.0
                }
                (refundableArea / config.unitSize * config.pricePerUnit * config.refundRate).toLong()
            }
        }
        
        return ModificationCostResult(
            areaChange = areaChange,
            areaBefore = currentTotalArea,
            areaAfter = newTotalArea,
            cost = if (isIncrease) cost else -cost,
            isIncrease = isIncrease
        )
    }

    fun calculatePermissionSettingCost(
        area: Double,
        permissionKey: PermissionKey,
        isManor: Boolean,
        isScope: Boolean,
        isPlayerTarget: Boolean
    ): Long {
        val baseCost = when {
            isManor && !isScope -> CommunityPricingConfig.PERMISSION_BASE_COST_MANOR_REGION.value
            !isManor && !isScope -> CommunityPricingConfig.PERMISSION_BASE_COST_REALM_REGION.value
            isManor -> CommunityPricingConfig.PERMISSION_BASE_COST_MANOR_SCOPE.value
            else -> CommunityPricingConfig.PERMISSION_BASE_COST_REALM_SCOPE.value
        }

        val coefficientPerUnit = when (permissionKey) {
            PermissionKey.BUILD_BREAK -> CommunityPricingConfig.PERMISSION_BUILD_BREAK_COEFFICIENT_PER_UNIT.value
            PermissionKey.CONTAINER -> CommunityPricingConfig.PERMISSION_CONTAINER_COEFFICIENT_PER_UNIT.value
            else -> 0L
        }

        val unitSize = CommunityPricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value
        val areaCost = (area / unitSize * coefficientPerUnit).toLong()
        val targetedAreaCost = if (isPlayerTarget) areaCost / CommunityPricingConfig.PERMISSION_TARGET_PLAYER_DENOMINATOR.value else areaCost
        return baseCost + targetedAreaCost
    }
}
