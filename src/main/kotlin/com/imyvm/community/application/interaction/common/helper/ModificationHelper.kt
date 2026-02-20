package com.imyvm.community.application.interaction.common.helper

import com.imyvm.community.domain.policy.territory.ModificationCostResult
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
    currentAssets: Long
): List<Text> {
    return TerritoryConfirmationMessage.generateModificationConfirmation(
        scopeName, costResult, isManor, currentAssets
    )
}


fun generateScopeAdditionConfirmationMessage(
    scopeName: String,
    shapeType: GeoShapeType,
    area: Double,
    fixedCost: Long,
    areaCost: Long,
    totalCost: Long,
    isManor: Boolean,
    currentAssets: Long
): List<Text> {
    return TerritoryConfirmationMessage.generateScopeAdditionConfirmation(
        scopeName, shapeType, area, fixedCost, areaCost, totalCost, isManor, currentAssets
    )
}
