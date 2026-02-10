package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.permission.PermissionCheck
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.GeographicFunctionType
import com.imyvm.community.domain.community.AdministrationPermission
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.entrypoints.screen.inner_community.CommunityAdministrationMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationAdvancementMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationAuditListMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationRenameMenuAnvil
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityMemberListMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import net.minecraft.server.network.ServerPlayerEntity

fun runAdmRenameCommunity(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit, voteCreationMode: Boolean = false){
    PermissionCheck.executeWithPermission(
        player,
        { 
            if (voteCreationMode) PermissionCheck.canAccessCouncil(player, community)
            else PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.RENAME_COMMUNITY) 
        }
    ) {
        AdministrationRenameMenuAnvil(player, community = community, runBackGrandfather).open()
    }
}

fun runAdmManageMembers(player: ServerPlayerEntity, community: Community, runBackGrandfather: ((ServerPlayerEntity) -> Unit), voteCreationMode: Boolean = false) {
    PermissionCheck.executeWithPermission(
        player,
        { 
            if (voteCreationMode) PermissionCheck.canAccessCouncil(player, community)
            else PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_MEMBERS) 
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityMemberListMenu(
                syncId = syncId,
                community = community,
                playerExecutor = player
            ) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather, voteCreationMode)}
        }
    }
}

fun runAdmAuditRequests(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit, voteCreationMode: Boolean = false) {
    PermissionCheck.executeWithPermission(
        player,
        { 
            if (voteCreationMode) PermissionCheck.canAccessCouncil(player, community)
            else PermissionCheck.canAuditApplications(player, community) 
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAuditListMenu(
                syncId = syncId,
                community = community,
                playerExecutor = player,
                page = 0
            ) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather, voteCreationMode) }
        }
    }
}

fun runAdmAdvancement(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit, voteCreationMode: Boolean = false){
    PermissionCheck.executeWithPermission(
        player,
        { 
            if (voteCreationMode) PermissionCheck.canAccessCouncil(player, community)
            else PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ADVANCEMENT) 
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAdvancementMenu(syncId, community, player, runBackGrandfather)
        }
    }
}

fun runAdmRegion(
    player: ServerPlayerEntity,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    runBackGrandfather: (ServerPlayerEntity) -> Unit,
    voteCreationMode: Boolean = false
) {
    val permission = when (geographicFunctionType) {
        GeographicFunctionType.GEOMETRY_MODIFICATION -> AdministrationPermission.MODIFY_REGION_GEOMETRY
        GeographicFunctionType.SETTING_ADJUSTMENT -> AdministrationPermission.MODIFY_REGION_SETTINGS
        GeographicFunctionType.TELEPORT_POINT_LOCATING -> AdministrationPermission.MANAGE_TELEPORT_POINTS
        else -> null
    }

    PermissionCheck.executeWithPermission(
        player,
        { 
            if (voteCreationMode) PermissionCheck.canAccessCouncil(player, community)
            else PermissionCheck.canExecuteAdministration(player, community, permission) 
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityRegionScopeMenu(
                syncId = syncId,
                playerExecutor = player,
                community = community,
                geographicFunctionType = geographicFunctionType
            ) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather, voteCreationMode) }
        }
    }
}

fun runAdmChangeJoinPolicy(player: ServerPlayerEntity, community: Community, policy: CommunityJoinPolicy, runBack: (ServerPlayerEntity) -> Unit, voteCreationMode: Boolean = false) {
    if (voteCreationMode) {
        val newPolicy = when (policy) {
            CommunityJoinPolicy.OPEN -> CommunityJoinPolicy.APPLICATION
            CommunityJoinPolicy.APPLICATION -> CommunityJoinPolicy.INVITE_ONLY
            CommunityJoinPolicy.INVITE_ONLY -> CommunityJoinPolicy.OPEN
        }
        com.imyvm.community.application.interaction.screen.inner_community.council.createChangeJoinPolicyVote(
            player, community, newPolicy, runBack
        )
    } else {
        PermissionCheck.executeWithPermission(
            player,
            { PermissionCheck.canChangeJoinPolicy(player, community) }
        ) {
            community.joinPolicy = when (policy) {
                CommunityJoinPolicy.OPEN -> CommunityJoinPolicy.APPLICATION
                CommunityJoinPolicy.APPLICATION -> CommunityJoinPolicy.INVITE_ONLY
                CommunityJoinPolicy.INVITE_ONLY -> CommunityJoinPolicy.OPEN
            }
            runBackToCommunityAdministrationMenu(player, community, runBack, voteCreationMode)
        }
    }
}

fun runAdmToggleCouncil(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit, voteCreationMode: Boolean = false) {
    PermissionCheck.executeWithPermission(
        player,
        { PermissionCheck.canToggleCouncil(player, community) }
    ) {
        community.council.enabled = !community.council.enabled
        runBackToCommunityAdministrationMenu(player, community, runBack, voteCreationMode)
    }
}

private fun runBackToCommunityAdministrationMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit, voteCreationMode: Boolean = false) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityAdministrationMenu(syncId, community, player, runBack, voteCreationMode)
    }
}