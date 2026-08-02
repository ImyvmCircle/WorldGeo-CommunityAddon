package com.imyvm.community.entrypoint.screen.inner_community.building

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingPoolMenu
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.util.Translator
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

class CommunityBuildingPoolMenu(
    syncId: Int,
    private val player: ServerPlayer,
    page: Int,
    private val runBack: (ServerPlayer) -> Unit
) : AbstractListMenu(
    syncId,
    Translator.tr("ui.community.building.pool.title"),
    page,
    runBack = runBack
) {
    init {
        val entries = CommunityBuildingService.getSelectablePool()
        renderList(entries, 28, 10) { entry, slot, _ ->
            addButton(
                slot,
                entry.baseBlockId,
                CommunityBuildingService.getBlockItem(entry.baseBlockId),
                CommunityBuildingService.buildPoolLore(entry)
            ) { p ->
                p.closeContainer()
                CommunityBuildingService.sendPoolEntryDetail(p, entry)
            }
        }
        handlePageWithSize(entries.size, 28)
        addButton(45, Translator.tr("ui.community.building.pool.button.add_hint").string, Items.WRITABLE_BOOK) { p ->
            p.closeContainer()
            sendBuildingPoolAddAction(p)
        }
        addButton(46, Translator.tr("ui.community.building.pool.button.remove_first").string, Items.BARRIER) { p ->
            val first = entries.getOrNull(page * 28) ?: return@addButton
            val result = CommunityBuildingService.removeSelectableEntry(first.baseBlockId)
            p.sendSystemMessage(
                if (result.isSuccess) Translator.tr("command.community.building.pool.remove.success", first.baseBlockId)
                else Translator.tr("command.community.building.pool.remove.failed", first.baseBlockId, result.exceptionOrNull()?.message ?: "error")
            )
            runOpenCommunityBuildingPoolMenu(p, page, runBack)
        }
    }

    private fun sendBuildingPoolAddAction(player: ServerPlayer) {
        val command = "/community building pool add <blockId> <capacityCost> <reward> [linkedBlockId,linkedBlockId]"
        val button = Translator.tr("command.community.building.pool.add.button").copy().withStyle { style ->
            style.withClickEvent(ClickEvent.SuggestCommand(command))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("command.community.building.pool.add.hover")))
        }
        player.sendSystemMessage(Component.empty().append(Translator.tr("command.community.building.pool.add.usage")).append(button))
    }

    override fun openNewPage(player: ServerPlayer, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId -> CommunityBuildingPoolMenu(syncId, player, newPage, runBack) }
    }
}
