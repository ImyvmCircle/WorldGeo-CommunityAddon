package com.imyvm.community.domain.policy.territory

import com.imyvm.community.infra.PricingConfig
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey

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

    fun orderedDimensionIds(dimensionIds: Collection<String>): List<String> = sortDimensionIds(dimensionIds)

    fun forEachLandBracket(
        fromArea: Double,
        toArea: Double,
        isManor: Boolean,
        action: (tierNum: Int, bracketLow: Double, bracketHigh: Double, areaInBracket: Double, multiplier: Long, cost: Long) -> Unit
    ) {
        val config = getPricingConfig(isManor)
        val x = config.freeArea
        val n = config.pricePerUnit.toDouble() / config.unitSize
        val lo = maxOf(fromArea, x)
        val hi = maxOf(toArea, x)
        if (lo >= hi) return
        var tierLow = x
        var mult = 1L
        var tierNum = 1
        while (tierLow * 4.0 <= lo) {
            tierLow *= 4.0
            mult = mult shl 1
            tierNum++
        }
        while (tierLow < hi) {
            val tierHigh = tierLow * 4.0
            val bracketFrom = maxOf(lo, tierLow)
            val bracketTo = minOf(hi, tierHigh)
            val areaInBracket = bracketTo - bracketFrom
            if (areaInBracket > 0) {
                action(tierNum, tierLow, tierHigh, areaInBracket, mult, (areaInBracket * n * mult).toLong())
            }
            tierLow = tierHigh
            mult = mult shl 1
            tierNum++
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
        if (fromArea >= toArea || coefficientPerUnit == 0L) return
        val x = freeArea
        val n = coefficientPerUnit.toDouble() / unitSize
        var tierLow = 0.0
        var tierHigh = x
        var mult = 1L
        var tierNum = 1
        while (tierHigh <= fromArea) {
            tierLow = tierHigh
            tierHigh = tierLow * 4.0
            mult++
            tierNum++
        }
        while (tierLow < toArea) {
            val bracketFrom = maxOf(fromArea, tierLow)
            val bracketTo = minOf(toArea, tierHigh)
            val areaInBracket = bracketTo - bracketFrom
            if (areaInBracket > 0) {
                action(tierNum, tierLow, tierHigh, areaInBracket, mult, (areaInBracket * n * mult).toLong())
            }
            tierLow = tierHigh
            tierHigh = tierLow * 4.0
            mult++
            tierNum++
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
        val totals = linkedMapOf<String, Double>()
        for (scope in region.geometryScope) {
            val area = scope.geoShape?.calculateArea() ?: 0.0
            if (area <= 0.0) continue
            val dimensionId = getScopeDimensionId(scope)
            totals[dimensionId] = (totals[dimensionId] ?: 0.0) + area
        }
        return orderAreaMap(totals.mapValues { roundArea(it.value) })
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
        val current = updated[normalized] ?: 0.0
        val next = roundArea((current + areaChange).coerceAtLeast(0.0))
        if (next <= 0.0) {
            updated.remove(normalized)
        } else {
            updated[normalized] = next
        }
        return orderAreaMap(updated)
    }

    fun applyGeoscopePriceMultiplier(baseCost: Long, dimensionId: String): FixedPriceResult {
        val normalized = normalizeDimensionId(dimensionId)
        val multiplier = getDimensionMultiplier(normalized)
        return FixedPriceResult(
            baseCost = baseCost,
            totalCost = baseCost * multiplier,
            dimensionId = normalized,
            dimensionMultiplier = multiplier
        )
    }

    fun calculateLandCostTotal(area: Double, isManor: Boolean): Long {
        return calculateLandCostTotal(buildAreaMap(DIMENSION_OVERWORLD, area), isManor)
    }

    fun calculateLandCostTotal(areaByDimension: Map<String, Double>, isManor: Boolean): Long {
        return calculateLandCostBreakdown(areaByDimension, isManor).sumOf { it.subtotal }
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
        return calculateCreationCost(buildAreaMap(DIMENSION_OVERWORLD, area), area, isManor)
    }

    fun calculateCreationCost(region: Region, isManor: Boolean): CreationCostResult {
        return calculateCreationCost(getRegionAreaByDimension(region), region.calculateTotalArea(), isManor)
    }

    fun calculateCreationCost(areaByDimension: Map<String, Double>, totalArea: Double, isManor: Boolean): CreationCostResult {
        val orderedAreaByDimension = orderAreaMap(areaByDimension)
        val baseCost = if (isManor) PricingConfig.PRICE_MANOR.value else PricingConfig.PRICE_REALM.value
        val dimensionCosts = calculateLandCostBreakdown(orderedAreaByDimension, isManor)
        val areaCost = dimensionCosts.sumOf { it.subtotal }
        return CreationCostResult(
            baseCost = baseCost,
            areaCost = areaCost,
            totalCost = baseCost + areaCost,
            area = roundArea(totalArea),
            dimensionCosts = dimensionCosts,
            areaByDimension = orderedAreaByDimension
        )
    }

    fun calculateModificationCost(areaChange: Double, currentTotalArea: Double, isManor: Boolean): ModificationCostResult {
        return calculateModificationCost(
            buildAreaMap(DIMENSION_OVERWORLD, currentTotalArea),
            buildAreaMap(DIMENSION_OVERWORLD, currentTotalArea + areaChange),
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
                    val adjustedCost = cost * dimensionMultiplier
                    grossCost += adjustedCost
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
                    val adjustedCost = cost * dimensionMultiplier
                    grossCost += adjustedCost
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
                        subtotal = -((grossCost) * config.refundRate).toLong(),
                        brackets = brackets
                    )
                )
            }
        }

        return ModificationCostResult(
            areaChange = roundArea(sumArea(orderedAreaAfterByDimension) - sumArea(orderedAreaBeforeByDimension)),
            areaBefore = roundArea(sumArea(orderedAreaBeforeByDimension)),
            areaAfter = roundArea(sumArea(orderedAreaAfterByDimension)),
            cost = dimensionCosts.sumOf { it.subtotal },
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
            -(result.cost * PricingConfig.AREA_REFUND_RATE.value).toLong()
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
            -(result.cost * PricingConfig.AREA_REFUND_RATE.value).toLong()
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
                val adjustedCost = cost * dimensionMultiplier
                grossCost += adjustedCost
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
                val adjustedCost = cost * dimensionMultiplier
                grossCost += adjustedCost
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
            cost = result.sumOf { it.subtotal } / denominator,
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
        return roundArea(areaByDimension.values.sum())
    }

    private fun roundArea(area: Double): Double {
        return String.format("%.2f", area).toDouble()
    }
}
