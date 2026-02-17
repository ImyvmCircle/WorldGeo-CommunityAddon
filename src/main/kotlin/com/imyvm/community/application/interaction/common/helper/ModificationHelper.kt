package com.imyvm.community.application.interaction.common.helper

import com.imyvm.community.domain.territory.cost.ModificationCostResult
import com.imyvm.community.domain.territory.cost.TerritoryConfirmationMessage
import com.imyvm.community.domain.territory.cost.TerritoryPricing
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
