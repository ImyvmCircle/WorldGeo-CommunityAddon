package com.imyvm.community.entrypoint.screen.component

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

fun getLoreButton(itemStack: ItemStack, loreLines: List<Text>): ItemStack {
    val lore = LoreComponent(loreLines)
    itemStack.set(DataComponentTypes.LORE, lore)

    return itemStack
}