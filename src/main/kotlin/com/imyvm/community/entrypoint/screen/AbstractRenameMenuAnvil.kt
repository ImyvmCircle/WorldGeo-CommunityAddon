package com.imyvm.community.entrypoint.screen

import com.imyvm.community.entrypoint.screen.component.ReadOnlySlot
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
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
    protected val initialName: String,
    private val errorHint: String? = null
) {
    private var currentName: String? = null
    private var hasConfirmed = false

    protected abstract fun processRenaming(finalName: String)
    protected abstract fun getMenuTitle(): Text
    protected abstract fun reopenWith(errorHint: String?, currentInput: String)

    protected open fun isNameValid(name: String): Boolean = UtilApi.isValidName(name)

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
                        currentName = name
                        return super.setNewItemName(name)
                    }

                    override fun updateResult() {
                        super.updateResult()
                        val name = currentName?.trim()?.takeIf { it.isNotEmpty() } ?: ""
                        if (name.isEmpty() || !isNameValid(name)) {
                            slots[OUTPUT_ID].stack = ItemStack.EMPTY
                        }
                    }

                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
                        if (slotIndex == OUTPUT_ID && !hasConfirmed) {
                            val name = currentName?.trim()?.takeIf { it.isNotEmpty() } ?: ""
                            if (name.isEmpty()) return
                            if (!isNameValid(name)) {
                                if (name != initialName) {
                                    this@AbstractRenameMenuAnvil.player.closeHandledScreen()
                                    this@AbstractRenameMenuAnvil.player.server.execute {
                                        reopenWith(
                                            Translator.tr("ui.create.error.name_invalid_format")?.string,
                                            name
                                        )
                                    }
                                }
                                return
                            }
                            hasConfirmed = true
                            processRenaming(name)
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

    protected fun buildTitle(base: Text): Text {
        if (errorHint == null) return base
        return Text.empty().append(base).append(Text.literal(" ($errorHint)"))
    }
}