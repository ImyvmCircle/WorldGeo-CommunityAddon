package com.imyvm.community.domain.policy.territory

import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.text.Text

object TerritoryConfirmationMessage {
    
    fun generateCreationConfirmation(
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

            val config = TerritoryPricing.getPricingConfig(isManor)
            val calculationResult = (costResult.area - config.freeArea) / config.unitSize * config.pricePerUnit / 100.0
            
            messages.add(Translator.tr(
                "community.create.confirm.area_cost_calculation",
                String.format("%.2f", costResult.area),
                config.freeArea.toInt().toString(),
                config.unitSize.toInt().toString(),
                (config.pricePerUnit / 100.0).toString(),
                String.format("%.2f", calculationResult)
            ) ?: Text.literal("Calculation: (${costResult.area} - ${config.freeArea}) / ${config.unitSize} * ${config.pricePerUnit / 100.0} = $calculationResult"))

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
    
    fun generateModificationConfirmation(
        scopeName: String,
        costResult: ModificationCostResult,
        isManor: Boolean,
        currentAssets: Long
    ): List<Text> {
        val messages = mutableListOf<Text>()
        
        val territoryType = if (isManor) {
            Translator.tr("community.region.territory.manor")?.string ?: "manor territory"
        } else {
            Translator.tr("community.region.territory.realm")?.string ?: "realm territory"
        }
        
        messages.add(Translator.tr("community.modification.confirm.header") 
            ?: Text.literal("====== TERRITORY MODIFICATION CONFIRMATION ======"))
        
        messages.add(Translator.tr("community.modification.confirm.scope", scopeName) 
            ?: Text.literal("Administrative District: $scopeName"))
        
        messages.add(Translator.tr("community.modification.confirm.area_before", String.format("%.2f", costResult.areaBefore)) 
            ?: Text.literal("Area Before: ${String.format("%.2f", costResult.areaBefore)} m²"))
        
        messages.add(Translator.tr("community.modification.confirm.area_after", String.format("%.2f", costResult.areaAfter)) 
            ?: Text.literal("Area After: ${String.format("%.2f", costResult.areaAfter)} m²"))
        
        val changeSign = if (costResult.isIncrease) "+" else ""
        messages.add(Translator.tr("community.modification.confirm.area_change", "$changeSign${String.format("%.2f", costResult.areaChange)}") 
            ?: Text.literal("Area Change: $changeSign${String.format("%.2f", costResult.areaChange)} m²"))
        
        val config = TerritoryPricing.getPricingConfig(isManor)
        
        if (costResult.isIncrease) {
            val costDisplay = String.format("%.2f", costResult.cost / 100.0)
            messages.add(Translator.tr("community.modification.confirm.cost", costDisplay) 
                ?: Text.literal("Cost: $costDisplay"))
            
            messages.add(Translator.tr("community.modification.confirm.cost_calculation",
                String.format("%.2f", costResult.areaChange),
                config.unitSize.toInt().toString(),
                (config.pricePerUnit / 100.0).toString(),
                costDisplay
            ) ?: Text.literal("Calculation: ${String.format("%.2f", costResult.areaChange)} / ${config.unitSize} * ${config.pricePerUnit / 100.0} = $costDisplay"))
            
            messages.add(Translator.tr("community.modification.confirm.cost_explanation.increase") 
                ?: Text.literal("Note: Area increase is charged at full price."))
            
            val assetsAfter = currentAssets - costResult.cost
            val assetsDisplay = String.format("%.2f", currentAssets / 100.0)
            val assetsAfterDisplay = String.format("%.2f", assetsAfter / 100.0)
            messages.add(Translator.tr("community.modification.confirm.assets", assetsDisplay, assetsAfterDisplay) 
                ?: Text.literal("Community Assets: $assetsDisplay -> $assetsAfterDisplay"))
            
            if (assetsAfter < 0) {
                messages.add(Translator.tr("community.modification.confirm.insufficient_assets") 
                    ?: Text.literal("§c§lWARNING: Insufficient assets!§r"))
            }
        } else {
            val refundDisplay = String.format("%.2f", -costResult.cost / 100.0)
            messages.add(Translator.tr("community.modification.confirm.refund", refundDisplay) 
                ?: Text.literal("Refund: $refundDisplay"))
            
            val areaDecreased = -costResult.areaChange
            val areaAfterDecrease = costResult.areaBefore - areaDecreased
            
            if (areaAfterDecrease >= config.freeArea) {
                messages.add(Translator.tr("community.modification.confirm.refund_calculation",
                    String.format("%.2f", areaDecreased),
                    config.unitSize.toInt().toString(),
                    (config.pricePerUnit / 100.0).toString(),
                    config.refundRate.toString(),
                    refundDisplay
                ) ?: Text.literal("Calculation: ${String.format("%.2f", areaDecreased)} / ${config.unitSize} * ${config.pricePerUnit / 100.0} * ${config.refundRate} = $refundDisplay"))
                
                val refundRatePercent = (config.refundRate * 100).toInt().toString()
                messages.add(Translator.tr("community.modification.confirm.refund_explanation.full", refundRatePercent) 
                    ?: Text.literal("Note: Refund at ${(config.refundRate * 100).toInt()}% of original price."))
            } else {
                val refundableArea = if (costResult.areaBefore > config.freeArea) {
                    costResult.areaBefore - config.freeArea
                } else {
                    0.0
                }
                
                if (refundableArea > 0) {
                    messages.add(Translator.tr("community.modification.confirm.refund_calculation.partial",
                        String.format("%.2f", refundableArea),
                        config.freeArea.toInt().toString(),
                        String.format("%.2f", refundableArea),
                        config.unitSize.toInt().toString(),
                        (config.pricePerUnit / 100.0).toString(),
                        config.refundRate.toString(),
                        refundDisplay
                    ) ?: Text.literal("Calculation: Only (${String.format("%.2f", refundableArea)}) above free area (${config.freeArea}) refunded: ${String.format("%.2f", refundableArea)} / ${config.unitSize} * ${config.pricePerUnit / 100.0} * ${config.refundRate} = $refundDisplay"))
                    
                    val refundRatePercent = (config.refundRate * 100).toInt().toString()
                    messages.add(Translator.tr("community.modification.confirm.refund_explanation.partial", 
                        config.freeArea.toInt().toString(),
                        refundRatePercent
                    ) ?: Text.literal("Note: Only area above ${config.freeArea} m² is refunded at ${(config.refundRate * 100).toInt()}% rate."))
                } else {
                    messages.add(Translator.tr("community.modification.confirm.refund_explanation.none") 
                        ?: Text.literal("Note: No refund as remaining area is within free area limit."))
                }
            }
            
            val assetsAfter = currentAssets - costResult.cost
            val assetsDisplay = String.format("%.2f", currentAssets / 100.0)
            val assetsAfterDisplay = String.format("%.2f", assetsAfter / 100.0)
            messages.add(Translator.tr("community.modification.confirm.assets", assetsDisplay, assetsAfterDisplay) 
                ?: Text.literal("Community Assets: $assetsDisplay -> $assetsAfterDisplay"))
        }
        
        messages.add(Translator.tr("community.modification.confirm.prompt") 
            ?: Text.literal("Please confirm to proceed with this modification."))
        
        return messages
    }
}
