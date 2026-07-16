package com.imyvm.community.entrypoint.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.helper.generateScopeCreationError
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runConfirmScopeCreationFromSelection
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runRenameNewScopeFromSelection
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runResetSelectionInScopeCreation
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runSwitchScopeShapeInCreation
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runToggleSelectionModeInScopeCreation
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class CommunityScopeCreationMenu(
    syncId: Int,
    val community: Community,
    val currentName: String = Translator.trStringOrFallback("ui.admin.region.global.add.default_name", "New-District"),
    val playerExecutor: ServerPlayer,
    val runBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = createMenuTitle(community, currentName, playerExecutor),
    runBack = runBack
) {
    init {
        val selectionState = ImyvmWorldGeo.pointSelectingPlayers[playerExecutor.uuid]
        val isSelectionModeEnabled = selectionState != null
        val hypotheticalShape = selectionState?.hypotheticalShape
        val isNormalSelectionMode = isSelectionModeEnabled && hypotheticalShape is HypotheticalShape.Normal
        val currentShape = if (hypotheticalShape is HypotheticalShape.Normal) hypotheticalShape.shapeType else GeoShapeType.RECTANGLE
        val pointCount = selectionState?.points?.size ?: 0
        val hasEnoughPoints = isNormalSelectionMode && pointCount >= 2

        if (isSelectionModeEnabled) {
            addButton(
                slot = 10,
                name = Translator.trStringOrFallback("ui.create.button.selection_mode.enable", "Selection Mode: On"),
                item = Items.COMMAND_BLOCK
            ) { runToggleSelectionModeInScopeCreation(it, community, currentName, runBack) }

            addButton(
                slot = 19,
                name = Translator.trStringOrFallback("ui.main.button.selection_mode.reset", "Reset Point Selection"),
                item = Items.TNT
            ) { runResetSelectionInScopeCreation(it, community, currentName, runBack) }

            if (isNormalSelectionMode) {
                addButton(
                    slot = 12,
                    name = (Translator.trStringOrFallback("ui.create.button.shape.prefix", "Current Shape(Click to change):"))
                            + when (currentShape) {
                                GeoShapeType.CIRCLE -> Translator.trStringOrFallback("community.shape.circle", "circle")
                                GeoShapeType.POLYGON -> Translator.trStringOrFallback("community.shape.polygon", "polygon")
                                else -> Translator.trStringOrFallback("community.shape.rectangle", "rectangle")
                            },
                    item = when (currentShape) {
                        GeoShapeType.CIRCLE -> Items.CLOCK
                        GeoShapeType.RECTANGLE -> Items.MAP
                        GeoShapeType.POLYGON -> Items.NETHER_STAR
                        GeoShapeType.UNKNOWN -> Items.STRUCTURE_BLOCK
                    }
                ) { runSwitchScopeShapeInCreation(it, community, currentName, runBack) }

                addButton(
                    slot = 14,
                    name = Translator.trStringOrFallback("ui.create.button.exit_to_select", "Exit Menu to Start Selecting"),
                    item = Items.ENDER_PEARL
                ) { p -> p.closeContainer() }
            }
        } else {
            addButton(
                slot = 10,
                name = Translator.trStringOrFallback("ui.create.button.selection_mode.disable", "Selection Mode: Off"),
                item = Items.REDSTONE_BLOCK
            ) { runToggleSelectionModeInScopeCreation(it, community, currentName, runBack) }
        }

        if (hasEnoughPoints) {
            addButton(
                slot = 28,
                name = currentName,
                item = Items.NAME_TAG
            ) { runRenameNewScopeFromSelection(it, community, currentName, runBack) }

            addButton(
                slot = 34,
                name = Translator.trStringOrFallback("ui.admin.region.global.add.confirm", "Confirm District Creation"),
                item = Items.EMERALD_BLOCK
            ) { runConfirmScopeCreationFromSelection(it, community, currentName) }
        }
    }

    companion object {
        fun createMenuTitle(
            community: Community,
            currentName: String,
            playerEntity: ServerPlayer
        ): Component {
            val selectionState = ImyvmWorldGeo.pointSelectingPlayers[playerEntity.uuid]
            val hypotheticalShape = selectionState?.hypotheticalShape
            val isNormalSelectionMode = selectionState != null && hypotheticalShape is HypotheticalShape.Normal
            val pointCount = selectionState?.points?.size ?: 0
            val communityMark = community.generateCommunityMark()
            val addTitle = Translator.trStringOrFallback("ui.admin.region.global.add.title", "Add")
            if (selectionState == null) {
                val hint = Translator.trStringOrFallback("ui.admin.region.global.add.hint.start", "→ Enable Mode")
                return Component.literal("$communityMark | $addTitle $hint")
            }
            if (!isNormalSelectionMode || pointCount < 2) {
                val hint = Translator.trStringOrFallback("ui.admin.region.global.add.hint.select", "→ Select Points")
                return Component.literal("$communityMark | $addTitle $hint")
            }
            val currentShape = hypotheticalShape.shapeType
            val existingScopeNames = community.getRegion()?.geometryScope?.map { it.scopeName }?.toSet() ?: emptySet()
            val error = generateScopeCreationError(currentName, currentShape, playerEntity, existingScopeNames)
            return Component.literal("$communityMark | $currentName" + if (error.isNotEmpty()) " ($error)" else "")
        }
    }
}
