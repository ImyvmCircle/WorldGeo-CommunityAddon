package com.imyvm.community.entrypoint.screen

import com.imyvm.community.entrypoint.screen.component.ReadOnlySlot
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.MenuProvider
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

abstract class AbstractRenameMenuAnvil(
    protected val player: ServerPlayer,
    protected val initialName: String,
    private val errorHint: String? = null
) {
    private var currentName: String? = null
    private var hasConfirmed = false

    protected abstract fun processRenaming(finalName: String)
    protected abstract fun getMenuTitle(): Component
    protected abstract fun reopenWith(errorHint: String?, currentInput: String)

    protected open fun isNameValid(name: String): Boolean = UtilApi.isValidName(name)

    fun open() {
        player.openMenu(object : MenuProvider {
            override fun createMenu(syncId: Int, inv: Inventory, p: Player): AbstractContainerMenu {
                val context = ContainerLevelAccess.create(p.level(), p.blockPosition())
                val simpleInventory = SimpleContainer(3)

                val anvil = object : AnvilMenu(syncId, inv, context) {
                    init {
                        this.slots[INPUT_SLOT] = ReadOnlySlot(simpleInventory, INPUT_SLOT, 27, 47)
                        this.slots[ADDITIONAL_SLOT] = ReadOnlySlot(simpleInventory, ADDITIONAL_SLOT, 76, 47)
                        this.slots[RESULT_SLOT] = ReadOnlySlot(simpleInventory, RESULT_SLOT, 125, 47)
                    }

                    override fun stillValid(player: Player) = true

                    override fun setItemName(name: String): Boolean {
                        currentName = name
                        return super.setItemName(name)
                    }

                    override fun createResult() {
                        super.createResult()
                        val name = currentName?.trim()?.takeIf { it.isNotEmpty() } ?: ""
                        if (name.isEmpty() || !isNameValid(name)) {
                            slots[RESULT_SLOT].set(ItemStack.EMPTY)
                        }
                    }

                    override fun clicked(slotIndex: Int, button: Int, actionType: ContainerInput, player: Player) {
                        if (slotIndex == RESULT_SLOT && !hasConfirmed) {
                            val name = currentName?.trim()?.takeIf { it.isNotEmpty() } ?: ""
                            if (name.isEmpty()) return
                            if (!isNameValid(name)) {
                                if (name != initialName) {
                                    this@AbstractRenameMenuAnvil.player.closeContainer()
                                    this@AbstractRenameMenuAnvil.player.level().server.execute {
                                        reopenWith(
                                            Translator.tr("ui.create.error.name_invalid_format").string,
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
                nameTag.set(DataComponents.CUSTOM_NAME, Component.literal(initialName))
                anvil.slots[AnvilMenu.INPUT_SLOT].set(nameTag)
                anvil.setItemName(initialName)
                anvil.createResult()

                return anvil
            }

            override fun getDisplayName(): Component = getMenuTitle()
        })
    }

    protected fun buildTitle(base: Component): Component {
        if (errorHint == null) return base
        return Component.empty().append(base).append(Component.literal(" ($errorHint)"))
    }
}