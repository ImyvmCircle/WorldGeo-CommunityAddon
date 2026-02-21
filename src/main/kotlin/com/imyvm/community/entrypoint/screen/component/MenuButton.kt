package com.imyvm.community.entrypoint.screen.component

import net.minecraft.item.Item
import net.minecraft.server.network.ServerPlayerEntity

data class MenuButton(
    val slot: Int,
    val item: Item,
    val name: String,
    val onClick: (ServerPlayerEntity) -> Unit
)