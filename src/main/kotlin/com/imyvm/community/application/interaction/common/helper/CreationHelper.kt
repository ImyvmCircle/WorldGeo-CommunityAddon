package com.imyvm.community.application.interaction.common.helper

import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.text.Text

data class CreationCostResult(
    val baseCost: Long,
    val areaCost: Long,
    val totalCost: Long,
    val area: Double
)

fun calculateCreationCost(area: Double, isManor: Boolean): CreationCostResult {
    val baseCost = if (isManor) CommunityConfig.PRICE_MANOR.value else CommunityConfig.PRICE_REALM.value
    
    val areaCost: Long = if (isManor) {
        val freeArea = 10000.0
        val pricePerUnit = 1000L
        val unitSize = 10000.0
        
        if (area <= freeArea) {
            0L
        } else {
            ((area - freeArea) / unitSize * pricePerUnit).toLong()
        }
    } else {
        val freeArea = 40000.0
        val pricePerUnit = 3000L
        val unitSize = 40000.0
        
        if (area <= freeArea) {
            0L
        } else {
            ((area - freeArea) / unitSize * pricePerUnit).toLong()
        }
    }
    
    return CreationCostResult(
        baseCost = baseCost,
        areaCost = areaCost,
        totalCost = baseCost + areaCost,
        area = area
    )
}

fun generateCreationConfirmationMessage(
    communityName: String,
    geoShapeType: GeoShapeType,
    isManor: Boolean,
    costResult: CreationCostResult
): List<Text> {
    val messages = mutableListOf<Text>()

    messages.add(Translator.tr("community.create.confirm.header") ?: Text.literal("====== COMMUNITY CREATION CONFIRMATION ======"))

    messages.add(Translator.tr("community.create.confirm.name", communityName) ?: Text.literal("Name: $communityName"))

    val typeKey = if (isManor) "community.type.manor" else "community.type.realm"
    val typeText = Translator.tr(typeKey) ?: Text.literal(if (isManor) "Manor" else "Realm")
    messages.add(Translator.tr("community.create.confirm.type", typeText.string) ?: Text.literal("Type: ${typeText.string}"))

    val shapeKey = when (geoShapeType) {
        GeoShapeType.CIRCLE -> "community.shape.circle"
        GeoShapeType.RECTANGLE -> "community.shape.rectangle"
        GeoShapeType.POLYGON -> "community.shape.polygon"
        else -> "community.shape.unknown"
    }
    val shapeText = Translator.tr(shapeKey) ?: Text.literal(geoShapeType.toString())
    messages.add(Translator.tr("community.create.confirm.shape", shapeText.string) ?: Text.literal("Shape: ${shapeText.string}"))

    messages.add(Translator.tr("community.create.confirm.base_cost", (costResult.baseCost / 100.0).toString()) 
        ?: Text.literal("Base Cost: ${costResult.baseCost / 100.0}"))

    messages.add(Translator.tr("community.create.confirm.area", String.format("%.2f", costResult.area)) 
        ?: Text.literal("Area: ${String.format("%.2f", costResult.area)} m²"))

    if (costResult.areaCost > 0) {
        messages.add(Translator.tr("community.create.confirm.area_cost", (costResult.areaCost / 100.0).toString()) 
            ?: Text.literal("Area Fee: ${costResult.areaCost / 100.0}"))

        val freeArea = if (isManor) 10000.0 else 40000.0
        val unitSize = if (isManor) 10000.0 else 40000.0
        val pricePerUnit = if (isManor) 1000L else 3000L
        val calculationResult = (costResult.area - freeArea) / unitSize * pricePerUnit / 100.0
        
        messages.add(Translator.tr(
            "community.create.confirm.area_cost_calculation",
            String.format("%.2f", costResult.area),
            freeArea.toInt().toString(),
            unitSize.toInt().toString(),
            (pricePerUnit / 100.0).toString(),
            String.format("%.2f", calculationResult)
        ) ?: Text.literal("Calculation: (${costResult.area} - $freeArea) / $unitSize * ${pricePerUnit / 100.0} = $calculationResult"))

        val explanationKey = if (isManor) "community.create.area_fee.manor.explanation" else "community.create.area_fee.realm.explanation"
        messages.add(Translator.tr(explanationKey) ?: Text.literal(""))
    } else {
        messages.add(Translator.tr("community.create.confirm.area_cost", "0 (within free area)") 
            ?: Text.literal("Area Fee: 0 (within free area)"))
    }

    messages.add(Translator.tr("community.create.confirm.total_cost", (costResult.totalCost / 100.0).toString()) 
        ?: Text.literal("TOTAL COST: ${costResult.totalCost / 100.0}"))

    messages.add(Translator.tr("community.create.confirm.refund_note") ?: Text.literal("Note: Cost will be refunded if creation is rejected."))
    messages.add(Translator.tr("community.create.confirm.region_note") ?: Text.literal("Region will be occupied. You can modify it after approval."))
    
    return messages
}
