package com.imyvm.community.application.interaction.screen

import com.imyvm.community.entrypoints.screen.AbstractMenu
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

object CommunityMenuOpener {
    fun <M : AbstractMenu> open(
        player: ServerPlayerEntity,
        handlerFactory: (syncId: Int) -> M
    ) {
        player.openHandledScreen(object : NamedScreenHandlerFactory {
            override fun createMenu(syncId: Int, inv: PlayerInventory, player: PlayerEntity): M =
                handlerFactory(syncId)

            override fun getDisplayName(): Text? =
                handlerFactory(0).menuTitle
        })
    }

}