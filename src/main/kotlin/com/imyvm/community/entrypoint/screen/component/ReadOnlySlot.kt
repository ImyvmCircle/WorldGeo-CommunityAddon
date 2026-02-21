package com.imyvm.community.entrypoint.screen.component

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

class ReadOnlySlot(inv: Inventory, index: Int, x: Int, y: Int) : Slot(inv, index, x, y) {
    override fun canTakeItems(player: PlayerEntity) = false
    override fun canInsert(stack: ItemStack) = false
}