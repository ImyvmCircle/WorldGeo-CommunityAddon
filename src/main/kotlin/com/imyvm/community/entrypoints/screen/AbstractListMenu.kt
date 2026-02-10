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

    protected fun <T> renderList(
        items: List<T>,
        itemsPerPage: Int,
        startSlot: Int,
        renderItem: (item: T, slot: Int, index: Int) -> Unit
    ) {
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, items.size)
        
        for (i in startIndex until endIndex) {
            val slot = startSlot + (i - startIndex)
            renderItem(items[i], slot, i)
        }
    }

    protected fun handlePageWithSize(listSize: Int, itemsPerPage: Int) {
        val totalPages = calculateTotalPages(listSize, itemsPerPage)
        addPageButtons(totalPages)
    }

    protected fun calculateTotalPages(listSize: Int, itemsPerPage: Int): Int {
        return if (listSize == 0) 1 else (listSize + itemsPerPage - 1) / itemsPerPage
    }

    protected open fun calculateTotalPages(listSize: Int): Int {
        throw NotImplementedError("Subclass must override calculateTotalPages(Int) or use calculateTotalPages(Int, Int)")
    }

    protected abstract fun openNewPage(player: ServerPlayerEntity, newPage: Int)

    private fun addPageButtons(totalPages: Int) {
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