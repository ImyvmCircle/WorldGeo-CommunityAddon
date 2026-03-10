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

        messages.add(Translator.tr("community.create.confirm.base_cost", String.format("%.2f", costResult.baseCost / 100.0))
            ?: Text.literal("Base Cost: ${String.format("%.2f", costResult.baseCost / 100.0)}"))
        messages.add(Translator.tr("community.create.confirm.area", String.format("%.2f", costResult.area))
            ?: Text.literal("Area: ${String.format("%.2f", costResult.area)} m²"))

        if (costResult.areaCost > 0) {
            messages.add(Translator.tr("community.create.confirm.land_brackets") ?: Text.literal("Land Fee (tiered):"))
            TerritoryPricing.forEachLandBracket(0.0, costResult.area, isManor) { tierNum, low, high, areaIn, mult, cost ->
                messages.add(Translator.tr("community.pricing.bracket_line",
                    tierNum.toString(), String.format("%.2f", low), String.format("%.2f", high),
                    String.format("%.2f", areaIn), mult.toString(), String.format("%.2f", cost / 100.0)
                ) ?: Text.literal("  Tier $tierNum (${String.format("%.2f", low)} ~ ${String.format("%.2f", high)} m²): ${String.format("%.2f", areaIn)} m² × ×$mult = ${String.format("%.2f", cost / 100.0)}"))
            }
        } else {
            messages.add(Translator.tr("community.create.confirm.area_free") ?: Text.literal("Land fee: Free (within free area)"))
        }

        messages.add(Translator.tr("community.create.confirm.total_cost", String.format("%.2f", costResult.totalCost / 100.0))
            ?: Text.literal("TOTAL COST: ${String.format("%.2f", costResult.totalCost / 100.0)}"))
        messages.add(Translator.tr("community.create.confirm.refund_note") ?: Text.literal("Note: Cost will be refunded if creation is rejected."))
        messages.add(Translator.tr("community.create.confirm.region_note") ?: Text.literal("Region will be occupied. You can modify it after approval."))
        
        return messages
    }
    
    fun generateModificationConfirmation(
        scopeName: String,
        costResult: ModificationCostResult,
        isManor: Boolean,
        currentAssets: Long,
        settingChanges: List<SettingItemCostChange> = emptyList()
    ): List<Text> {
        val messages = mutableListOf<Text>()
        val config = TerritoryPricing.getPricingConfig(isManor)
        
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

        val landCostAbs = Math.abs(costResult.cost)
        if (costResult.isIncrease) {
            messages.add(Translator.tr("community.pricing.land.increase_header", String.format("%.2f", costResult.areaChange))
                ?: Text.literal("Land Fee (+${String.format("%.2f", costResult.areaChange)} m²):"))
            TerritoryPricing.forEachLandBracket(costResult.areaBefore, costResult.areaAfter, isManor) { tierNum, low, high, areaIn, mult, cost ->
                messages.add(Translator.tr("community.pricing.bracket_line",
                    tierNum.toString(), String.format("%.2f", low), String.format("%.2f", high),
                    String.format("%.2f", areaIn), mult.toString(), String.format("%.2f", cost / 100.0)
                ) ?: Text.literal("  Tier $tierNum (${String.format("%.2f", low)} ~ ${String.format("%.2f", high)} m²): ${String.format("%.2f", areaIn)} m² × ×$mult = ${String.format("%.2f", cost / 100.0)}"))
            }
        } else {
            val refundRate = (config.refundRate * 100).toInt()
            messages.add(Translator.tr("community.pricing.land.decrease_header", String.format("%.2f", -costResult.areaChange))
                ?: Text.literal("Land Refund (-${String.format("%.2f", -costResult.areaChange)} m²):"))
            TerritoryPricing.forEachLandBracket(costResult.areaAfter, costResult.areaBefore, isManor) { tierNum, low, high, areaIn, mult, cost ->
                messages.add(Translator.tr("community.pricing.bracket_line",
                    tierNum.toString(), String.format("%.2f", low), String.format("%.2f", high),
                    String.format("%.2f", areaIn), mult.toString(), String.format("%.2f", cost / 100.0)
                ) ?: Text.literal("  Tier $tierNum (${String.format("%.2f", low)} ~ ${String.format("%.2f", high)} m²): ${String.format("%.2f", areaIn)} m² × ×$mult = ${String.format("%.2f", cost / 100.0)}"))
            }
            messages.add(Translator.tr("community.pricing.refund_summary", refundRate.toString(), String.format("%.2f", landCostAbs / 100.0))
                ?: Text.literal("  × $refundRate% refund = ${String.format("%.2f", landCostAbs / 100.0)}"))
        }

        var totalCost = costResult.cost
        if (settingChanges.isNotEmpty()) {
            messages.add(Translator.tr("community.modification.confirm.setting_changes_header")
                ?: Text.literal("Setting item cost changes:"))
            for (change in settingChanges) {
                totalCost += change.costChange
                val target = change.playerName ?: (Translator.tr("community.setting.confirmation.target.global.short")?.string ?: "global")
                val layer = change.scopeName ?: (Translator.tr("community.setting.confirmation.layer.region.short")?.string ?: "region")
                val sign = if (change.costChange >= 0) "+" else ""
                messages.add(Translator.tr(
                    "community.modification.confirm.setting_change_line",
                    change.settingKeyName,
                    layer,
                    target,
                    String.format("%.2f", change.areaOld),
                    String.format("%.2f", change.areaNew),
                    "$sign${String.format("%.2f", change.costChange / 100.0)}"
                ) ?: Text.literal("  ${change.settingKeyName} [$layer/$target]: ${String.format("%.2f", change.areaOld)}→${String.format("%.2f", change.areaNew)} m², $sign${String.format("%.2f", change.costChange / 100.0)}"))
            }
        }

        val assetsAfter = currentAssets - totalCost
        if (totalCost >= 0) {
            messages.add(Translator.tr("community.modification.confirm.cost", String.format("%.2f", totalCost / 100.0))
                ?: Text.literal("Cost: ${String.format("%.2f", totalCost / 100.0)}"))
        } else {
            messages.add(Translator.tr("community.modification.confirm.refund", String.format("%.2f", -totalCost / 100.0))
                ?: Text.literal("Refund: ${String.format("%.2f", -totalCost / 100.0)}"))
        }
        messages.add(Translator.tr("community.modification.confirm.assets", String.format("%.2f", currentAssets / 100.0), String.format("%.2f", assetsAfter / 100.0))
            ?: Text.literal("Community Assets: ${String.format("%.2f", currentAssets / 100.0)} -> ${String.format("%.2f", assetsAfter / 100.0)}"))
        if (totalCost > 0 && currentAssets < totalCost) {
            messages.add(Translator.tr("community.modification.confirm.insufficient_assets")
                ?: Text.literal("WARNING: Insufficient assets!"))
        }
        messages.add(Translator.tr("community.modification.confirm.prompt")
            ?: Text.literal("Please confirm to proceed with this modification."))
        
        return messages
    }

    fun generateScopeAdditionConfirmation(
        scopeName: String,
        shapeType: GeoShapeType,
        area: Double,
        fixedCost: Long,
        landCostChange: Long,
        settingChanges: List<SettingItemCostChange>,
        isManor: Boolean,
        currentAssets: Long,
        currentTotalArea: Double,
        excessCount: Int = 0,
        maxScopesAllowed: Int = 0,
        formalMemberCount: Int = 0,
        fixedCostBase: Long = 0L
    ): List<Text> {
        val messages = mutableListOf<Text>()

        val shapeText = when (shapeType) {
            GeoShapeType.CIRCLE -> Translator.tr("community.shape.circle")?.string ?: "circle"
            GeoShapeType.RECTANGLE -> Translator.tr("community.shape.rectangle")?.string ?: "rectangle"
            GeoShapeType.POLYGON -> Translator.tr("community.shape.polygon")?.string ?: "polygon"
            else -> Translator.tr("community.shape.unknown")?.string ?: "unknown"
        }
        var totalCost = fixedCost + landCostChange + settingChanges.sumOf { it.costChange }

        messages.add(Translator.tr("community.scope_add.confirm.header") ?: Text.literal("====== SCOPE CREATION CONFIRMATION ======"))
        messages.add(Translator.tr("community.scope_add.confirm.scope", scopeName) ?: Text.literal("Administrative District: $scopeName"))
        messages.add(Translator.tr("community.scope_add.confirm.shape", shapeText) ?: Text.literal("Shape: $shapeText"))
        messages.add(Translator.tr("community.scope_add.confirm.area", String.format("%.2f", area)) ?: Text.literal("Area: ${String.format("%.2f", area)} m²"))
        if (excessCount > 0) {
            messages.add(Translator.tr(
                "community.scope_add.confirm.base_cost_adjusted",
                excessCount.toString(),
                maxScopesAllowed.toString(),
                formalMemberCount.toString(),
                String.format("%.2f", fixedCost / 100.0),
                String.format("%.2f", fixedCostBase / 100.0)
            ) ?: Text.literal("Base Cost (Soft Limit Surcharge ×$excessCount, limit $maxScopesAllowed/$formalMemberCount members): ${String.format("%.2f", fixedCost / 100.0)} (original ${String.format("%.2f", fixedCostBase / 100.0)})"))
        } else {
            messages.add(Translator.tr("community.scope_add.confirm.base_cost", String.format("%.2f", fixedCost / 100.0)) ?: Text.literal("Base Cost: ${String.format("%.2f", fixedCost / 100.0)}"))
        }

        if (landCostChange > 0) {
            messages.add(Translator.tr("community.pricing.land.increase_header", String.format("%.2f", area))
                ?: Text.literal("Land Fee (+${String.format("%.2f", area)} m²):"))
            TerritoryPricing.forEachLandBracket(currentTotalArea, currentTotalArea + area, isManor) { tierNum, low, high, areaIn, mult, cost ->
                messages.add(Translator.tr("community.pricing.bracket_line",
                    tierNum.toString(), String.format("%.2f", low), String.format("%.2f", high),
                    String.format("%.2f", areaIn), mult.toString(), String.format("%.2f", cost / 100.0)
                ) ?: Text.literal("  Tier $tierNum (${String.format("%.2f", low)} ~ ${String.format("%.2f", high)} m²): ${String.format("%.2f", areaIn)} m² × ×$mult = ${String.format("%.2f", cost / 100.0)}"))
            }
        } else {
            messages.add(Translator.tr("community.scope_add.confirm.land_free") ?: Text.literal("Land fee: Free (within free area)"))
        }

        if (settingChanges.isNotEmpty()) {
            messages.add(Translator.tr("community.scope_add.confirm.setting_changes_header") ?: Text.literal("Affected setting items:"))
            for (change in settingChanges) {
                val target = change.playerName ?: (Translator.tr("community.setting.confirmation.target.global.short")?.string ?: "global")
                val sign = if (change.costChange >= 0) "+" else ""
                messages.add(Translator.tr(
                    "community.scope_add.confirm.setting_change_line",
                    change.settingKeyName,
                    target,
                    String.format("%.2f", change.areaOld),
                    String.format("%.2f", change.areaNew),
                    "$sign${String.format("%.2f", change.costChange / 100.0)}"
                ) ?: Text.literal("  ${change.settingKeyName} [$target]: ${String.format("%.2f", change.areaOld)}→${String.format("%.2f", change.areaNew)} m², $sign${String.format("%.2f", change.costChange / 100.0)}"))
            }
        }

        messages.add(Translator.tr("community.scope_add.confirm.total_cost", String.format("%.2f", totalCost / 100.0)) ?: Text.literal("TOTAL COST: ${String.format("%.2f", totalCost / 100.0)}"))
        messages.add(Translator.tr("community.scope_add.confirm.assets", String.format("%.2f", currentAssets / 100.0), String.format("%.2f", (currentAssets - totalCost) / 100.0)) ?: Text.literal("Community Assets: ${String.format("%.2f", currentAssets / 100.0)} -> ${String.format("%.2f", (currentAssets - totalCost) / 100.0)}"))
        if (currentAssets < totalCost) {
            messages.add(Translator.tr("community.scope_add.confirm.insufficient_assets") ?: Text.literal("Insufficient assets"))
        }
        messages.add(Translator.tr("community.scope_add.confirm.prompt") ?: Text.literal("Please confirm to proceed with scope creation."))
        return messages
    }

}
