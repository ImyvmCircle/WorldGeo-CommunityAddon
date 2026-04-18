package com.imyvm.community.domain.policy.territory

import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getColoredDimensionName
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.network.chat.Component
import kotlin.math.abs

object TerritoryConfirmationMessage {

    fun generateCreationConfirmation(
        communityName: String,
        geoShapeType: GeoShapeType,
        isManor: Boolean,
        costResult: CreationCostResult
    ): List<Component> {
        val messages = mutableListOf<Component>()

        messages.add(Translator.tr("community.create.confirm.header") ?: Component.literal("====== COMMUNITY CREATION CONFIRMATION ======"))
        messages.add(Translator.tr("community.create.confirm.name", communityName) ?: Component.literal("Name: $communityName"))

        val typeKey = if (isManor) "community.type.manor" else "community.type.realm"
        val typeText = Translator.tr(typeKey) ?: Component.literal(if (isManor) "Manor" else "Realm")
        messages.add(Translator.tr("community.create.confirm.type", typeText.string) ?: Component.literal("Type: ${typeText.string}"))

        val shapeKey = when (geoShapeType) {
            GeoShapeType.CIRCLE -> "community.shape.circle"
            GeoShapeType.RECTANGLE -> "community.shape.rectangle"
            GeoShapeType.POLYGON -> "community.shape.polygon"
            else -> "community.shape.unknown"
        }
        val shapeText = Translator.tr(shapeKey) ?: Component.literal(geoShapeType.toString())
        messages.add(Translator.tr("community.create.confirm.shape", shapeText.string) ?: Component.literal("Shape: ${shapeText.string}"))
        val creationDimensionId = TerritoryPricing.orderedDimensionIds(costResult.areaByDimension.keys).firstOrNull()
            ?: TerritoryPricing.DIMENSION_OVERWORLD
        messages.add(
            Translator.tr("community.create.confirm.dimension", getColoredDimensionName(creationDimensionId))
                ?: Component.literal("Dimension: ${getColoredDimensionName(creationDimensionId)}")
        )

        messages.add(
            Translator.tr("community.create.confirm.base_cost", String.format("%.2f", costResult.baseCost / 100.0))
                ?: Component.literal("Base Cost: ${String.format("%.2f", costResult.baseCost / 100.0)}")
        )
        messages.add(
            Translator.tr("community.create.confirm.area", String.format("%.2f", costResult.area))
                ?: Component.literal("Area: ${String.format("%.2f", costResult.area)} m²")
        )
        appendAreaSnapshot(messages, "community.pricing.area.breakdown.header", costResult.areaByDimension)
        addDimensionLegend(messages, costResult.dimensionCosts.map { it.dimensionId })

        if (costResult.areaCost > 0) {
            messages.add(Translator.tr("community.create.confirm.land_brackets") ?: Component.literal("Land Fee (tiered):"))
            val config = TerritoryPricing.getPricingConfig(isManor)
            appendDimensionBreakdowns(messages, costResult.dimensionCosts, config.pricePerUnit.toDouble() / config.unitSize)
        } else {
            messages.add(Translator.tr("community.create.confirm.area_free") ?: Component.literal("Land fee: Free (within free area)"))
        }

        messages.add(
            Translator.tr("community.create.confirm.total_cost", String.format("%.2f", costResult.totalCost / 100.0))
                ?: Component.literal("TOTAL COST: ${String.format("%.2f", costResult.totalCost / 100.0)}")
        )
        messages.add(Translator.tr("community.create.confirm.refund_note") ?: Component.literal("Note: Cost will be refunded if creation is rejected."))
        messages.add(Translator.tr("community.create.confirm.region_note") ?: Component.literal("Region will be occupied. You can modify it after approval."))

        return messages
    }

    fun generateModificationConfirmation(
        regionName: String,
        scopeName: String,
        scopeAreaBefore: Double,
        scopeAreaAfter: Double,
        costResult: ModificationCostResult,
        isManor: Boolean,
        currentAssets: Long,
        settingChanges: List<SettingItemCostChange> = emptyList()
    ): List<Component> {
        val messages = mutableListOf<Component>()
        val config = TerritoryPricing.getPricingConfig(isManor)

        messages.add(
            Translator.tr("community.modification.confirm.header")
                ?: Component.literal("====== TERRITORY MODIFICATION CONFIRMATION ======")
        )
        messages.add(Translator.tr("community.modification.confirm.region", regionName) ?: Component.literal("Region: $regionName"))
        messages.add(Translator.tr("community.modification.confirm.scope", scopeName) ?: Component.literal("Administrative District: $scopeName"))
        appendAreaSummary(
            messages = messages,
            headerKey = "community.pricing.scope_area.header",
            areaBefore = scopeAreaBefore,
            areaAfter = scopeAreaAfter
        )
        appendAreaSummary(
            messages = messages,
            headerKey = "community.pricing.region_total.header",
            areaBefore = costResult.areaBefore,
            areaAfter = costResult.areaAfter
        )
        appendChangedDimensions(messages, costResult.areaBeforeByDimension, costResult.areaAfterByDimension)
        addDimensionLegend(messages, costResult.dimensionCosts.map { it.dimensionId })

        if (costResult.isIncrease) {
            messages.add(
                Translator.tr("community.pricing.land.increase_header", String.format("%.2f", costResult.areaChange))
                    ?: Component.literal("Land Fee (+${String.format("%.2f", costResult.areaChange)} m²):")
            )
        } else {
            messages.add(
                Translator.tr("community.pricing.land.decrease_header", String.format("%.2f", abs(costResult.areaChange)))
                    ?: Component.literal("Land Refund (-${String.format("%.2f", abs(costResult.areaChange))} m²):")
            )
        }
        appendDimensionBreakdowns(messages, costResult.dimensionCosts, config.pricePerUnit.toDouble() / config.unitSize)
        if (costResult.dimensionCosts.isEmpty() && costResult.isIncrease) {
            messages.add(Translator.tr("community.create.confirm.area_free") ?: Component.literal("Land fee: Free (within free area)"))
        }

        var totalCost = costResult.cost
        if (settingChanges.isNotEmpty()) {
            messages.add(Translator.tr("community.modification.confirm.setting_changes_header") ?: Component.literal("Setting item cost changes:"))
            for (change in settingChanges) {
                totalCost += change.costChange
                val target = change.playerName ?: (Translator.tr("community.setting.confirmation.target.global.short")?.string ?: "global")
                val layer = change.scopeName ?: (Translator.tr("community.setting.confirmation.layer.region.short")?.string ?: "region")
                val sign = if (change.costChange >= 0) "+" else ""
                messages.add(
                    Translator.tr(
                        "community.modification.confirm.setting_change_line",
                        change.settingKeyName,
                        layer,
                        target,
                        "$sign${String.format("%.2f", change.costChange / 100.0)}"
                    ) ?: Component.literal("  ${change.settingKeyName} [$layer/$target]: $sign${String.format("%.2f", change.costChange / 100.0)}")
                )
                appendChangedDimensions(messages, change.areaOldByDimension, change.areaNewByDimension, nested = true)
            }
        }

        val assetsAfter = currentAssets - totalCost
        if (totalCost >= 0) {
            messages.add(
                Translator.tr("community.modification.confirm.cost", String.format("%.2f", totalCost / 100.0))
                    ?: Component.literal("Cost: ${String.format("%.2f", totalCost / 100.0)}")
            )
        } else {
            messages.add(
                Translator.tr("community.modification.confirm.refund", String.format("%.2f", -totalCost / 100.0))
                    ?: Component.literal("Refund: ${String.format("%.2f", -totalCost / 100.0)}")
            )
        }
        messages.add(
            Translator.tr("community.modification.confirm.assets", String.format("%.2f", currentAssets / 100.0), String.format("%.2f", assetsAfter / 100.0))
                ?: Component.literal("Community Assets: ${String.format("%.2f", currentAssets / 100.0)} -> ${String.format("%.2f", assetsAfter / 100.0)}")
        )
        if (totalCost > 0 && currentAssets < totalCost) {
            messages.add(Translator.tr("community.modification.confirm.insufficient_assets") ?: Component.literal("WARNING: Insufficient assets!"))
        }
        messages.add(Translator.tr("community.modification.confirm.prompt") ?: Component.literal("Please confirm to proceed with this modification."))

        return messages
    }

    fun generateScopeDeletionConfirmation(
        regionName: String,
        scopeName: String,
        scopeArea: Double,
        costResult: ModificationCostResult,
        isManor: Boolean,
        currentAssets: Long,
        settingChanges: List<SettingItemCostChange> = emptyList()
    ): List<Component> {
        val messages = mutableListOf<Component>()
        val config = TerritoryPricing.getPricingConfig(isManor)

        messages.add(Translator.tr("community.scope_delete.confirm.header") ?: Component.literal("====== SELL SCOPE CONFIRMATION ======"))
        messages.add(Translator.tr("community.scope_delete.confirm.region", regionName) ?: Component.literal("Region: $regionName"))
        messages.add(Translator.tr("community.scope_delete.confirm.scope", scopeName) ?: Component.literal("Administrative District: $scopeName"))
        appendAreaSummary(
            messages = messages,
            headerKey = "community.pricing.scope_area.header",
            areaBefore = scopeArea,
            areaAfter = 0.0
        )
        appendAreaSummary(
            messages = messages,
            headerKey = "community.pricing.region_total.header",
            areaBefore = costResult.areaBefore,
            areaAfter = costResult.areaAfter
        )
        appendChangedDimensions(messages, costResult.areaBeforeByDimension, costResult.areaAfterByDimension)
        addDimensionLegend(messages, costResult.dimensionCosts.map { it.dimensionId })

        messages.add(
            Translator.tr("community.pricing.land.decrease_header", String.format("%.2f", scopeArea))
                ?: Component.literal("Land Refund (-${String.format("%.2f", scopeArea)} m²):")
        )
        appendDimensionBreakdowns(messages, costResult.dimensionCosts, config.pricePerUnit.toDouble() / config.unitSize)

        var totalCost = costResult.cost
        if (settingChanges.isNotEmpty()) {
            messages.add(Translator.tr("community.modification.confirm.setting_changes_header") ?: Component.literal("Setting item cost changes:"))
            for (change in settingChanges) {
                totalCost += change.costChange
                val target = change.playerName ?: (Translator.tr("community.setting.confirmation.target.global.short")?.string ?: "global")
                val layer = change.scopeName ?: (Translator.tr("community.setting.confirmation.layer.region.short")?.string ?: "region")
                val sign = if (change.costChange >= 0) "+" else ""
                messages.add(
                    Translator.tr(
                        "community.modification.confirm.setting_change_line",
                        change.settingKeyName,
                        layer,
                        target,
                        "$sign${String.format("%.2f", change.costChange / 100.0)}"
                    ) ?: Component.literal("  ${change.settingKeyName} [$layer/$target]: $sign${String.format("%.2f", change.costChange / 100.0)}")
                )
                appendChangedDimensions(messages, change.areaOldByDimension, change.areaNewByDimension, nested = true)
            }
        }

        val assetsAfter = currentAssets - totalCost
        messages.add(
            Translator.tr("community.scope_delete.confirm.refund", String.format("%.2f", -totalCost / 100.0))
                ?: Component.literal("Total Refund: ${String.format("%.2f", -totalCost / 100.0)}")
        )
        messages.add(
            Translator.tr("community.modification.confirm.assets", String.format("%.2f", currentAssets / 100.0), String.format("%.2f", assetsAfter / 100.0))
                ?: Component.literal("Community Assets: ${String.format("%.2f", currentAssets / 100.0)} -> ${String.format("%.2f", assetsAfter / 100.0)}")
        )
        messages.add(Translator.tr("community.scope_delete.confirm.prompt") ?: Component.literal("Please confirm to sell this scope to the system."))

        return messages
    }

    fun generateScopeAdditionConfirmation(
        regionName: String,
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
        val messages = mutableListOf<Component>()

        val shapeText = when (shapeType) {
            GeoShapeType.CIRCLE -> Translator.tr("community.shape.circle")?.string ?: "circle"
            GeoShapeType.RECTANGLE -> Translator.tr("community.shape.rectangle")?.string ?: "rectangle"
            GeoShapeType.POLYGON -> Translator.tr("community.shape.polygon")?.string ?: "polygon"
            else -> Translator.tr("community.shape.unknown")?.string ?: "unknown"
        }
        val totalCost = if (excessCount > 0) adjustedTotal else rawTotal

        messages.add(Translator.tr("community.scope_add.confirm.header") ?: Component.literal("====== SCOPE CREATION CONFIRMATION ======"))
        messages.add(Translator.tr("community.scope_add.confirm.region", regionName) ?: Component.literal("Region: $regionName"))
        messages.add(Translator.tr("community.scope_add.confirm.scope", scopeName) ?: Component.literal("Administrative District: $scopeName"))
        messages.add(Translator.tr("community.scope_add.confirm.shape", shapeText) ?: Component.literal("Shape: $shapeText"))
        messages.add(
            Translator.tr("community.scope_add.confirm.dimension", getColoredDimensionName(scopeDimensionId))
                ?: Component.literal("Dimension: ${getColoredDimensionName(scopeDimensionId)}")
        )
        appendAreaSummary(
            messages = messages,
            headerKey = "community.pricing.new_scope_area.header",
            areaBefore = 0.0,
            areaAfter = area
        )
        appendAreaSummary(
            messages = messages,
            headerKey = "community.pricing.region_total.header",
            areaBefore = landCostResult.areaBefore,
            areaAfter = landCostResult.areaAfter
        )
        appendChangedDimensions(messages, landCostResult.areaBeforeByDimension, landCostResult.areaAfterByDimension)
        messages.add(Translator.tr("community.scope_add.confirm.base_cost", String.format("%.2f", fixedCostBase / 100.0)) ?: Component.literal("Base Cost: ${String.format("%.2f", fixedCostBase / 100.0)}"))
        addDimensionLegend(messages, landCostResult.dimensionCosts.map { it.dimensionId } + scopeDimensionId)

        if (landCostResult.cost > 0) {
            messages.add(
                Translator.tr("community.pricing.land.increase_header", String.format("%.2f", area))
                    ?: Component.literal("Land Fee (+${String.format("%.2f", area)} m²):")
            )
            val config = TerritoryPricing.getPricingConfig(isManor)
            appendDimensionBreakdowns(messages, landCostResult.dimensionCosts, config.pricePerUnit.toDouble() / config.unitSize)
        } else {
            messages.add(Translator.tr("community.scope_add.confirm.land_free") ?: Component.literal("Land fee: Free (within free area)"))
        }

        if (settingChanges.isNotEmpty()) {
            messages.add(Translator.tr("community.scope_add.confirm.setting_changes_header") ?: Component.literal("Affected setting items:"))
            for (change in settingChanges) {
                val target = change.playerName ?: (Translator.tr("community.setting.confirmation.target.global.short")?.string ?: "global")
                val sign = if (change.costChange >= 0) "+" else ""
                messages.add(
                    Translator.tr(
                        "community.scope_add.confirm.setting_change_line",
                        change.settingKeyName,
                        target,
                        "$sign${String.format("%.2f", change.costChange / 100.0)}"
                    ) ?: Component.literal("  ${change.settingKeyName} [$target]: $sign${String.format("%.2f", change.costChange / 100.0)}")
                )
                appendChangedDimensions(messages, change.areaOldByDimension, change.areaNewByDimension, nested = true)
            }
        }

        if (excessCount > 0) {
            val multiplierStr = String.format("%.1f", multiplier)
            messages.add(
                Translator.tr(
                    "community.scope_add.confirm.surcharge_formula",
                    String.format("%.2f", rawTotal / 100.0),
                    multiplierStr,
                    excessCount.toString(),
                    String.format("%.2f", adjustedTotal / 100.0),
                    maxScopesAllowed.toString(),
                    formalMemberCount.toString()
                ) ?: Component.literal("Soft Limit Surcharge (limit $maxScopesAllowed/$formalMemberCount, $excessCount excess): ${String.format("%.2f", rawTotal / 100.0)} × ${multiplierStr}^$excessCount = ${String.format("%.2f", adjustedTotal / 100.0)}")
            )
        }

        messages.add(Translator.tr("community.scope_add.confirm.total_cost", String.format("%.2f", totalCost / 100.0)) ?: Component.literal("TOTAL COST: ${String.format("%.2f", totalCost / 100.0)}"))
        messages.add(
            Translator.tr("community.scope_add.confirm.assets", String.format("%.2f", currentAssets / 100.0), String.format("%.2f", (currentAssets - totalCost) / 100.0))
                ?: Component.literal("Community Assets: ${String.format("%.2f", currentAssets / 100.0)} -> ${String.format("%.2f", (currentAssets - totalCost) / 100.0)}")
        )
        if (currentAssets < totalCost) {
            messages.add(Translator.tr("community.scope_add.confirm.insufficient_assets") ?: Component.literal("Insufficient assets"))
        }
        messages.add(Translator.tr("community.scope_add.confirm.prompt") ?: Component.literal("Please confirm to proceed with scope creation."))
        return messages
    }

    private fun addDimensionLegend(messages: MutableList<Component>, dimensionIds: Collection<String>) {
        if (dimensionIds.isEmpty()) return
        val orderedIds = TerritoryPricing.orderedDimensionIds(dimensionIds)
        val parts = orderedIds.map { dimensionId ->
            val key = when (dimensionId) {
                TerritoryPricing.DIMENSION_NETHER -> "community.pricing.dimension.multiplier.nether"
                TerritoryPricing.DIMENSION_END -> "community.pricing.dimension.multiplier.end"
                else -> "community.pricing.dimension.multiplier.overworld"
            }
            Translator.tr(key, TerritoryPricing.getDimensionMultiplier(dimensionId).toString())?.string ?: "$dimensionId x${TerritoryPricing.getDimensionMultiplier(dimensionId)}"
        }
        messages.add(
            Translator.tr("community.pricing.dimension.legend", parts.joinToString("§7, "))
                ?: Component.literal("§7Dimension multipliers: ${parts.joinToString(", ")}")
        )
    }

    private fun appendAreaSummary(
        messages: MutableList<Component>,
        headerKey: String,
        areaBefore: Double,
        areaAfter: Double
    ) {
        messages.add(Translator.tr(headerKey) ?: Component.literal("Area summary:"))
        messages.add(
            Translator.tr("community.pricing.area.summary.before", String.format("%.2f", areaBefore))
                ?: Component.literal("  Before ${String.format("%.2f", areaBefore)} m²")
        )
        messages.add(
            Translator.tr("community.pricing.area.summary.after", String.format("%.2f", areaAfter))
                ?: Component.literal("  After ${String.format("%.2f", areaAfter)} m²")
        )
        messages.add(
            Translator.tr("community.pricing.area.summary.change", formatSignedArea(areaAfter - areaBefore))
                ?: Component.literal("  Change ${formatSignedArea(areaAfter - areaBefore)} m²")
        )
    }

    private fun appendAreaSnapshot(
        messages: MutableList<Component>,
        headerKey: String,
        areaByDimension: Map<String, Double>
    ) {
        if (areaByDimension.isEmpty()) return
        messages.add(
            Translator.tr(headerKey)
                ?: Component.literal("Area by dimension:")
        )
        for (dimensionId in TerritoryPricing.orderedDimensionIds(areaByDimension.keys)) {
            val area = areaByDimension[dimensionId] ?: continue
            val dimensionName = Translator.tr(TerritoryPricing.getDimensionDisplayKey(dimensionId))?.string ?: dimensionId
            messages.add(
                Translator.tr(
                    "community.pricing.area.line",
                    dimensionName,
                    String.format("%.2f", area)
                ) ?: Component.literal("  $dimensionName: ${String.format("%.2f", area)} m²")
            )
        }
    }

    private fun appendChangedDimensions(
        messages: MutableList<Component>,
        areaBeforeByDimension: Map<String, Double>,
        areaAfterByDimension: Map<String, Double>,
        nested: Boolean = false
    ) {
        val changedDimensionIds = TerritoryPricing.orderedDimensionIds(areaBeforeByDimension.keys + areaAfterByDimension.keys)
            .filter { String.format("%.2f", areaBeforeByDimension[it] ?: 0.0) != String.format("%.2f", areaAfterByDimension[it] ?: 0.0) }
        if (changedDimensionIds.isEmpty()) return
        messages.add(Translator.tr(if (nested) "community.pricing.area.nested.changed.header" else "community.pricing.area.changed.header")
            ?: Component.literal("Changed dimensions:"))
        for (dimensionId in changedDimensionIds) {
            val areaBefore = areaBeforeByDimension[dimensionId] ?: 0.0
            val areaAfter = areaAfterByDimension[dimensionId] ?: 0.0
            val dimensionName = Translator.tr(TerritoryPricing.getDimensionDisplayKey(dimensionId))?.string ?: dimensionId
            messages.add(
                Translator.tr(
                    if (nested) "community.pricing.area.nested.changed.dimension" else "community.pricing.area.changed.dimension",
                    dimensionName
                ) ?: Component.literal("  $dimensionName")
            )
            messages.add(
                Translator.tr(
                    if (nested) "community.pricing.area.nested.before" else "community.pricing.area.before",
                    String.format("%.2f", areaBefore),
                ) ?: Component.literal("    Before ${String.format("%.2f", areaBefore)} m²")
            )
            messages.add(
                Translator.tr(
                    if (nested) "community.pricing.area.nested.after" else "community.pricing.area.after",
                    String.format("%.2f", areaAfter),
                ) ?: Component.literal("    After ${String.format("%.2f", areaAfter)} m²")
            )
            messages.add(
                Translator.tr(
                    if (nested) "community.pricing.area.nested.change" else "community.pricing.area.change",
                    formatSignedArea(areaAfter - areaBefore)
                ) ?: Component.literal("    Change ${formatSignedArea(areaAfter - areaBefore)} m²")
            )
        }
    }

    private fun formatSignedArea(area: Double): String {
        val sign = if (area >= 0.0) "+" else ""
        return "$sign${String.format("%.2f", area)}"
    }

    private fun appendDimensionBreakdowns(
        messages: MutableList<Component>,
        dimensionCosts: List<DimensionCostBreakdown>,
        baseUnitPrice: Double
    ) {
        for (dimensionCost in dimensionCosts) {
            val dimensionName = Translator.tr(TerritoryPricing.getDimensionDisplayKey(dimensionCost.dimensionId))?.string ?: dimensionCost.dimensionId
            val displayArea = if (dimensionCost.areaBefore == 0.0) {
                dimensionCost.areaAfter
            } else {
                abs(dimensionCost.areaAfter - dimensionCost.areaBefore)
            }
            messages.add(
                Translator.tr(
                    "community.pricing.dimension.header",
                    dimensionName,
                    String.format("%.2f", displayArea),
                    dimensionCost.dimensionMultiplier.toString(),
                    String.format("%.2f", dimensionCost.grossCost / 100.0)
                ) ?: Component.literal("§7  $dimensionName: ${String.format("%.2f", displayArea)} m² ×${dimensionCost.dimensionMultiplier} = ${String.format("%.2f", dimensionCost.grossCost / 100.0)}")
            )
            for (bracket in dimensionCost.brackets) {
                val unitPrice = baseUnitPrice * bracket.bracketMultiplier.toDouble() * dimensionCost.dimensionMultiplier.toDouble() / 100.0
                messages.add(
                    Translator.tr(
                        "community.pricing.bracket_line",
                        bracket.tierNum.toString(),
                        String.format("%.2f", bracket.bracketLow),
                        String.format("%.2f", bracket.bracketHigh),
                        String.format("%.2f", bracket.areaInBracket),
                        String.format("%.3f", unitPrice),
                        String.format("%.2f", bracket.cost / 100.0)
                    ) ?: Component.literal("  Tier ${bracket.tierNum} (${String.format("%.2f", bracket.bracketLow)} ~ ${String.format("%.2f", bracket.bracketHigh)} m²): ${String.format("%.2f", bracket.areaInBracket)} m² ×${String.format("%.3f", unitPrice)}/m² = ${String.format("%.2f", bracket.cost / 100.0)}")
                )
            }
            if (dimensionCost.subtotal < 0) {
                val refundPct = (PricingConfig.AREA_REFUND_RATE.value * 100).toInt()
                messages.add(
                    Translator.tr("community.pricing.refund_summary", refundPct.toString(), String.format("%.2f", -dimensionCost.subtotal / 100.0))
                        ?: Component.literal("  × $refundPct% refund = ${String.format("%.2f", -dimensionCost.subtotal / 100.0)}")
                )
            }
        }
    }
}
