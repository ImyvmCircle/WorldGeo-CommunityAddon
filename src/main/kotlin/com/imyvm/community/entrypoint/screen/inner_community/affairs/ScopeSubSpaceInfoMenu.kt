package com.imyvm.community.entrypoint.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.inner_community.affairs.AdministrativeAreaReadService
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenScopeSubSpaceInfoMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.WorldGeoSpaceSnapshot
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class ScopeSubSpaceInfoMenu(
    syncId: Int,
    private val player: ServerPlayer,
    private val community: Community,
    private val scopeSnapshot: WorldGeoSpaceSnapshot,
    private val page: Int = 0,
    private val runBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = Component.literal(scopeSnapshot.displayName),
    runBack = runBack
) {
    private val scopeDetailsView = AdministrativeAreaReadService.readScopeDetailsView(player, community, scopeSnapshot)
    private val subSpaceSlots = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40)
    private val pageSize = subSpaceSlots.size

    init {
        addScopeSummary()
        addSubSpaceEntries()
        addPageControls()
    }

    private fun addScopeSummary() {
        val scopeView = scopeDetailsView?.scope
        val snapshot = scopeView?.snapshot ?: scopeSnapshot
        addButton(
            slot = 10,
            itemStack = getLoreButton(ItemStack(Items.RECOVERY_COMPASS), buildSpaceLore(snapshot, scopeView?.dominantBiomeDisplay)),
            name = snapshot.displayName
        ) {}
    }

    private fun addSubSpaceEntries() {
        val subSpaceEntries = scopeDetailsView?.subSpaces ?: emptyList()
        if (subSpaceEntries.isEmpty()) {
            addButton(
                slot = 31,
                itemStack = getLoreButton(
                    ItemStack(Items.BARRIER),
                    listOf(Translator.tr("ui.community.space.empty.subspaces") ?: Component.literal("暂无子空间"))
                ),
                name = Translator.tr("ui.community.space.empty.subspaces")?.string ?: "暂无子空间"
            ) {}
            return
        }

        val start = page * pageSize
        val current = subSpaceEntries.drop(start).take(pageSize)
        current.forEachIndexed { index, entry ->
            addButton(
                slot = subSpaceSlots[index],
                itemStack = getLoreButton(ItemStack(Items.CHEST), buildSpaceLore(entry.snapshot, entry.dominantBiomeDisplay)),
                name = entry.snapshot.displayName
            ) {}
        }
    }

    private fun addPageControls() {
        val subSpaceEntries = scopeDetailsView?.subSpaces ?: emptyList()
        val totalPages = if (subSpaceEntries.isEmpty()) 1 else (subSpaceEntries.size + pageSize - 1) / pageSize
        val scopeView = scopeDetailsView?.scope
        val snapshot = scopeView?.snapshot ?: scopeSnapshot
        if (page > 0) {
            addButton(0, Translator.tr("ui.common.button.previous")?.string ?: "上一页", Items.ARROW) {
                runOpenScopeSubSpaceInfoMenu(it, community, snapshot, page - 1, runBack)
            }
        }
        if (page < totalPages - 1) {
            addButton(8, Translator.tr("ui.common.button.next")?.string ?: "下一页", Items.ARROW) {
                runOpenScopeSubSpaceInfoMenu(it, community, snapshot, page + 1, runBack)
            }
        }
    }
}
