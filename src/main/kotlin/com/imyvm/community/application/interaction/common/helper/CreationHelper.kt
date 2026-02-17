package com.imyvm.community.application.interaction.common.helper

import com.imyvm.community.domain.territory.cost.CreationCostResult
import com.imyvm.community.domain.territory.cost.TerritoryPricing
import com.imyvm.community.domain.territory.cost.TerritoryConfirmationMessage
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.text.Text

fun calculateCreationCost(area: Double, isManor: Boolean): CreationCostResult {
    return TerritoryPricing.calculateCreationCost(area, isManor)
}

fun generateCreationConfirmationMessage(
    communityName: String,
    geoShapeType: GeoShapeType,
    isManor: Boolean,
    costResult: CreationCostResult
): List<Text> {
    return TerritoryConfirmationMessage.generateCreationConfirmation(
        communityName, geoShapeType, isManor, costResult
    )
}
