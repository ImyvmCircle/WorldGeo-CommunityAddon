package com.imyvm.community.entrypoints.screen

import com.imyvm.community.entrypoints.screen.component.MenuButton
import com.imyvm.community.entrypoints.screen.component.ReadOnlySlot
import com.imyvm.community.util.Translator
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

abstract class AbstractMenu(
    syncId: Int,
    rows: Int = 6,
    private val defaultBackground: Item = Items.GRAY_STAINED_GLASS_PANE,
    private val defaultBackgroundName: String = " ",
    val menuTitle: Text? = Text.literal("Menu"),
    private val runBack: ((ServerPlayerEntity) -> Unit)? = null
) : ScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId) {

    private val inventory = SimpleInventory(rows * 9)
    private val buttons = mutableListOf<MenuButton>()

    init {
        fillBackground()
        setupSlots()
        addDefaultCloseButton()
        setupOptionalBackButton()
    }

    private fun fillBackground() {
        for (i in 0 until inventory.size()) {
            inventory.setStack(i, createItem(Text.literal(defaultBackgroundName), defaultBackground))
        }
    }

    private fun setupSlots() {
        for (i in 0 until inventory.size()) {
            addSlot(ReadOnlySlot(inventory, i, 0, 0))
        }
    }

    protected fun addButton(slot: Int, name: String, item: Item, onClick: (ServerPlayerEntity) -> Unit) {
        buttons.add(MenuButton(slot, item, name, onClick))
        inventory.setStack(slot, createItem(Text.literal(name), item))
    }

    protected fun addButton(slot: Int, itemStack: ItemStack, name: String? = null, onClick: (ServerPlayerEntity) -> Unit) {
        val finalName = name ?: itemStack.name.string
        if (name != null) {
            itemStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name))
        }
        buttons.add(MenuButton(slot, itemStack.item, finalName, onClick))
        inventory.setStack(slot, itemStack)
    }

   fun incrementSlotIndex(slot: Int): Int {
        var newSlot = slot + 1
        while (newSlot % 9 == 0) {
            newSlot += 1
        }
        return newSlot
    }

    private fun createItem(name: Text, item: Item): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponentTypes.CUSTOM_NAME, name)
        return stack
    }

    private fun addDefaultCloseButton() {
        addButton(
            slot = 53,
            name = Translator.tr("ui.general.button.close")?.string ?: "Close",
            item = Items.BARRIER
        ) { playerExecutor -> runClose(playerExecutor) }
    }

    private fun setupOptionalBackButton() {
        runBack?.let { backLogic ->
            addButton(
                slot = 44,
                name = Translator.tr("ui.general.button.back")?.string ?: "Back",
                item = Items.ARROW
            ) { playerExecutor -> backLogic(playerExecutor) }
        }
    }

    private fun runClose(playerExecutor: ServerPlayerEntity) {
        playerExecutor.closeHandledScreen()
        playerExecutor.sendMessage(Translator.tr("ui.general.button.close.feedback"))
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, playerExecutor: PlayerEntity) {
        super.onSlotClick(slotIndex, button, actionType, playerExecutor)
        if (slotIndex < 0 || slotIndex >= inventory.size()) return

        (playerExecutor as? ServerPlayerEntity)?.let { p ->
            val clickedName = inventory.getStack(slotIndex).get(DataComponentTypes.CUSTOM_NAME)?.string
            buttons.find { it.slot == slotIndex && it.name == clickedName }?.onClick?.invoke(p)
        }
    }

    override fun canUse(playerExecutor: PlayerEntity) = true
    override fun quickMove(playerExecutor: PlayerEntity, slotIndex: Int): ItemStack = ItemStack.EMPTY
}