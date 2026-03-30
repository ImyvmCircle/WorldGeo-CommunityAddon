package com.imyvm.community.entrypoint.screen

import com.imyvm.community.entrypoint.screen.component.MenuButton
import com.imyvm.community.entrypoint.screen.component.ReadOnlySlot
import com.imyvm.community.util.Translator
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

abstract class AbstractMenu(
    syncId: Int,
    rows: Int = 6,
    private val defaultBackground: Item = Items.GRAY_STAINED_GLASS_PANE,
    private val defaultBackgroundName: String = " ",
    val menuTitle: Component? = Component.literal("Menu"),
    private val runBack: ((ServerPlayer) -> Unit)? = null
) : AbstractContainerMenu(MenuType.GENERIC_9x6, syncId) {

    private val inventory = SimpleContainer(rows * 9)
    private val buttons = mutableListOf<MenuButton>()

    init {
        fillBackground()
        setupSlots()
        addDefaultCloseButton()
        setupOptionalBackButton()
    }

    private fun fillBackground() {
        for (i in 0 until inventory.getContainerSize()) {
            inventory.setItem(i, createItem(Component.literal(defaultBackgroundName), defaultBackground))
        }
    }

    private fun setupSlots() {
        for (i in 0 until inventory.getContainerSize()) {
            addSlot(ReadOnlySlot(inventory, i, 0, 0))
        }
    }

    protected fun addButton(slot: Int, name: String, item: Item, onClick: (ServerPlayer) -> Unit) {
        buttons.add(MenuButton(slot, item, name, onClick))
        inventory.setItem(slot, createItem(Component.literal(name), item))
    }

    protected fun addButton(slot: Int, itemStack: ItemStack, name: String? = null, onClick: (ServerPlayer) -> Unit) {
        val finalName = name ?: itemStack.hoverName.string
        if (name != null) {
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        }
        buttons.add(MenuButton(slot, itemStack.item, finalName, onClick))
        inventory.setItem(slot, itemStack)
    }

   fun incrementSlotIndex(slot: Int): Int {
        var newSlot = slot + 1
        while (newSlot % 9 == 0) {
            newSlot += 1
        }
        return newSlot
    }

    private fun createItem(name: Component, item: Item): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, name)
        return stack
    }

    private fun addDefaultCloseButton() {
        addButton(
            slot = 53,
            name = Translator.tr("ui.general.button.close").string ?: "Close",
            item = Items.BARRIER
        ) { playerExecutor -> runClose(playerExecutor) }
    }

    private fun setupOptionalBackButton() {
        runBack?.let { backLogic ->
            addButton(
                slot = 44,
                name = Translator.tr("ui.general.button.back").string ?: "Back",
                item = Items.ARROW
            ) { playerExecutor -> backLogic(playerExecutor) }
        }
    }

    private fun runClose(playerExecutor: ServerPlayer) {
        playerExecutor.closeContainer()
        playerExecutor.sendSystemMessage(Translator.tr("ui.general.button.close.feedback"))
    }

    override fun clicked(slotIndex: Int, button: Int, actionType: ContainerInput, playerExecutor: Player) {
        super.clicked(slotIndex, button, actionType, playerExecutor)
        if (slotIndex < 0 || slotIndex >= inventory.getContainerSize()) return

        (playerExecutor as? ServerPlayer)?.let { p ->
            val clickedName = inventory.getItem(slotIndex).get(DataComponents.CUSTOM_NAME)?.string
            buttons.find { it.slot == slotIndex && it.name == clickedName }?.onClick?.invoke(p)
        }
    }

    override fun stillValid(playerExecutor: Player) = true
    override fun quickMoveStack(playerExecutor: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY
}