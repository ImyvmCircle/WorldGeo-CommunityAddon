package com.imyvm.community.entrypoint.screen.inner_community.building

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingEditor
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingMenu
import com.imyvm.community.application.townbuilding.CommunityBuildingDraft
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer

class CommunityBuildingStyleListMenu(
    syncId: Int,
    private val playerExecutor: ServerPlayer,
    private val community: Community,
    private val adminMode: Boolean,
    page: Int,
    private val parentBack: (ServerPlayer) -> Unit
) : AbstractListMenu(
    syncId,
    Translator.tr("ui.community.building.list.title", community.generateCommunityMark()),
    page,
    runBack = { runOpenCommunityBuildingMenu(it, community, parentBack) }
) {
    init {
        val entries = community.buildingState.activeEntries().sortedBy { it.baseBlockId }
        renderList(entries, 28, 10) { entry, slot, _ ->
            addButton(
                slot,
                entry.baseBlockId,
                CommunityBuildingService.getBlockItem(entry.baseBlockId),
                CommunityBuildingService.buildSelectedLore(entry)
            ) { player ->
                if (adminMode) {
                    runOpenCommunityBuildingEditor(
                        player,
                        community,
                        CommunityBuildingDraft(entry.baseBlockId, entry.unitCost, entry.rewardPerBlock, entry.linkedBlockIds.toMutableList(), true),
                        parentBack
                    )
                } else {
                    CommunityBuildingService.sendEntryDetail(player, community, entry)
                }
            }
        }
        handlePageWithSize(entries.size, 28)
    }

    override fun openNewPage(player: ServerPlayer, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityBuildingStyleListMenu(syncId, player, community, adminMode, newPage, parentBack)
        }
    }
}
