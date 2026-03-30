package com.imyvm.community.entrypoint.screen.component

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component

fun getLoreButton(itemStack: ItemStack, loreLines: List<Component>): ItemStack {
    val lore = ItemLore(loreLines)
    itemStack.set(DataComponents.LORE, lore)

    return itemStack
}