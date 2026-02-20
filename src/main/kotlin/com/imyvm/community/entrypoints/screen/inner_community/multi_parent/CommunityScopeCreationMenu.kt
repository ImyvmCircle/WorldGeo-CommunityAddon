package com.imyvm.community.entrypoints.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.helper.generateScopeCreationError
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runConfirmScopeCreation
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runRenameNewScope
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runSwitchScopeShape
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityScopeCreationMenu(
    syncId: Int,
    private val community: Community,
    private val currentName: String = Translator.tr("ui.community.administration.region.global.add.default_name")?.string
        ?: "New-District",
    private val currentShape: GeoShapeType = GeoShapeType.RECTANGLE,
    private val playerExecutor: ServerPlayerEntity,
    private val runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = createMenuTitle(community, currentName, currentShape, playerExecutor),
    runBack = runBack
) {
    init {
        addButton(
            slot = 10,
            name = currentName,
            item = Items.NAME_TAG
        ) { runRenameNewScope(it, community, currentName, currentShape, runBack) }

        addButton(
            slot = 13,
            name = (Translator.tr("ui.create.button.shape.prefix")?.string ?: "Current Shape(Click to change):") + currentShape,
            item = when (currentShape) {
                GeoShapeType.CIRCLE -> Items.CLOCK
                GeoShapeType.RECTANGLE -> Items.MAP
                GeoShapeType.POLYGON -> Items.NETHER_STAR
                GeoShapeType.UNKNOWN -> Items.STRUCTURE_BLOCK
            }
        ) { runSwitchScopeShape(it, community, currentName, currentShape, runBack) }

        addButton(
            slot = 35,
            name = Translator.tr("ui.community.administration.region.global.add.confirm")?.string ?: "Confirm District Creation",
            item = Items.EMERALD_BLOCK
        ) { runConfirmScopeCreation(it, community, currentName, currentShape) }
    }

    companion object {
        private fun createMenuTitle(
            community: Community,
            currentName: String,
            currentShape: GeoShapeType,
            playerEntity: ServerPlayerEntity
        ): Text {
            val existingScopeNames = community.getRegion()?.geometryScope?.map { it.scopeName }?.toSet() ?: emptySet()
            val error = generateScopeCreationError(currentName, currentShape, playerEntity, existingScopeNames)
            val prefix = Translator.tr("ui.community.administration.region.global.add.title")?.string ?: "Add Administrative District"
            return Text.of(prefix + ": " + currentName + if (error.isNotEmpty()) " ($error)" else "")
        }
    }
}
