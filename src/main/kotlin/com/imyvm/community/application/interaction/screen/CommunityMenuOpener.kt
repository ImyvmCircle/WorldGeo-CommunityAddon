package com.imyvm.community.application.interaction.screen

import com.imyvm.community.entrypoint.screen.AbstractMenu
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.MenuProvider
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

object CommunityMenuOpener {
    fun <M : AbstractMenu> open(
        player: ServerPlayer,
        handlerFactory: (syncId: Int) -> M
    ) {
        player.openMenu(object : MenuProvider {
            override fun createMenu(syncId: Int, inv: Inventory, player: Player): M =
                handlerFactory(syncId)

            override fun getDisplayName(): Component =
                handlerFactory(0).menuTitle ?: Component.empty()
        })
    }

}