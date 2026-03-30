package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.helper.generateCreationError
import com.imyvm.community.application.interaction.screen.outer_community.runConfirmCommunityCreationFromSelectionMenu
import com.imyvm.community.application.interaction.screen.outer_community.runRenameNewCommunityFromSelectionMenu
import com.imyvm.community.application.interaction.screen.outer_community.runResetSelectionInCreation
import com.imyvm.community.application.interaction.screen.outer_community.runSwitchCommunityTypeInSelectionMenu
import com.imyvm.community.application.interaction.screen.outer_community.runSwitchSelectionShapeInCreation
import com.imyvm.community.application.interaction.screen.outer_community.runToggleSelectionModeInCreation
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class CommunityCreationSelectionMenu(
    syncId: Int,
    currentName: String,
    isCurrentCommunityTypeManor: Boolean = true,
    playerExecutor: ServerPlayer,
    runBack: ((ServerPlayer) -> Unit)
) : AbstractMenu(
    syncId = syncId,
    menuTitle = createMenuTitle(currentName, isCurrentCommunityTypeManor, playerExecutor),
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
                name = Translator.tr("ui.create.button.selection_mode.enable").string ?: "Selection Mode: Enabled",
                item = Items.COMMAND_BLOCK
            ) { runToggleSelectionModeInCreation(it, currentName, isCurrentCommunityTypeManor, runBack) }

            addButton(
                slot = 19,
                name = Translator.tr("ui.main.button.selection_mode.reset").string ?: "Reset Point Selection",
                item = Items.TNT
            ) { runResetSelectionInCreation(it, currentName, isCurrentCommunityTypeManor, runBack) }

            if (isNormalSelectionMode) {
                addButton(
                    slot = 12,
                    name = (Translator.tr("ui.create.button.shape.prefix").string ?: "Current Shape(Click to change):")
                            + when (currentShape) {
                                GeoShapeType.CIRCLE -> Translator.tr("community.shape.circle").string ?: "circle"
                                GeoShapeType.POLYGON -> Translator.tr("community.shape.polygon").string ?: "polygon"
                                else -> Translator.tr("community.shape.rectangle").string ?: "rectangle"
                            },
                    item = when (currentShape) {
                        GeoShapeType.CIRCLE -> Items.CLOCK
                        GeoShapeType.RECTANGLE -> Items.MAP
                        GeoShapeType.POLYGON -> Items.NETHER_STAR
                        GeoShapeType.UNKNOWN -> Items.STRUCTURE_BLOCK
                    }
                ) { runSwitchSelectionShapeInCreation(it, currentName, isCurrentCommunityTypeManor, runBack) }

                addButton(
                    slot = 14,
                    name = Translator.tr("ui.create.button.exit_to_select").string ?: "Exit Menu to Start Selecting",
                    item = Items.ENDER_PEARL
                ) { p -> p.closeContainer() }
            }
        } else {
            addButton(
                slot = 10,
                name = Translator.tr("ui.create.button.selection_mode.disable").string ?: "Selection Mode: Disabled",
                item = Items.REDSTONE_BLOCK
            ) { runToggleSelectionModeInCreation(it, currentName, isCurrentCommunityTypeManor, runBack) }
        }

        if (hasEnoughPoints) {
            addButton(
                slot = 28,
                name = currentName,
                item = Items.NAME_TAG
            ) { runRenameNewCommunityFromSelectionMenu(it, currentName, isCurrentCommunityTypeManor, runBack) }

            addButton(
                slot = 31,
                name = if (isCurrentCommunityTypeManor) Translator.tr("ui.create.button.type.manor").string ?: "Manor"
                else Translator.tr("ui.create.button.type.realm").string ?: "Realm",
                item = if (isCurrentCommunityTypeManor) Items.BIRCH_PLANKS else Items.CHERRY_PLANKS
            ) { runSwitchCommunityTypeInSelectionMenu(it, currentName, isCurrentCommunityTypeManor, runBack) }

            addButton(
                slot = 34,
                name = Translator.tr("ui.create.button.confirm").string ?: "Confirm Creation",
                item = Items.EMERALD_BLOCK
            ) { runConfirmCommunityCreationFromSelectionMenu(it, currentName, isCurrentCommunityTypeManor) }
        }
    }

    companion object {
        private fun createMenuTitle(
            currentName: String,
            isCurrentCommunityTypeManor: Boolean,
            playerEntity: ServerPlayer
        ): Component {
            val selectionState = ImyvmWorldGeo.pointSelectingPlayers[playerEntity.uuid]
            val hypotheticalShape = selectionState?.hypotheticalShape
            val isNormalSelectionMode = selectionState != null && hypotheticalShape is HypotheticalShape.Normal
            val pointCount = selectionState?.points?.size ?: 0
            val baseTitle = Translator.tr("ui.create.selection.title").string ?: "Create Community"
            if (selectionState == null) {
                val hint = Translator.tr("ui.admin.region.global.add.hint.start").string ?: "→ Enable Mode"
                return Component.literal("$baseTitle $hint")
            }
            if (!isNormalSelectionMode || pointCount < 2) {
                val hint = Translator.tr("ui.admin.region.global.add.hint.select").string ?: "→ Select Points"
                return Component.literal("$baseTitle $hint")
            }
            val currentShape = (hypotheticalShape as HypotheticalShape.Normal).shapeType
            val error = generateCreationError(currentName, currentShape, isCurrentCommunityTypeManor, playerEntity)
            return Component.literal(currentName + if (error.isNotEmpty()) " ($error)" else "")
        }
    }
}
