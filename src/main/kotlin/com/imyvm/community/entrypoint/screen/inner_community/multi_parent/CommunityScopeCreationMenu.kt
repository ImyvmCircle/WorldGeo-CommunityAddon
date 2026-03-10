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
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityScopeCreationMenu(
    syncId: Int,
    val community: Community,
    val currentName: String = Translator.tr("ui.admin.region.global.add.default_name")?.string ?: "New-District",
    val playerExecutor: ServerPlayerEntity,
    val runBack: (ServerPlayerEntity) -> Unit
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
                name = Translator.tr("ui.create.button.selection_mode.enable")?.string ?: "Selection Mode: On",
                item = Items.COMMAND_BLOCK
            ) { runToggleSelectionModeInScopeCreation(it, community, currentName, runBack) }

            addButton(
                slot = 19,
                name = Translator.tr("ui.main.button.selection_mode.reset")?.string ?: "Reset Point Selection",
                item = Items.TNT
            ) { runResetSelectionInScopeCreation(it, community, currentName, runBack) }

            if (isNormalSelectionMode) {
                addButton(
                    slot = 12,
                    name = (Translator.tr("ui.create.button.shape.prefix")?.string ?: "Current Shape(Click to change):")
                            + when (currentShape) {
                                GeoShapeType.CIRCLE -> Translator.tr("community.shape.circle")?.string ?: "circle"
                                GeoShapeType.POLYGON -> Translator.tr("community.shape.polygon")?.string ?: "polygon"
                                else -> Translator.tr("community.shape.rectangle")?.string ?: "rectangle"
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
                    name = Translator.tr("ui.create.button.exit_to_select")?.string ?: "Exit Menu to Start Selecting",
                    item = Items.ENDER_PEARL
                ) { p -> p.closeHandledScreen() }
            }
        } else {
            addButton(
                slot = 10,
                name = Translator.tr("ui.create.button.selection_mode.disable")?.string ?: "Selection Mode: Off",
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
                name = Translator.tr("ui.admin.region.global.add.confirm")?.string ?: "Confirm District Creation",
                item = Items.EMERALD_BLOCK
            ) { runConfirmScopeCreationFromSelection(it, community, currentName) }
        }
    }

    companion object {
        fun createMenuTitle(
            community: Community,
            currentName: String,
            playerEntity: ServerPlayerEntity
        ): Text {
            val selectionState = ImyvmWorldGeo.pointSelectingPlayers[playerEntity.uuid]
            val hypotheticalShape = selectionState?.hypotheticalShape
            val isNormalSelectionMode = selectionState != null && hypotheticalShape is HypotheticalShape.Normal
            val pointCount = selectionState?.points?.size ?: 0
            val communityMark = community.generateCommunityMark()
            val addTitle = Translator.tr("ui.admin.region.global.add.title")?.string ?: "Add"
            if (selectionState == null) {
                val hint = Translator.tr("ui.admin.region.global.add.hint.start")?.string ?: "→ Enable Mode"
                return Text.of("$communityMark | $addTitle $hint")
            }
            if (!isNormalSelectionMode || pointCount < 2) {
                val hint = Translator.tr("ui.admin.region.global.add.hint.select")?.string ?: "→ Select Points"
                return Text.of("$communityMark | $addTitle $hint")
            }
            val currentShape = (hypotheticalShape as HypotheticalShape.Normal).shapeType
            val existingScopeNames = community.getRegion()?.geometryScope?.map { it.scopeName }?.toSet() ?: emptySet()
            val error = generateScopeCreationError(currentName, currentShape, playerEntity, existingScopeNames)
            return Text.of("$communityMark | $currentName" + if (error.isNotEmpty()) " ($error)" else "")
        }
    }
}
