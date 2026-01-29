package com.imyvm.community.entrypoints.screen.outer_community

import com.imyvm.community.application.interaction.screen.helper.generateCreationError
import com.imyvm.community.application.interaction.screen.outer_community.runConfirmCommunityCreation
import com.imyvm.community.application.interaction.screen.outer_community.runRenameNewCommunity
import com.imyvm.community.application.interaction.screen.outer_community.runSwitchCommunityShape
import com.imyvm.community.application.interaction.screen.outer_community.runSwitchCommunityType
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityCreationMenu(
    syncId: Int,
    currentName: String = Translator.tr("ui.create.title")?.string ?: "New-Creating-Community",
    currentShape: GeoShapeType = GeoShapeType.RECTANGLE,
    isCurrentCommunityTypeManor: Boolean = true,
    playerExecutor: ServerPlayerEntity,
    runBack : ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId = syncId,
    menuTitle = createMenuTitle(currentName, currentShape, isCurrentCommunityTypeManor, playerExecutor),
    runBack = runBack
) {
    init {
        addButton(
            slot = 10,
            name = currentName,
            item = Items.NAME_TAG
        ) { runRenameNewCommunity(it, currentName, currentShape, isCurrentCommunityTypeManor, runBack) }

        addButton(
            slot = 13,
            name = (Translator.tr("ui.create.button.shape.prefix")?.string ?: "Current Shape(Click to change):")
                    + currentShape.toString(),
            item = when (currentShape) {
                GeoShapeType.CIRCLE -> Items.CLOCK
                GeoShapeType.RECTANGLE -> Items.MAP
                GeoShapeType.POLYGON -> Items.NETHER_STAR
                GeoShapeType.UNKNOWN -> Items.STRUCTURE_BLOCK
            }
        ) { runSwitchCommunityShape(it, currentName, currentShape, isCurrentCommunityTypeManor, runBack) }

        addButton(
            slot = 16,
            name = if (isCurrentCommunityTypeManor) Translator.tr("ui.create.button.type.manor")?.string ?: "Manor"
            else Translator.tr("ui.create.button.type.realm")?.string ?: "Realm",
            item = if (isCurrentCommunityTypeManor) Items.BIRCH_PLANKS else Items.CHERRY_PLANKS
        ) { runSwitchCommunityType(it, currentName, currentShape, isCurrentCommunityTypeManor, runBack) }

        addButton(
            slot = 35,
            name = Translator.tr("ui.create.button.confirm")?.string ?: "Confirm Creation",
            item = Items.EMERALD_BLOCK
        ) { runConfirmCommunityCreation(it, currentName, currentShape, isCurrentCommunityTypeManor) }
    }

    companion object {
        private fun createMenuTitle(
            currentName: String,
            currentShape: GeoShapeType,
            isCurrentCommunityTypeManor: Boolean,
            playerEntity: ServerPlayerEntity
        ): Text {
            val error = generateCreationError(currentName, currentShape, isCurrentCommunityTypeManor, playerEntity)
            return Text.of(currentName + if (error.isNotEmpty()) " ($error)" else "")
        }
    }
}