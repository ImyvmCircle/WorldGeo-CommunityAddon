package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.outer_community.*
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class MainMenu(
    syncId: Int,
    val playerExecutor: ServerPlayerEntity
) : AbstractMenu(
        syncId,
        menuTitle = Translator.tr("ui.main.title") ?: Text.literal("Community Menu")
    ) {

    init {
        addGeneralButtons()
        addSelectionModeButtons()
        if (playerExecutor.hasPermissionLevel(2)) { addServerOperatorButton() }
        addActionBarToggleButton()
    }

    private fun addGeneralButtons() {
        addButton(
            slot = 10,
            name = Translator.tr("ui.main.button.list")?.string ?: "List",
            item = Items.WRITABLE_BOOK
        ) { runList(it) }

        addButton(
            slot = 13,
            name = Translator.tr("ui.main.button.geo")?.string ?: "Territory",
            item = Items.DIAMOND_PICKAXE
        ) { runGeoOperation(it) }

        addButton(
            slot = 16,
            name = Translator.tr("ui.main.button.my")?.string ?: "My Village",
            item = Items.RED_BED
        ) { runMyCommunity(it) }
    }

    private fun addSelectionModeButtons() {
        val isSelectionModeEnabled = ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerExecutor.uuid)
        if (!isSelectionModeEnabled) return

        addButton(
            slot = 22,
            name = Translator.tr("ui.create.button.selection_mode.close")?.string ?: "Close Selection Mode",
            item = Items.COMMAND_BLOCK
        ) { runToggleSelectionMode(it) }

        addButton(
            slot = 23,
            name = Translator.tr("ui.main.button.selection_mode.reset")?.string ?: "Reset Point Selection",
            item = Items.TNT
        ) { runResetSelection(it) }
    }

    private fun addServerOperatorButton() {
        addButton(
            slot = 19,
            name = Translator.tr("ui.main.button.op")?.string ?: "OP",
            item = Items.ANVIL
        ) {}
    }

    private fun addActionBarToggleButton() {
        val isRegionActionBarEnabled = ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(playerExecutor.uuid)
        addButton(
            slot = 44,
            name = if (isRegionActionBarEnabled) {
                Translator.tr("ui.main.button.action_bar.enable")?.string ?: "Action Bar : Enabled"
            } else {
                Translator.tr("ui.main.button.action_bar.disable")?.string ?: "Action Bar: Disabled"
            },
            item = if (isRegionActionBarEnabled) Items.LIME_DYE else Items.GRAY_DYE
        ) { runToggleActionBar(playerExecutor)}
    }
}