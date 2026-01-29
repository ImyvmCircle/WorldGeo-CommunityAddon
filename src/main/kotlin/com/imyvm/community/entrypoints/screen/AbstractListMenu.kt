package com.imyvm.community.entrypoints.screen

import com.imyvm.community.util.Translator
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

abstract class AbstractListMenu(
    syncId: Int,
    menuTitle: Text?,
    val page: Int = 0,
    runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = menuTitle,
    runBack = runBack
) {
    fun handlePage(listSize: Int) {
        val totalPages = calculateTotalPages(listSize)
        addPageButtons(totalPages) { player, newPage -> openNewPage(player, newPage) }
    }
    protected abstract fun calculateTotalPages(listSize: Int): Int

    protected abstract fun openNewPage(player: ServerPlayerEntity, newPage: Int)

    private fun addPageButtons(totalPages: Int, openNewPage: (player: ServerPlayerEntity, newPage: Int) -> Unit) {
        if (page > 0) {
            addButton(slot = 0, name = Translator.tr("ui.general.list.prev")?.string ?: "Previous", itemStack = ItemStack(Items.ARROW)) {
                openNewPage(it, page - 1)
            }
        }

        if (page < totalPages - 1) {
            addButton(slot = 8, name = Translator.tr("ui.general.list.next")?.string ?: "Next", itemStack = ItemStack(Items.ARROW)) {
                openNewPage(it, page + 1)
            }
        }
    }
}