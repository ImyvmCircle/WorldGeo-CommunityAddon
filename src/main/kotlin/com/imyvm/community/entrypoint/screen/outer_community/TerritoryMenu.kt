package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.outer_community.runAddScope
import com.imyvm.community.application.interaction.screen.outer_community.runModifyScope
import com.imyvm.community.application.interaction.screen.outer_community.runOpenCommunityCreation
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.HypotheticalShape
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class TerritoryMenu(
    syncId: Int,
    playerExecutor: ServerPlayerEntity,
    runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId = syncId,
    menuTitle = createMenuTitle(playerExecutor),
    runBack = runBack
) {
    init {
        addButton(
            slot = 10,
            name = Translator.tr("ui.territory.button.create")?.string ?: "Create Community",
            item = Items.DIAMOND_PICKAXE
        ) { runOpenCommunityCreation(it, runBack) }

        addButton(
            slot = 13,
            name = Translator.tr("ui.territory.button.add_scope")?.string ?: "Add Scope",
            item = Items.GRASS_BLOCK
        ) { runAddScope(it, runBack) }

        addButton(
            slot = 16,
            name = Translator.tr("ui.territory.button.modify")?.string ?: "Modify Territory",
            item = Items.SHEARS
        ) { runModifyScope(it, runBack) }
    }

    companion object {
        private fun createMenuTitle(playerExecutor: ServerPlayerEntity): Text {
            val base = Translator.tr("ui.territory.title")?.string ?: "Territory"
            return when (val hypotheticalShape = ImyvmWorldGeo.pointSelectingPlayers[playerExecutor.uuid]?.hypotheticalShape) {
                is HypotheticalShape.Normal -> {
                    val hint = Translator.tr("ui.territory.title.hint.creating")?.string ?: "[Creating]"
                    Text.of("$base $hint")
                }
                is HypotheticalShape.ModifyExisting -> {
                    val prefix = Translator.tr("ui.territory.title.hint.modifying_prefix")?.string ?: "[Modifying: "
                    Text.of("$base $prefix${hypotheticalShape.scope.scopeName}]")
                }
                else -> Translator.tr("ui.territory.title") ?: Text.literal("Territory")
            }
        }
    }
}
