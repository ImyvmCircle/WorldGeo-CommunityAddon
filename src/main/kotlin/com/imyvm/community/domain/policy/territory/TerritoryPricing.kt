package com.imyvm.community.domain.policy.territory

import com.imyvm.community.infra.PricingConfig
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey

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
            freeArea = if (isManor) PricingConfig.MANOR_FREE_AREA.value else PricingConfig.REALM_FREE_AREA.value,
            pricePerUnit = if (isManor) PricingConfig.MANOR_AREA_PRICE_PER_UNIT.value else PricingConfig.REALM_AREA_PRICE_PER_UNIT.value,
            unitSize = if (isManor) PricingConfig.MANOR_AREA_UNIT_SIZE.value else PricingConfig.REALM_AREA_UNIT_SIZE.value,
            refundRate = PricingConfig.AREA_REFUND_RATE.value
        )
    }
    
    fun calculateCreationCost(area: Double, isManor: Boolean): CreationCostResult {
        val baseCost = if (isManor) PricingConfig.PRICE_MANOR.value else PricingConfig.PRICE_REALM.value
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
            isManor && !isScope -> PricingConfig.PERMISSION_BASE_COST_MANOR_REGION.value
            !isManor && !isScope -> PricingConfig.PERMISSION_BASE_COST_REALM_REGION.value
            isManor -> PricingConfig.PERMISSION_BASE_COST_MANOR_SCOPE.value
            else -> PricingConfig.PERMISSION_BASE_COST_REALM_SCOPE.value
        }

        val coefficientPerUnit = getPermissionCoefficientPerUnit(permissionKey)

        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value
        val areaCost = (area / unitSize * coefficientPerUnit).toLong()
        val targetedAreaCost = if (isPlayerTarget) areaCost / PricingConfig.PERMISSION_TARGET_PLAYER_DENOMINATOR.value else areaCost
        return baseCost + targetedAreaCost
    }

    fun getPermissionCoefficientPerUnit(permissionKey: PermissionKey): Long {
        return when (permissionKey) {
            PermissionKey.BUILD_BREAK -> PricingConfig.PERMISSION_BUILD_BREAK_COEFFICIENT_PER_UNIT.value
            PermissionKey.BUILD -> PricingConfig.PERMISSION_BUILD_COEFFICIENT_PER_UNIT.value
            PermissionKey.BREAK -> PricingConfig.PERMISSION_BREAK_COEFFICIENT_PER_UNIT.value
            PermissionKey.BUCKET_BUILD -> PricingConfig.PERMISSION_BUCKET_BUILD_COEFFICIENT_PER_UNIT.value
            PermissionKey.BUCKET_SCOOP -> PricingConfig.PERMISSION_BUCKET_SCOOP_COEFFICIENT_PER_UNIT.value
            PermissionKey.INTERACTION -> PricingConfig.PERMISSION_INTERACTION_COEFFICIENT_PER_UNIT.value
            PermissionKey.CONTAINER -> PricingConfig.PERMISSION_CONTAINER_COEFFICIENT_PER_UNIT.value
            PermissionKey.REDSTONE -> PricingConfig.PERMISSION_REDSTONE_COEFFICIENT_PER_UNIT.value
            PermissionKey.TRADE -> PricingConfig.PERMISSION_TRADE_COEFFICIENT_PER_UNIT.value
            PermissionKey.PVP -> PricingConfig.PERMISSION_PVP_COEFFICIENT_PER_UNIT.value
            PermissionKey.ANIMAL_KILLING -> PricingConfig.PERMISSION_ANIMAL_KILLING_COEFFICIENT_PER_UNIT.value
            PermissionKey.VILLAGER_KILLING -> PricingConfig.PERMISSION_VILLAGER_KILLING_COEFFICIENT_PER_UNIT.value
            PermissionKey.THROWABLE -> PricingConfig.PERMISSION_THROWABLE_COEFFICIENT_PER_UNIT.value
            PermissionKey.EGG_USE -> PricingConfig.PERMISSION_EGG_USE_COEFFICIENT_PER_UNIT.value
            PermissionKey.SNOWBALL_USE -> PricingConfig.PERMISSION_SNOWBALL_USE_COEFFICIENT_PER_UNIT.value
            PermissionKey.POTION_USE -> PricingConfig.PERMISSION_POTION_USE_COEFFICIENT_PER_UNIT.value
            PermissionKey.FARMING -> PricingConfig.PERMISSION_FARMING_COEFFICIENT_PER_UNIT.value
            PermissionKey.IGNITE -> PricingConfig.PERMISSION_IGNITE_COEFFICIENT_PER_UNIT.value
            PermissionKey.ARMOR_STAND -> PricingConfig.PERMISSION_ARMOR_STAND_COEFFICIENT_PER_UNIT.value
            PermissionKey.ITEM_FRAME -> PricingConfig.PERMISSION_ITEM_FRAME_COEFFICIENT_PER_UNIT.value
            else -> 0L
        }
    }

    fun calculateRuleSettingCost(
        area: Double,
        ruleKey: RuleKey,
        isManor: Boolean,
        isScope: Boolean
    ): Long {
        val baseCost = when {
            isManor && !isScope -> PricingConfig.PERMISSION_BASE_COST_MANOR_REGION.value
            !isManor && !isScope -> PricingConfig.PERMISSION_BASE_COST_REALM_REGION.value
            isManor -> PricingConfig.PERMISSION_BASE_COST_MANOR_SCOPE.value
            else -> PricingConfig.PERMISSION_BASE_COST_REALM_SCOPE.value
        }

        val coefficientPerUnit = getRuleCoefficientPerUnit(ruleKey)
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value
        val areaCost = (area / unitSize * coefficientPerUnit).toLong()
        return baseCost + areaCost
    }

    fun getRuleCoefficientPerUnit(ruleKey: RuleKey): Long {
        return when (ruleKey) {
            RuleKey.SPAWN_MONSTERS -> PricingConfig.RULE_SPAWN_MONSTERS_COEFFICIENT_PER_UNIT.value
            RuleKey.SPAWN_PHANTOMS -> PricingConfig.RULE_SPAWN_PHANTOMS_COEFFICIENT_PER_UNIT.value
            RuleKey.TNT_BLOCK_PROTECTION -> PricingConfig.RULE_TNT_BLOCK_PROTECTION_COEFFICIENT_PER_UNIT.value
        }
    }
}
