package com.imyvm.community.entrypoint.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoScope
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ScopeTransferTargetListMenu(
    syncId: Int,
    private val sourceCommunity: Community,
    private val scope: GeoScope,
    page: Int = 0,
    val runBack: (ServerPlayerEntity) -> Unit,
    private val onCommunitySelected: (ServerPlayerEntity, Community) -> Unit
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr(
        "ui.scope_transfer.target.title",
        sourceCommunity.generateCommunityMark(),
        scope.scopeName
    ) ?: Text.literal("Transfer: ${scope.scopeName} → Select Community"),
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

    override fun openNewPage(playerExecutor: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            ScopeTransferTargetListMenu(
                syncId = syncId,
                sourceCommunity = sourceCommunity,
                scope = scope,
                page = newPage,
                runBack = runBack,
                onCommunitySelected = onCommunitySelected
            )
        }
    }
}
