package com.imyvm.community.entrypoints.screen

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.entrypoints.screen.inner_community.CommunityMenu
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

abstract class AbstractCommunityListMenu(
    syncId: Int,
    menuTitle: Text?,
    page: Int = 0,
    open val runBack: (ServerPlayerEntity) -> Unit
) : AbstractListMenu(
    syncId,
    menuTitle = menuTitle,
    page = page,
    runBack = runBack
) {

    private val communitiesPerPage = 26
    private val startSlot = 10
    private val endSlot = 35

    protected abstract fun getCommunities(): List<Community>

    fun addCommunityButtons() {
        val communityList = getCommunities().drop(page * communitiesPerPage).take(communitiesPerPage)
        var slot = startSlot

        for (community in communityList) {
            if (!addPlayerHeadButton(slot, community) { player -> onCommunityButtonClick(player, community, runBack) }) continue

            slot = super.incrementSlotIndex(slot)
            if (slot > endSlot) break
        }
    }

    protected open fun onCommunityButtonClick(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityMenu(
                syncId = syncId,
                player = player,
                community = community,
                runBack = runBack
            )
        }
    }


    override fun calculateTotalPages(listSize: Int): Int {
        return ((listSize + communitiesPerPage - 1) / communitiesPerPage)
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId -> createNewMenu(syncId, newPage) }
    }

    protected abstract fun createNewMenu(syncId: Int, newPage: Int): AbstractListMenu

    private fun addPlayerHeadButton(slot: Int, community: Community, onClick: (ServerPlayerEntity) -> Unit): Boolean {
        addButton(
            slot = slot,
            name = community.generateCommunityMark(),
            itemStack = getPlayerHeadButtonItemStackCommunity(community), onClick = onClick
        )
        return true
    }
}

