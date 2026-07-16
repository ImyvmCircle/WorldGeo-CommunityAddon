package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.outer_community.runAddScope
import com.imyvm.community.application.interaction.screen.outer_community.runModifyScope
import com.imyvm.community.application.interaction.screen.outer_community.runOpenCommunityCreation
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.HypotheticalShape
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class TerritoryMenu(
    syncId: Int,
    playerExecutor: ServerPlayer,
    runBack: ((ServerPlayer) -> Unit)
) : AbstractMenu(
    syncId = syncId,
    menuTitle = createMenuTitle(playerExecutor),
    runBack = runBack
) {
    init {
        addButton(
            slot = 10,
            name = Translator.trStringOrFallback("ui.territory.button.create", "Create Community"),
            item = Items.DIAMOND_PICKAXE
        ) { runOpenCommunityCreation(it, runBack) }

        addButton(
            slot = 13,
            name = Translator.trStringOrFallback("ui.territory.button.add_scope", "Add Scope"),
            item = Items.GRASS_BLOCK
        ) { runAddScope(it, runBack) }

        addButton(
            slot = 16,
            name = Translator.trStringOrFallback("ui.territory.button.modify", "Modify Territory"),
            item = Items.SHEARS
        ) { runModifyScope(it, runBack) }
    }

    companion object {
        private fun createMenuTitle(playerExecutor: ServerPlayer): Component {
            val base = Translator.trStringOrFallback("ui.territory.title", "Territory")
            return when (val hypotheticalShape = ImyvmWorldGeo.pointSelectingPlayers[playerExecutor.uuid]?.hypotheticalShape) {
                is HypotheticalShape.Normal -> {
                    val hint = Translator.trStringOrFallback("ui.territory.title.hint.creating", "[Creating]")
                    Component.literal("$base $hint")
                }
                is HypotheticalShape.ModifyExisting -> {
                    val prefix = Translator.trStringOrFallback("ui.territory.title.hint.modifying_prefix", "[Modifying: ")
                    Component.literal("$base $prefix${hypotheticalShape.scope.scopeName}]")
                }
                else -> Translator.trOrFallback("ui.territory.title", "Territory")
            }
        }
    }
}
