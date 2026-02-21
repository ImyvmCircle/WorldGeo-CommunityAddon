package com.imyvm.community.entrypoint.screen

import com.imyvm.community.entrypoint.screen.component.ReadOnlySlot
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.AnvilScreenHandler
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

abstract class AbstractRenameMenuAnvil(
    protected val player: ServerPlayerEntity,
    protected val initialName: String
) {
    private var newName: String? = null
    private var hasConfirmed = false

    protected abstract fun processRenaming(finalName: String)
    protected abstract fun getMenuTitle(): Text

    fun open() {
        player.openHandledScreen(object : NamedScreenHandlerFactory {
            override fun createMenu(syncId: Int, inv: PlayerInventory, p: PlayerEntity): ScreenHandler {
                val context = ScreenHandlerContext.create(p.world, p.blockPos)
                val simpleInventory = SimpleInventory(3)

                val anvil = object : AnvilScreenHandler(syncId, inv, context) {
                    init {
                        this.slots[INPUT_1_ID] = ReadOnlySlot(simpleInventory, INPUT_1_ID, 27, 47)
                        this.slots[INPUT_2_ID] = ReadOnlySlot(simpleInventory, INPUT_2_ID, 76, 47)
                        this.slots[OUTPUT_ID] = ReadOnlySlot(simpleInventory, OUTPUT_ID, 125, 47)
                    }

                    override fun canUse(player: PlayerEntity?) = true

                    override fun setNewItemName(name: String?): Boolean {
                        newName = name
                        return super.setNewItemName(name)
                    }

                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
                        if (slotIndex == OUTPUT_ID && !hasConfirmed) {
                            hasConfirmed = true
                            val finalName = newName?.trim()?.takeIf { it.isNotEmpty() } ?: ""
                            processRenaming(finalName)
                        }
                    }
                }

                val nameTag = ItemStack(Items.NAME_TAG)
                nameTag.set(DataComponentTypes.CUSTOM_NAME, Text.of(initialName))
                anvil.slots[AnvilScreenHandler.INPUT_1_ID].stack = nameTag
                anvil.setNewItemName(initialName)
                anvil.updateResult()

                return anvil
            }

            override fun getDisplayName(): Text = getMenuTitle()
        })
    }
}