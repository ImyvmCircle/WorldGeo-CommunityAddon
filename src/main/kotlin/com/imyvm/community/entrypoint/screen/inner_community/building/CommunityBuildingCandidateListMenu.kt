package com.imyvm.community.entrypoint.screen.inner_community.building

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingEditor
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingMenu
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer

class CommunityBuildingCandidateListMenu(
    syncId: Int,
    private val playerExecutor: ServerPlayer,
    private val community: Community,
    page: Int,
    private val parentBack: (ServerPlayer) -> Unit
) : AbstractListMenu(
    syncId,
    Translator.tr("ui.community.building.candidate.title", community.generateCommunityMark()),
    page,
    runBack = { runOpenCommunityBuildingMenu(it, community, parentBack) }
) {
    init {
        val candidates = CommunityBuildingService.getSelectablePool()
        renderList(candidates, 28, 10) { entry, slot, _ ->
            addButton(
                slot,
                entry.baseBlockId,
                CommunityBuildingService.getBlockItem(entry.baseBlockId),
                CommunityBuildingService.buildCatalogLore(community, entry)
            ) { player ->
                val draft = CommunityBuildingService.createDraftFromSelectable(entry.baseBlockId) ?: return@addButton
                runOpenCommunityBuildingEditor(player, community, draft, parentBack)
            }
        }
        handlePageWithSize(candidates.size, 28)
    }

    override fun openNewPage(player: ServerPlayer, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityBuildingCandidateListMenu(syncId, player, community, newPage, parentBack)
        }
    }
}
