package com.imyvm.community.entrypoint.screen.component

import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.Slot

class ReadOnlySlot(inv: Container, index: Int, x: Int, y: Int) : Slot(inv, index, x, y) {
    override fun mayPickup(player: Player) = false
    override fun mayPlace(stack: ItemStack) = false
}