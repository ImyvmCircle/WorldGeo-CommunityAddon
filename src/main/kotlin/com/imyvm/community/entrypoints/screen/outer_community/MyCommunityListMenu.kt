package com.imyvm.community.entrypoints.screen.outer_community

import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractCommunityListMenu
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity

class MyCommunityListMenu(
    syncId: Int,
    private val joinedCommunities: List<Community>,
    page: Int = 0,
    override val runBack: (ServerPlayerEntity) -> Unit
) : AbstractCommunityListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.my_communities.title"),
    page = page,
    runBack = runBack
) {

    init {
        addCommunityButtons()
        handlePage(getCommunities().size)
    }

    override fun createNewMenu(syncId: Int,newPage: Int): AbstractCommunityListMenu {
        return MyCommunityListMenu(syncId, joinedCommunities, newPage, runBack)
    }

    override fun getCommunities(): List<Community> {
        return joinedCommunities
    }
}