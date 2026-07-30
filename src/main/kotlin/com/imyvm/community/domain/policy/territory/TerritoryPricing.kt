package com.imyvm.community.domain.policy.territory

import com.imyvm.community.infra.PricingConfig
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class PricingConfiguration(
    val freeArea: Double,
    val pricePerUnit: Long,
    val unitSize: Double,
    val refundRate: Double
)

data class DimensionBracketCost(
    val tierNum: Int,
    val bracketLow: Double,
    val bracketHigh: Double,
    val areaInBracket: Double,
    val bracketMultiplier: Long,
    val cost: Long
)

data class DimensionCostBreakdown(
    val dimensionId: String,
    val areaBefore: Double,
    val areaAfter: Double,
    val dimensionMultiplier: Long,
    val grossCost: Long,
    val subtotal: Long,
    val brackets: List<DimensionBracketCost>
)

data class FixedPriceResult(
    val baseCost: Long,
    val totalCost: Long,
    val dimensionId: String,
    val dimensionMultiplier: Long
)

data class SettingCostResult(
    val cost: Long,
    val denominator: Long,
    val dimensionCosts: List<DimensionCostBreakdown>,
    val areaByDimension: Map<String, Double> = emptyMap()
)

data class CreationCostResult(
    val baseCost: Long,
    val areaCost: Long,
    val totalCost: Long,
    val area: Double,
    val dimensionCosts: List<DimensionCostBreakdown> = emptyList(),
    val areaByDimension: Map<String, Double> = emptyMap()
)

data class ModificationCostResult(
    val areaChange: Double,
    val areaBefore: Double,
    val areaAfter: Double,
    val cost: Long,
    val isIncrease: Boolean,
    val dimensionCosts: List<DimensionCostBreakdown> = emptyList(),
    val areaBeforeByDimension: Map<String, Double> = emptyMap(),
    val areaAfterByDimension: Map<String, Double> = emptyMap()
)

data class SettingItemCostChange(
    val settingKeyName: String,
    val scopeName: String?,
    val playerName: String?,
    val areaOld: Double,
    val areaNew: Double,
    val costChange: Long,
    val areaOldByDimension: Map<String, Double> = emptyMap(),
    val areaNewByDimension: Map<String, Double> = emptyMap()
)

object TerritoryPricing {

    const val DIMENSION_OVERWORLD = "minecraft:overworld"
    const val DIMENSION_NETHER = "minecraft:the_nether"
    const val DIMENSION_END = "minecraft:the_end"

    private val DIMENSION_ORDER = listOf(DIMENSION_OVERWORLD, DIMENSION_NETHER, DIMENSION_END)
    private const val MAX_EXACT_AREA = 9_007_199_254_740_991L
    private const val MAX_PRICING_EXPONENT = 1024

    fun orderedDimensionIds(dimensionIds: Collection<String>): List<String> = sortDimensionIds(dimensionIds)

    fun forEachLandBracket(
        fromArea: Double,
        toArea: Double,
        isManor: Boolean,
        action: (tierNum: Int, bracketLow: Double, bracketHigh: Double, areaInBracket: Double, multiplier: Long, cost: Long) -> Unit
    ) {
        val config = getPricingConfig(isManor)
        val freeArea = positiveAreaToLong(config.freeArea, "land pricing free area")
        val unitSize = positiveAreaToLong(config.unitSize, "land pricing unit size")
        val lo = maxOf(areaToLong(fromArea), freeArea)
        val hi = maxOf(areaToLong(toArea), freeArea)
        if (lo >= hi) return
        var tierLow = freeArea
        var multiplier = 1L
        var tierNum = 1
        while (nextTier(tierLow) <= lo) {
            tierLow = nextTier(tierLow)
            multiplier = Math.multiplyExact(multiplier, 2L)
            tierNum = Math.incrementExact(tierNum)
        }
        while (tierLow < hi) {
            val tierHigh = nextTier(tierLow)
            val bracketFrom = maxOf(lo, tierLow)
            val bracketTo = minOf(hi, tierHigh)
            val areaInBracket = Math.subtractExact(bracketTo, bracketFrom)
            if (areaInBracket > 0L) {
                action(
                    tierNum, tierLow.toDouble(), tierHigh.toDouble(), areaInBracket.toDouble(), multiplier,
                    bracketCost(areaInBracket, config.pricePerUnit, multiplier, unitSize)
                )
            }
            tierLow = tierHigh
            multiplier = Math.multiplyExact(multiplier, 2L)
            tierNum = Math.incrementExact(tierNum)
        }
    }

    fun forEachSettingBracket(
        fromArea: Double,
        toArea: Double,
        coefficientPerUnit: Long,
        unitSize: Double,
        freeArea: Double,
        action: (tierNum: Int, bracketLow: Double, bracketHigh: Double, areaInBracket: Double, multiplier: Long, cost: Long) -> Unit
    ) {
        val from = areaToLong(fromArea)
        val to = areaToLong(toArea)
        if (from >= to || coefficientPerUnit == 0L) return
        require(coefficientPerUnit > 0L) { "Setting coefficient must not be negative" }
        val unit = positiveAreaToLong(unitSize, "setting pricing unit size")
        var tierLow = 0L
        var tierHigh = positiveAreaToLong(freeArea, "setting pricing free area")
        var multiplier = 1L
        var tierNum = 1
        while (tierHigh <= from) {
            tierLow = tierHigh
            tierHigh = nextTier(tierLow)
            multiplier = Math.incrementExact(multiplier)
            tierNum = Math.incrementExact(tierNum)
        }
        while (tierLow < to) {
            val bracketFrom = maxOf(from, tierLow)
            val bracketTo = minOf(to, tierHigh)
            val areaInBracket = Math.subtractExact(bracketTo, bracketFrom)
            if (areaInBracket > 0L) {
                action(
                    tierNum, tierLow.toDouble(), tierHigh.toDouble(), areaInBracket.toDouble(), multiplier,
                    bracketCost(areaInBracket, coefficientPerUnit, multiplier, unit)
                )
            }
            tierLow = tierHigh
            tierHigh = nextTier(tierLow)
            multiplier = Math.incrementExact(multiplier)
            tierNum = Math.incrementExact(tierNum)
        }
    }

    fun normalizeDimensionId(rawDimensionId: String?): String {
        return when (rawDimensionId) {
            null, "", DIMENSION_OVERWORLD -> DIMENSION_OVERWORLD
            DIMENSION_NETHER -> DIMENSION_NETHER
            DIMENSION_END -> DIMENSION_END
            else -> when {
                rawDimensionId.contains("the_nether") -> DIMENSION_NETHER
                rawDimensionId.contains("the_end") -> DIMENSION_END
                rawDimensionId.contains("overworld") -> DIMENSION_OVERWORLD
                else -> rawDimensionId
            }
        }
    }

    fun getDimensionDisplayKey(dimensionId: String): String {
        return when (normalizeDimensionId(dimensionId)) {
            DIMENSION_OVERWORLD -> "community.dimension.overworld"
            DIMENSION_NETHER -> "community.dimension.nether"
            DIMENSION_END -> "community.dimension.end"
            else -> "community.dimension.other"
        }
    }

    fun getDimensionMultiplier(dimensionId: String): Long {
        return when (normalizeDimensionId(dimensionId)) {
            DIMENSION_NETHER -> PricingConfig.DIMENSION_PRICE_MULTIPLIER_NETHER.value
            DIMENSION_END -> PricingConfig.DIMENSION_PRICE_MULTIPLIER_END.value
            else -> 1L
        }
    }

    fun getScopeDimensionId(scope: GeoScope): String {
        return normalizeDimensionId(scope.worldId.toString())
    }

    fun getScopeAreaByDimension(scope: GeoScope, areaOverride: Double? = null): Map<String, Double> {
        val area = roundArea(areaOverride ?: scope.geoShape?.calculateArea() ?: 0.0)
        if (area <= 0.0) return emptyMap()
        return linkedMapOf(getScopeDimensionId(scope) to area)
    }

    fun getRegionAreaByDimension(region: Region): Map<String, Double> {
        val totals = linkedMapOf<String, Long>()
        for (scope in region.geometryScope) {
            val area = areaToLong(scope.geoShape?.calculateArea() ?: 0.0)
            if (area == 0L) continue
            val dimensionId = getScopeDimensionId(scope)
            totals[dimensionId] = Math.addExact(totals[dimensionId] ?: 0L, area)
        }
        return orderAreaMap(totals.mapValues { it.value.toDouble() })
    }

    fun buildAreaMap(dimensionId: String, area: Double): Map<String, Double> {
        val roundedArea = roundArea(area)
        if (roundedArea <= 0.0) return emptyMap()
        return linkedMapOf(normalizeDimensionId(dimensionId) to roundedArea)
    }

    fun applyAreaChange(
        currentAreaByDimension: Map<String, Double>,
        dimensionId: String,
        areaChange: Double
    ): Map<String, Double> {
        val normalized = normalizeDimensionId(dimensionId)
        val updated = currentAreaByDimension.toMutableMap()
        val current = areaToLong(updated[normalized] ?: 0.0)
        val next = maxOf(0L, Math.addExact(current, signedAreaToLong(areaChange)))
        if (next == 0L) {
            updated.remove(normalized)
        } else {
            updated[normalized] = next.toDouble()
        }
        return orderAreaMap(updated)
    }

    fun applyGeoscopePriceMultiplier(baseCost: Long, dimensionId: String): FixedPriceResult {
        require(baseCost >= 0L) { "Base cost must not be negative" }
        val normalized = normalizeDimensionId(dimensionId)
        val multiplier = getDimensionMultiplier(normalized)
        return FixedPriceResult(
            baseCost = baseCost,
            totalCost = Math.multiplyExact(baseCost, multiplier),
            dimensionId = normalized,
            dimensionMultiplier = multiplier
        )
    }

    fun calculateRefund(amount: Long): Long =
        Math.negateExact(applyRate(amount, PricingConfig.AREA_REFUND_RATE.value))

    fun applySoftLimitMultiplier(amount: Long, multiplier: Double, exponent: Int): Long {
        require(amount >= 0L && multiplier.isFinite() && multiplier >= 1.0) { "Invalid scope soft-limit price" }
        require(exponent in 0..MAX_PRICING_EXPONENT) { "Scope soft-limit exponent exceeds supported range" }
        return BigDecimal.valueOf(amount)
            .multiply(BigDecimal.valueOf(multiplier).pow(exponent))
            .setScale(0, RoundingMode.DOWN)
            .longValueExact()
    }

    fun calculateLandCostTotal(area: Double, isManor: Boolean): Long {
        return calculateLandCostTotal(buildAreaMap(DIMENSION_OVERWORLD, area), isManor)
    }

    fun calculateLandCostTotal(areaByDimension: Map<String, Double>, isManor: Boolean): Long {
        return sumExact(calculateLandCostBreakdown(areaByDimension, isManor).map { it.subtotal })
    }

    fun calculateSettingCostTotal(
        area: Double,
        coefficientPerUnit: Long,
        unitSize: Double,
        isPlayerTarget: Boolean,
        freeArea: Double
    ): Long {
        return calculateSettingCostTotal(buildAreaMap(DIMENSION_OVERWORLD, area), coefficientPerUnit, unitSize, isPlayerTarget, freeArea)
    }

    fun calculateSettingCostTotal(
        areaByDimension: Map<String, Double>,
        coefficientPerUnit: Long,
        unitSize: Double,
        isPlayerTarget: Boolean,
        freeArea: Double
    ): Long {
        return calculateSettingCostResult(areaByDimension, coefficientPerUnit, unitSize, isPlayerTarget, freeArea).cost
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
        return calculateSettingCostChange(
            buildAreaMap(DIMENSION_OVERWORLD, areaOld),
            buildAreaMap(DIMENSION_OVERWORLD, areaNew),
            coefficientPerUnit,
            unitSize,
            isPlayerTarget,
            freeArea,
            refundRate
        )
    }

    fun calculateSettingCostChange(
        areaOldByDimension: Map<String, Double>,
        areaNewByDimension: Map<String, Double>,
        coefficientPerUnit: Long,
        unitSize: Double,
        isPlayerTarget: Boolean,
        freeArea: Double,
        refundRate: Double
    ): Long {
        val costOld = calculateSettingCostResult(areaOldByDimension, coefficientPerUnit, unitSize, isPlayerTarget, freeArea).cost
        val costNew = calculateSettingCostResult(areaNewByDimension, coefficientPerUnit, unitSize, isPlayerTarget, freeArea).cost
        return if (sumArea(areaNewByDimension) >= sumArea(areaOldByDimension)) {
            Math.subtractExact(costNew, costOld)
        } else {
            Math.negateExact(applyRate(Math.subtractExact(costOld, costNew), refundRate))
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
        return calculateCreationCost(buildAreaMap(DIMENSION_OVERWORLD, area), area, isManor)
    }

    fun calculateCreationCost(region: Region, isManor: Boolean): CreationCostResult {
        return calculateCreationCost(getRegionAreaByDimension(region), region.calculateTotalArea(), isManor)
    }

    fun calculateCreationCost(areaByDimension: Map<String, Double>, totalArea: Double, isManor: Boolean): CreationCostResult {
        val orderedAreaByDimension = orderAreaMap(areaByDimension)
        val baseCost = if (isManor) PricingConfig.PRICE_MANOR.value else PricingConfig.PRICE_REALM.value
        val dimensionCosts = calculateLandCostBreakdown(orderedAreaByDimension, isManor)
        val areaCost = sumExact(dimensionCosts.map { it.subtotal })
        return CreationCostResult(
            baseCost = baseCost,
            areaCost = areaCost,
            totalCost = Math.addExact(baseCost, areaCost),
            area = roundArea(totalArea),
            dimensionCosts = dimensionCosts,
            areaByDimension = orderedAreaByDimension
        )
    }

    fun calculateModificationCost(areaChange: Double, currentTotalArea: Double, isManor: Boolean): ModificationCostResult {
        val current = areaToLong(currentTotalArea)
        val next = maxOf(0L, Math.addExact(current, signedAreaToLong(areaChange)))
        return calculateModificationCost(
            buildAreaMap(DIMENSION_OVERWORLD, current.toDouble()),
            buildAreaMap(DIMENSION_OVERWORLD, next.toDouble()),
            isManor
        )
    }

    fun calculateModificationCost(
        areaBeforeByDimension: Map<String, Double>,
        areaAfterByDimension: Map<String, Double>,
        isManor: Boolean
    ): ModificationCostResult {
        val config = getPricingConfig(isManor)
        val orderedAreaBeforeByDimension = orderAreaMap(areaBeforeByDimension)
        val orderedAreaAfterByDimension = orderAreaMap(areaAfterByDimension)
        val isIncrease = sumArea(orderedAreaAfterByDimension) >= sumArea(orderedAreaBeforeByDimension)
        val dimensionCosts = mutableListOf<DimensionCostBreakdown>()

        for (dimensionId in sortDimensionIds(orderedAreaBeforeByDimension.keys + orderedAreaAfterByDimension.keys)) {
            val areaBefore = roundArea(orderedAreaBeforeByDimension[dimensionId] ?: 0.0)
            val areaAfter = roundArea(orderedAreaAfterByDimension[dimensionId] ?: 0.0)
            if (areaBefore == areaAfter) continue

            val dimensionMultiplier = getDimensionMultiplier(dimensionId)
            val brackets = mutableListOf<DimensionBracketCost>()
            var grossCost = 0L

            if (areaAfter >= areaBefore) {
                forEachLandBracket(areaBefore, areaAfter, isManor) { tierNum, low, high, areaIn, bracketMultiplier, cost ->
                    val adjustedCost = Math.multiplyExact(cost, dimensionMultiplier)
                    grossCost = Math.addExact(grossCost, adjustedCost)
                    brackets.add(
                        DimensionBracketCost(tierNum, low, high, areaIn, bracketMultiplier, adjustedCost)
                    )
                }
                dimensionCosts.add(
                    DimensionCostBreakdown(
                        dimensionId = dimensionId,
                        areaBefore = areaBefore,
                        areaAfter = areaAfter,
                        dimensionMultiplier = dimensionMultiplier,
                        grossCost = grossCost,
                        subtotal = grossCost,
                        brackets = brackets
                    )
                )
            } else {
                forEachLandBracket(areaAfter, areaBefore, isManor) { tierNum, low, high, areaIn, bracketMultiplier, cost ->
                    val adjustedCost = Math.multiplyExact(cost, dimensionMultiplier)
                    grossCost = Math.addExact(grossCost, adjustedCost)
                    brackets.add(
                        DimensionBracketCost(tierNum, low, high, areaIn, bracketMultiplier, adjustedCost)
                    )
                }
                dimensionCosts.add(
                    DimensionCostBreakdown(
                        dimensionId = dimensionId,
                        areaBefore = areaBefore,
                        areaAfter = areaAfter,
                        dimensionMultiplier = dimensionMultiplier,
                        grossCost = grossCost,
                        subtotal = Math.negateExact(applyRate(grossCost, config.refundRate)),
                        brackets = brackets
                    )
                )
            }
        }

        return ModificationCostResult(
            areaChange = roundSignedArea(sumArea(orderedAreaAfterByDimension) - sumArea(orderedAreaBeforeByDimension)),
            areaBefore = roundArea(sumArea(orderedAreaBeforeByDimension)),
            areaAfter = roundArea(sumArea(orderedAreaAfterByDimension)),
            cost = sumExact(dimensionCosts.map { it.subtotal }),
            isIncrease = isIncrease,
            dimensionCosts = dimensionCosts,
            areaBeforeByDimension = orderedAreaBeforeByDimension,
            areaAfterByDimension = orderedAreaAfterByDimension
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
        return calculatePermissionSettingCost(
            buildAreaMap(DIMENSION_OVERWORLD, area),
            permissionKey,
            isManor,
            isScope,
            isPlayerTarget,
            isRestoringDefault
        )
    }

    fun calculatePermissionSettingCost(
        areaByDimension: Map<String, Double>,
        permissionKey: PermissionKey,
        isManor: Boolean,
        isScope: Boolean,
        isPlayerTarget: Boolean,
        isRestoringDefault: Boolean = false
    ): Long {
        val result = calculatePermissionSettingCostResult(areaByDimension, permissionKey, isManor, isPlayerTarget)
        return if (isRestoringDefault) {
            Math.negateExact(applyRate(result.cost, PricingConfig.AREA_REFUND_RATE.value))
        } else {
            result.cost
        }
    }

    fun calculatePermissionSettingCostResult(
        areaByDimension: Map<String, Double>,
        permissionKey: PermissionKey,
        isManor: Boolean,
        isPlayerTarget: Boolean
    ): SettingCostResult {
        val coefficientPerUnit = getPermissionCoefficientPerUnit(permissionKey)
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value.toDouble()
        val freeArea = if (isManor) PricingConfig.MANOR_FREE_AREA.value else PricingConfig.REALM_FREE_AREA.value
        return calculateSettingCostResult(areaByDimension, coefficientPerUnit, unitSize, isPlayerTarget, freeArea)
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
            PermissionKey.WIND_CHARGE_USE -> PricingConfig.PERMISSION_WIND_CHARGE_USE_COEFFICIENT_PER_UNIT.value
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
        return calculateRuleSettingCost(
            buildAreaMap(DIMENSION_OVERWORLD, area),
            ruleKey,
            isManor,
            isScope,
            isRestoringDefault
        )
    }

    fun calculateRuleSettingCost(
        areaByDimension: Map<String, Double>,
        ruleKey: RuleKey,
        isManor: Boolean,
        isScope: Boolean,
        isRestoringDefault: Boolean = false
    ): Long {
        val result = calculateRuleSettingCostResult(areaByDimension, ruleKey, isManor)
        return if (isRestoringDefault) {
            Math.negateExact(applyRate(result.cost, PricingConfig.AREA_REFUND_RATE.value))
        } else {
            result.cost
        }
    }

    fun calculateRuleSettingCostResult(
        areaByDimension: Map<String, Double>,
        ruleKey: RuleKey,
        isManor: Boolean
    ): SettingCostResult {
        val coefficientPerUnit = getRuleCoefficientPerUnit(ruleKey)
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value.toDouble()
        val freeArea = if (isManor) PricingConfig.MANOR_FREE_AREA.value else PricingConfig.REALM_FREE_AREA.value
        return calculateSettingCostResult(areaByDimension, coefficientPerUnit, unitSize, false, freeArea)
    }

    fun getRuleCoefficientPerUnit(ruleKey: RuleKey): Long {
        return when (ruleKey) {
            RuleKey.SPAWN_MONSTERS -> PricingConfig.RULE_SPAWN_MONSTERS_COEFFICIENT_PER_UNIT.value
            RuleKey.SPAWN_PHANTOMS -> PricingConfig.RULE_SPAWN_PHANTOMS_COEFFICIENT_PER_UNIT.value
            RuleKey.TNT_BLOCK_PROTECTION -> PricingConfig.RULE_TNT_BLOCK_PROTECTION_COEFFICIENT_PER_UNIT.value
            RuleKey.ENDERMAN_BLOCK_PICKUP -> PricingConfig.RULE_ENDERMAN_BLOCK_PICKUP_COEFFICIENT_PER_UNIT.value
            RuleKey.SCULK_SPREAD -> PricingConfig.RULE_SCULK_SPREAD_COEFFICIENT_PER_UNIT.value
            RuleKey.SNOW_GOLEM_TRAIL -> PricingConfig.RULE_SNOW_GOLEM_TRAIL_COEFFICIENT_PER_UNIT.value
            RuleKey.DISPENSER -> PricingConfig.RULE_DISPENSER_COEFFICIENT_PER_UNIT.value
            RuleKey.PRESSURE_PLATE -> PricingConfig.RULE_PRESSURE_PLATE_COEFFICIENT_PER_UNIT.value
            RuleKey.PISTON -> PricingConfig.RULE_PISTON_COEFFICIENT_PER_UNIT.value
            RuleKey.RPG_FIRE_SPREAD -> PricingConfig.RULE_RPG_FIRE_SPREAD_COEFFICIENT_PER_UNIT.value
            RuleKey.RPG_HUNGER -> PricingConfig.RULE_RPG_HUNGER_COEFFICIENT_PER_UNIT.value
        }
    }

    private fun calculateLandCostBreakdown(
        areaByDimension: Map<String, Double>,
        isManor: Boolean
    ): List<DimensionCostBreakdown> {
        val result = mutableListOf<DimensionCostBreakdown>()
        for ((dimensionId, area) in orderAreaMap(areaByDimension)) {
            if (area <= 0.0) continue
            val dimensionMultiplier = getDimensionMultiplier(dimensionId)
            val brackets = mutableListOf<DimensionBracketCost>()
            var grossCost = 0L
            forEachLandBracket(0.0, area, isManor) { tierNum, low, high, areaIn, bracketMultiplier, cost ->
                val adjustedCost = Math.multiplyExact(cost, dimensionMultiplier)
                grossCost = Math.addExact(grossCost, adjustedCost)
                brackets.add(DimensionBracketCost(tierNum, low, high, areaIn, bracketMultiplier, adjustedCost))
            }
            result.add(
                DimensionCostBreakdown(
                    dimensionId = dimensionId,
                    areaBefore = 0.0,
                    areaAfter = area,
                    dimensionMultiplier = dimensionMultiplier,
                    grossCost = grossCost,
                    subtotal = grossCost,
                    brackets = brackets
                )
            )
        }
        return result
    }

    private fun calculateSettingCostResult(
        areaByDimension: Map<String, Double>,
        coefficientPerUnit: Long,
        unitSize: Double,
        isPlayerTarget: Boolean,
        freeArea: Double
    ): SettingCostResult {
        val denominator = if (isPlayerTarget) PricingConfig.PERMISSION_TARGET_PLAYER_DENOMINATOR.value else 1L
        val orderedAreaByDimension = orderAreaMap(areaByDimension)
        if (coefficientPerUnit == 0L) return SettingCostResult(0L, denominator, emptyList(), orderedAreaByDimension)

        val result = mutableListOf<DimensionCostBreakdown>()
        for ((dimensionId, area) in orderedAreaByDimension) {
            if (area <= 0.0) continue
            val dimensionMultiplier = getDimensionMultiplier(dimensionId)
            val brackets = mutableListOf<DimensionBracketCost>()
            var grossCost = 0L
            forEachSettingBracket(0.0, area, coefficientPerUnit, unitSize, freeArea) { tierNum, low, high, areaIn, bracketMultiplier, cost ->
                val adjustedCost = Math.multiplyExact(cost, dimensionMultiplier)
                grossCost = Math.addExact(grossCost, adjustedCost)
                brackets.add(DimensionBracketCost(tierNum, low, high, areaIn, bracketMultiplier, adjustedCost))
            }
            result.add(
                DimensionCostBreakdown(
                    dimensionId = dimensionId,
                    areaBefore = 0.0,
                    areaAfter = area,
                    dimensionMultiplier = dimensionMultiplier,
                    grossCost = grossCost,
                    subtotal = grossCost,
                    brackets = brackets
                )
            )
        }

        return SettingCostResult(
            cost = sumExact(result.map { it.subtotal }) / denominator,
            denominator = denominator,
            dimensionCosts = result,
            areaByDimension = orderedAreaByDimension
        )
    }

    private fun orderAreaMap(areaByDimension: Map<String, Double>): Map<String, Double> {
        val ordered = linkedMapOf<String, Double>()
        for (dimensionId in sortDimensionIds(areaByDimension.keys)) {
            val area = roundArea(areaByDimension[dimensionId] ?: 0.0)
            if (area > 0.0) {
                ordered[dimensionId] = area
            }
        }
        return ordered
    }

    private fun sortDimensionIds(dimensionIds: Collection<String>): List<String> {
        return dimensionIds.map(::normalizeDimensionId).distinct().sortedWith(
            compareBy<String> { DIMENSION_ORDER.indexOf(it).let { index -> if (index >= 0) index else Int.MAX_VALUE } }
                .thenBy { it }
        )
    }

    private fun sumArea(areaByDimension: Map<String, Double>): Double {
        val total = areaByDimension.values.fold(0L) { sum, area -> Math.addExact(sum, areaToLong(area)) }
        require(total <= MAX_EXACT_AREA) { "Total area exceeds supported range" }
        return total.toDouble()
    }

    private fun roundArea(area: Double): Double = areaToLong(area).toDouble()

    private fun roundSignedArea(area: Double): Double = signedAreaToLong(area).toDouble()

    private fun signedAreaToLong(area: Double): Long {
        require(area.isFinite()) { "Area must be finite" }
        val rounded = BigDecimal.valueOf(area).setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(rounded in -MAX_EXACT_AREA..MAX_EXACT_AREA) { "Area exceeds supported range" }
        return rounded
    }

    private fun areaToLong(area: Double): Long {
        require(area.isFinite() && area >= 0.0) { "Area must be finite and non-negative" }
        val rounded = BigDecimal.valueOf(area).setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(rounded <= MAX_EXACT_AREA) { "Area exceeds supported range" }
        return rounded
    }

    private fun positiveAreaToLong(area: Double, name: String): Long =
        areaToLong(area).also { require(it > 0L) { name + " must round to a positive integer area" } }

    private fun nextTier(current: Long): Long {
        require(current > 0L) { "Pricing tier must be positive" }
        val next = Math.multiplyExact(current, 4L)
        require(next > current) { "Pricing tier must advance" }
        return next
    }

    private fun bracketCost(area: Long, coefficient: Long, multiplier: Long, unitSize: Long): Long {
        require(area >= 0L && coefficient >= 0L && multiplier > 0L && unitSize > 0L) { "Invalid pricing quantity" }
        return BigInteger.valueOf(area)
            .multiply(BigInteger.valueOf(coefficient))
            .multiply(BigInteger.valueOf(multiplier))
            .divide(BigInteger.valueOf(unitSize))
            .longValueExact()
    }

    private fun applyRate(amount: Long, rate: Double): Long {
        require(amount >= 0L && rate.isFinite() && rate in 0.0..1.0) { "Invalid pricing rate" }
        return BigDecimal.valueOf(amount)
            .multiply(BigDecimal.valueOf(rate))
            .setScale(0, RoundingMode.DOWN)
            .longValueExact()
    }

    private fun sumExact(values: Iterable<Long>): Long =
        values.fold(0L) { sum, value -> Math.addExact(sum, value) }
}
