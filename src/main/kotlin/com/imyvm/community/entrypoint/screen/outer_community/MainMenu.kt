package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.outer_community.*
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer

class MainMenu(
    syncId: Int,
    val playerExecutor: ServerPlayer
) : AbstractMenu(
        syncId,
        menuTitle = Translator.tr("ui.main.title")
    ) {

    init {
        addGeneralButtons()
        addSelectionModeButtons()
        if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(playerExecutor.permissions())) { addServerOperatorButton() }
        addActionBarToggleButton()
    }

    private fun addGeneralButtons() {
        addButton(
            slot = 10,
            name = Translator.tr("ui.main.button.list").string,
            item = Items.WRITABLE_BOOK
        ) { runList(it) }

        addButton(
            slot = 13,
            name = Translator.tr("ui.main.button.geo").string,
            item = Items.DIAMOND_PICKAXE
        ) { runGeoOperation(it) }

        addButton(
            slot = 16,
            name = Translator.tr("ui.main.button.my").string,
            item = Items.RED_BED
        ) { runMyCommunity(it) }
    }

    private fun addSelectionModeButtons() {
        val isSelectionModeEnabled = ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerExecutor.uuid)
        if (!isSelectionModeEnabled) return

        addButton(
            slot = 22,
            name = Translator.tr("ui.create.button.selection_mode.close").string,
            item = Items.COMMAND_BLOCK
        ) { runToggleSelectionMode(it) }

        addButton(
            slot = 31,
            name = Translator.tr("ui.main.button.selection_mode.reset").string,
            item = Items.TNT
        ) { runResetSelection(it) }
    }

    private fun addServerOperatorButton() {
        addButton(
            slot = 19,
            name = Translator.tr("ui.main.button.op").string,
            item = Items.ANVIL
        ) {}
    }

    private fun addActionBarToggleButton() {
        val isRegionActionBarEnabled = ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(playerExecutor.uuid)
        addButton(
            slot = 44,
            name = if (isRegionActionBarEnabled) {
                Translator.tr("ui.main.button.action_bar.enable").string
            } else {
                Translator.tr("ui.main.button.action_bar.disable").string
            },
            item = if (isRegionActionBarEnabled) Items.LIME_DYE else Items.GRAY_DYE
        ) { runToggleActionBar(playerExecutor)}
    }
}