package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityScopeSelectionMenu(
    syncId: Int,
    private val communities: List<Community>,
    private val player: ServerPlayerEntity,
    title: Text,
    page: Int = 0,
    val runBack: (ServerPlayerEntity) -> Unit,
    private val onCommunitySelected: (ServerPlayerEntity, Community) -> Unit
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = title,
    page = page,
    runBack = runBack
) {
    private val communitiesPerPage = 26
    private val startSlot = 10

    init {
        renderList(communities, communitiesPerPage, startSlot) { community, slot, _ ->
            addButton(
                slot = slot,
                name = community.generateCommunityMark(),
                itemStack = getPlayerHeadButtonItemStackCommunity(community)
            ) { p -> onCommunitySelected(p, community) }
        }
        handlePageWithSize(communities.size, communitiesPerPage)
    }

    override fun openNewPage(playerExecutor: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            CommunityScopeSelectionMenu(
                syncId = syncId,
                communities = communities,
                player = player,
                title = menuTitle ?: Text.literal("Select Community"),
                page = newPage,
                runBack = runBack,
                onCommunitySelected = onCommunitySelected
            )
        }
    }
}
