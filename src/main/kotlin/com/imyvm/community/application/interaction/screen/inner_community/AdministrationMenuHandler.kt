package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.GeographicFunctionType
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.entrypoints.screen.inner_community.CommunityAdministrationMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationAdvancementMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationAuditListMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationRenameMenuAnvil
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityMemberListMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import net.minecraft.server.network.ServerPlayerEntity

fun runAdmRenameCommunity(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit){
    AdministrationRenameMenuAnvil(player, community = community, runBackGrandfather).open()
}

fun runAdmManageMembers(player: ServerPlayerEntity, community: Community, runBackGrandfather: ((ServerPlayerEntity) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMemberListMenu(
            syncId = syncId,
            community = community,
            playerExecutor = player
        ) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather)}
    }
}

fun runAdmAuditRequests(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        AdministrationAuditListMenu(
            syncId = syncId,
            community = community,
            playerExecutor = player,
            page = 0
        ) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather) }
    }
}

fun runAdmAdvancement(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit){
    CommunityMenuOpener.open(player) { syncId ->
        AdministrationAdvancementMenu(syncId, community, player, runBackGrandfather)
    }
}

fun runAdmRegion(
    player: ServerPlayerEntity,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    runBackGrandfather: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = player,
            community = community,
            geographicFunctionType = geographicFunctionType
        ) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather) }
    }
}

fun runAdmChangeJoinPolicy(player: ServerPlayerEntity, community: Community, policy: CommunityJoinPolicy, runBack: (ServerPlayerEntity) -> Unit) {
    community.joinPolicy = when (policy) {
        CommunityJoinPolicy.OPEN -> CommunityJoinPolicy.APPLICATION
        CommunityJoinPolicy.APPLICATION -> CommunityJoinPolicy.INVITE_ONLY
        CommunityJoinPolicy.INVITE_ONLY -> CommunityJoinPolicy.OPEN
    }
    runBackToCommunityAdministrationMenu(player, community, runBack)
}

private fun runBackToCommunityAdministrationMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityAdministrationMenu(syncId, community, player, runBack)
    }
}