package com.imyvm.community.domain.policy.territory

import com.imyvm.community.infra.PricingConfig
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
import kotlin.math.ln

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
    val area: Double,
    val tierIndex: Int,
    val tierMultiplier: Long
)

data class ModificationCostResult(
    val areaChange: Double,
    val areaBefore: Double,
    val areaAfter: Double,
    val cost: Long,
    val isIncrease: Boolean,
    val tierBefore: Int,
    val tierAfter: Int,
    val tierMultiplierBefore: Long,
    val tierMultiplierAfter: Long
)

data class SettingItemCostChange(
    val settingKeyName: String,
    val scopeName: String?,
    val playerName: String?,
    val areaOld: Double,
    val areaNew: Double,
    val costChange: Long
)

object TerritoryPricing {

    fun getLandTierIndex(area: Double, freeArea: Double): Int {
        if (area <= freeArea) return 0
        return (ln(area / freeArea) / ln(4.0)).toInt()
    }

    fun getLandTierMultiplier(tierIndex: Int): Long = (1L shl tierIndex)

    fun calculateLandCostTotal(area: Double, isManor: Boolean): Long {
        val config = getPricingConfig(isManor)
        if (area <= config.freeArea) return 0L
        val tier = getLandTierIndex(area, config.freeArea)
        val multiplier = getLandTierMultiplier(tier)
        return (multiplier * config.pricePerUnit * (area - config.freeArea) / config.unitSize).toLong()
    }

    fun getSettingTierMultiplier(area: Double, freeArea: Double): Int {
        if (area < freeArea) return 1
        return (ln(area / freeArea) / ln(4.0)).toInt() + 2
    }

    fun calculateSettingCostTotal(
        area: Double,
        coefficientPerUnit: Long,
        unitSize: Double,
        isPlayerTarget: Boolean,
        freeArea: Double
    ): Long {
        if (area <= 0.0 || coefficientPerUnit == 0L) return 0L
        val multiplier = getSettingTierMultiplier(area, freeArea)
        val denominator = if (isPlayerTarget) PricingConfig.PERMISSION_TARGET_PLAYER_DENOMINATOR.value else 1L
        return (multiplier * coefficientPerUnit * area / unitSize / denominator).toLong()
    }

    fun calculateSettingCostChange(
        areaOld: Double,
        areaNew: Double,
        coefficientPerUnit: Long,
        unitSize: Double,
        isPlayerTarget: Boolean,
        freeArea: Double,
        refundRate: Double
    ): Long {
        val costOld = calculateSettingCostTotal(areaOld, coefficientPerUnit, unitSize, isPlayerTarget, freeArea)
        val costNew = calculateSettingCostTotal(areaNew, coefficientPerUnit, unitSize, isPlayerTarget, freeArea)
        return if (areaNew >= areaOld) {
            costNew - costOld
        } else {
            ((costOld - costNew) * refundRate * -1).toLong()
        }
    }

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
        val areaCost = calculateLandCostTotal(area, isManor)
        val config = getPricingConfig(isManor)
        val tier = getLandTierIndex(area, config.freeArea)
        return CreationCostResult(
            baseCost = baseCost,
            areaCost = areaCost,
            totalCost = baseCost + areaCost,
            area = area,
            tierIndex = tier,
            tierMultiplier = getLandTierMultiplier(tier)
        )
    }
    
    fun calculateModificationCost(areaChange: Double, currentTotalArea: Double, isManor: Boolean): ModificationCostResult {
        val config = getPricingConfig(isManor)
        val isIncrease = areaChange > 0
        val newTotalArea = currentTotalArea + areaChange
        val costOld = calculateLandCostTotal(currentTotalArea, isManor)
        val costNew = calculateLandCostTotal(newTotalArea, isManor)
        val cost: Long = if (isIncrease) {
            costNew - costOld
        } else {
            -((costOld - costNew) * config.refundRate).toLong()
        }
        val tierBefore = getLandTierIndex(currentTotalArea, config.freeArea)
        val tierAfter = getLandTierIndex(newTotalArea, config.freeArea)
        return ModificationCostResult(
            areaChange = areaChange,
            areaBefore = currentTotalArea,
            areaAfter = newTotalArea,
            cost = cost,
            isIncrease = isIncrease,
            tierBefore = tierBefore,
            tierAfter = tierAfter,
            tierMultiplierBefore = getLandTierMultiplier(tierBefore),
            tierMultiplierAfter = getLandTierMultiplier(tierAfter)
        )
    }

    fun calculatePermissionSettingCost(
        area: Double,
        permissionKey: PermissionKey,
        isManor: Boolean,
        isScope: Boolean,
        isPlayerTarget: Boolean,
        isRestoringDefault: Boolean = false
    ): Long {
        val coefficientPerUnit = getPermissionCoefficientPerUnit(permissionKey)
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value.toDouble()
        val freeArea = if (isManor) PricingConfig.MANOR_FREE_AREA.value else PricingConfig.REALM_FREE_AREA.value
        val refundRate = PricingConfig.AREA_REFUND_RATE.value
        return if (isRestoringDefault) {
            -(calculateSettingCostTotal(area, coefficientPerUnit, unitSize, isPlayerTarget, freeArea) * refundRate).toLong()
        } else {
            calculateSettingCostTotal(area, coefficientPerUnit, unitSize, isPlayerTarget, freeArea)
        }
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
        isScope: Boolean,
        isRestoringDefault: Boolean = false
    ): Long {
        val coefficientPerUnit = getRuleCoefficientPerUnit(ruleKey)
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value.toDouble()
        val freeArea = if (isManor) PricingConfig.MANOR_FREE_AREA.value else PricingConfig.REALM_FREE_AREA.value
        val refundRate = PricingConfig.AREA_REFUND_RATE.value
        return if (isRestoringDefault) {
            -(calculateSettingCostTotal(area, coefficientPerUnit, unitSize, false, freeArea) * refundRate).toLong()
        } else {
            calculateSettingCostTotal(area, coefficientPerUnit, unitSize, false, freeArea)
        }
    }

    fun getRuleCoefficientPerUnit(ruleKey: RuleKey): Long {
        return when (ruleKey) {
            RuleKey.SPAWN_MONSTERS -> PricingConfig.RULE_SPAWN_MONSTERS_COEFFICIENT_PER_UNIT.value
            RuleKey.SPAWN_PHANTOMS -> PricingConfig.RULE_SPAWN_PHANTOMS_COEFFICIENT_PER_UNIT.value
            RuleKey.TNT_BLOCK_PROTECTION -> PricingConfig.RULE_TNT_BLOCK_PROTECTION_COEFFICIENT_PER_UNIT.value
        }
    }
}
