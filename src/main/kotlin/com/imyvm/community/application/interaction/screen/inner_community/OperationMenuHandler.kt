package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.GeographicFunctionType
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityMemberListMenu
import com.imyvm.community.entrypoints.screen.inner_community.CommunityOperationMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoints.screen.inner_community.operation_only.CommunityOperationAdvancementMenu
import com.imyvm.community.entrypoints.screen.inner_community.operation_only.CommunityOperationAuditListMenu
import com.imyvm.community.entrypoints.screen.inner_community.operation_only.CommunityOperationRenameMenuAnvil
import net.minecraft.server.network.ServerPlayerEntity

fun runOPRenameCommunity(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit){
    CommunityOperationRenameMenuAnvil(player, community = community, runBackGrandfather).open()
}

fun runOpManageMembers(player: ServerPlayerEntity, community: Community, runBackGrandfather: ((ServerPlayerEntity) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMemberListMenu(
            syncId = syncId,
            community = community,
            playerExecutor = player
        ) { runBackToCommunityOperationMenu(player, community, runBackGrandfather)}
    }
}

fun runOPAuditRequests(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityOperationAuditListMenu(
            syncId = syncId,
            community = community,
            playerExecutor = player,
            page = 0
        ) { runBackToCommunityOperationMenu(player, community, runBackGrandfather) }
    }
}

fun runOPAdvancement(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit){
    CommunityMenuOpener.open(player) { syncId ->
        CommunityOperationAdvancementMenu(syncId, community, player, runBackGrandfather)
    }
}

fun runOPRegion(
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
        ) { runBackToCommunityOperationMenu(player, community, runBackGrandfather) }
    }
}

fun runOPChangeJoinPolicy(player: ServerPlayerEntity, community: Community, policy: CommunityJoinPolicy, runBack: (ServerPlayerEntity) -> Unit) {
    community.joinPolicy = when (policy) {
        CommunityJoinPolicy.OPEN -> CommunityJoinPolicy.APPLICATION
        CommunityJoinPolicy.APPLICATION -> CommunityJoinPolicy.INVITE_ONLY
        CommunityJoinPolicy.INVITE_ONLY -> CommunityJoinPolicy.OPEN
    }
    runBackToCommunityOperationMenu(player, community, runBack)
}

private fun runBackToCommunityOperationMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityOperationMenu(syncId, community, player, runBack)
    }
}