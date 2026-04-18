package com.imyvm.community.application.interaction.common.helper

import com.imyvm.community.domain.policy.territory.ModificationCostResult
import com.imyvm.community.domain.policy.territory.SettingItemCostChange
import com.imyvm.community.domain.policy.territory.TerritoryConfirmationMessage
import com.imyvm.community.domain.policy.territory.TerritoryPricing
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.network.chat.Component

fun calculateModificationCost(areaChange: Double, currentTotalArea: Double, isManor: Boolean): ModificationCostResult {
    return TerritoryPricing.calculateModificationCost(areaChange, currentTotalArea, isManor)
}

fun calculateModificationCost(
    areaBeforeByDimension: Map<String, Double>,
    areaAfterByDimension: Map<String, Double>,
    isManor: Boolean
): ModificationCostResult {
    return TerritoryPricing.calculateModificationCost(areaBeforeByDimension, areaAfterByDimension, isManor)
}

fun generateModificationConfirmationMessage(
    scopeName: String,
    costResult: ModificationCostResult,
    isManor: Boolean,
    currentAssets: Long,
    settingChanges: List<SettingItemCostChange> = emptyList()
): List<Component> {
    return TerritoryConfirmationMessage.generateModificationConfirmation(
        scopeName, costResult, isManor, currentAssets, settingChanges
    )
}


fun generateScopeDeletionConfirmationMessage(
    scopeName: String,
    scopeArea: Double,
    costResult: ModificationCostResult,
    isManor: Boolean,
    currentAssets: Long,
    settingChanges: List<SettingItemCostChange> = emptyList()
): List<Component> {
    return TerritoryConfirmationMessage.generateScopeDeletionConfirmation(
        scopeName, scopeArea, costResult, isManor, currentAssets, settingChanges
    )
}


fun generateScopeAdditionConfirmationMessage(
    scopeName: String,
    shapeType: GeoShapeType,
    area: Double,
    fixedCostBase: Long,
    landCostResult: ModificationCostResult,
    settingChanges: List<SettingItemCostChange>,
    isManor: Boolean,
    currentAssets: Long,
    currentTotalArea: Double,
    scopeDimensionId: String,
    rawTotal: Long = 0L,
    adjustedTotal: Long = 0L,
    excessCount: Int = 0,
    maxScopesAllowed: Int = 0,
    formalMemberCount: Int = 0,
    multiplier: Double = 1.5
): List<Component> {
    return TerritoryConfirmationMessage.generateScopeAdditionConfirmation(
        scopeName, shapeType, area, fixedCostBase, landCostResult, settingChanges, isManor, currentAssets,
        currentTotalArea, scopeDimensionId, rawTotal, adjustedTotal, excessCount, maxScopesAllowed, formalMemberCount, multiplier
    )
}
