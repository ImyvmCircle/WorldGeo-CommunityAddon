package com.imyvm.community.application.interaction.common.helper

import com.imyvm.community.domain.policy.territory.ModificationCostResult
import com.imyvm.community.domain.policy.territory.SettingItemCostChange
import com.imyvm.community.domain.policy.territory.TerritoryConfirmationMessage
import com.imyvm.community.domain.policy.territory.TerritoryPricing
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.text.Text

fun calculateModificationCost(areaChange: Double, currentTotalArea: Double, isManor: Boolean): ModificationCostResult {
    return TerritoryPricing.calculateModificationCost(areaChange, currentTotalArea, isManor)
}

fun generateModificationConfirmationMessage(
    scopeName: String,
    costResult: ModificationCostResult,
    isManor: Boolean,
    currentAssets: Long,
    settingChanges: List<SettingItemCostChange> = emptyList()
): List<Text> {
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
): List<Text> {
    return TerritoryConfirmationMessage.generateScopeDeletionConfirmation(
        scopeName, scopeArea, costResult, isManor, currentAssets, settingChanges
    )
}


fun generateScopeAdditionConfirmationMessage(
    scopeName: String,
    shapeType: GeoShapeType,
    area: Double,
    fixedCostBase: Long,
    landCostChange: Long,
    settingChanges: List<SettingItemCostChange>,
    isManor: Boolean,
    currentAssets: Long,
    currentTotalArea: Double,
    rawTotal: Long = 0L,
    adjustedTotal: Long = 0L,
    excessCount: Int = 0,
    maxScopesAllowed: Int = 0,
    formalMemberCount: Int = 0,
    multiplier: Double = 1.5
): List<Text> {
    return TerritoryConfirmationMessage.generateScopeAdditionConfirmation(
        scopeName, shapeType, area, fixedCostBase, landCostChange, settingChanges, isManor, currentAssets,
        currentTotalArea, rawTotal, adjustedTotal, excessCount, maxScopesAllowed, formalMemberCount, multiplier
    )
}
