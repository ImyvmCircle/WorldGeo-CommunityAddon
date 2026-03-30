package com.imyvm.community.entrypoint.screen.component

import net.minecraft.world.item.Item
import net.minecraft.server.level.ServerPlayer

data class MenuButton(
    val slot: Int,
    val item: Item,
    val name: String,
    val onClick: (ServerPlayer) -> Unit
)