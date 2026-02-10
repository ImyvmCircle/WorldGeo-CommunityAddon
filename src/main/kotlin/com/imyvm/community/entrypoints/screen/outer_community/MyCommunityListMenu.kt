package com.imyvm.community.entrypoints.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.entrypoints.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.entrypoints.screen.inner_community.CommunityMenu
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity

class MyCommunityListMenu(
    syncId: Int,
    private val joinedCommunities: List<Community>,
    page: Int = 0,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.my_communities.title"),
    page = page,
    runBack = runBack
) {

    private val communitiesPerPage = 26
    private val startSlot = 10

    init {
        renderList(joinedCommunities, communitiesPerPage, startSlot) { community, slot, _ ->
            addButton(
                slot = slot,
                name = community.generateCommunityMark(),
                itemStack = getPlayerHeadButtonItemStackCommunity(community)
            ) { player ->
                CommunityMenuOpener.open(player) { newSyncId ->
                    CommunityMenu(
                        syncId = newSyncId,
                        player = player,
                        community = community,
                        runBack = runBack
                    )
                }
            }
        }
        handlePageWithSize(joinedCommunities.size, communitiesPerPage)
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            MyCommunityListMenu(syncId, joinedCommunities, newPage, runBack)
        }
    }
}