package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class TreasuryGrantTargetListMenu(
    syncId: Int,
    private val sourceCommunity: Community,
    page: Int = 0,
    val runBack: (ServerPlayer) -> Unit,
    private val onCommunitySelected: (ServerPlayer, Community) -> Unit
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr(
        "ui.treasury_grant.target.title",
        sourceCommunity.generateCommunityMark()
    ) ?: Component.literal("Grant Coins: ${sourceCommunity.generateCommunityMark()} → Select Target"),
    page = page,
    runBack = runBack
) {
    private val communitiesPerPage = 26
    private val startSlot = 10

    init {
        val targetCommunities = CommunityDatabase.communities.filter {
            it.regionNumberId != sourceCommunity.regionNumberId && it.getRegion() != null
        }
        renderList(targetCommunities, communitiesPerPage, startSlot) { community, slot, _ ->
            addButton(
                slot = slot,
                name = community.generateCommunityMark(),
                itemStack = getPlayerHeadButtonItemStackCommunity(community)
            ) { player -> onCommunitySelected(player, community) }
        }
        handlePageWithSize(targetCommunities.size, communitiesPerPage)
    }

    override fun openNewPage(playerExecutor: ServerPlayer, newPage: Int) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            TreasuryGrantTargetListMenu(
                syncId = syncId,
                sourceCommunity = sourceCommunity,
                page = newPage,
                runBack = runBack,
                onCommunitySelected = onCommunitySelected
            )
        }
    }
}
