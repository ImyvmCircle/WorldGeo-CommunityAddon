package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.entrypoint.screen.inner_community.CommunityMenu
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class MyCommunityListMenu(
    syncId: Int,
    private val joinedCommunities: List<Community>,
    page: Int = 0,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.my_communities.title") ?: Text.literal("My Communities"),
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