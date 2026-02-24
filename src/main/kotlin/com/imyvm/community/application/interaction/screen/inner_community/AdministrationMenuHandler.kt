package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.policy.permission.PermissionResult
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.entrypoint.screen.inner_community.CommunityAdministrationMenu
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationAdvancementMenu
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationAuditListMenu
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationRenameMenuAnvil
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityMemberListMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import net.minecraft.server.network.ServerPlayerEntity

fun runAdmRenameCommunity(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit){
    CommunityPermissionPolicy.executeWithPermission(
        player,
        {
            val check = CommunityPermissionPolicy.canRenameCommunity(player, community)
            if (!check.isAllowed()) return@executeWithPermission check
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.RENAME_COMMUNITY)
        }
    ) {
        AdministrationRenameMenuAnvil(player, community = community, runBackGrandfather = { p -> runBackToCommunityAdministrationMenu(p, community, runBackGrandfather) }).open()
    }
}

fun runAdmManageMembers(player: ServerPlayerEntity, community: Community, runBackGrandfather: ((ServerPlayerEntity) -> Unit)) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_MEMBERS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_MEMBERS)
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityMemberListMenu(
                syncId = syncId,
                community = community,
                playerExecutor = player
            ) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather)}
        }
    }
}

fun runAdmAuditRequests(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canAuditApplications(player, community) }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAuditListMenu(
                syncId = syncId,
                community = community,
                playerExecutor = player,
                page = 0
            ) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather) }
        }
    }
}

fun runAdmAdvancement(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit){
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_ADVANCEMENT)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_ADVANCEMENT)
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAdvancementMenu(syncId, community, player) { runBackToCommunityAdministrationMenu(player, community, runBackGrandfather) }
        }
    }
}

fun runAdmRegion(
    player: ServerPlayerEntity,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    runBackGrandfather: (ServerPlayerEntity) -> Unit
) {
    val permission = when (geographicFunctionType) {
        GeographicFunctionType.GEOMETRY_MODIFICATION -> AdminPrivilege.MODIFY_REGION_GEOMETRY
        GeographicFunctionType.SETTING_ADJUSTMENT -> AdminPrivilege.MODIFY_REGION_SETTINGS
        GeographicFunctionType.TELEPORT_POINT_LOCATING -> AdminPrivilege.MANAGE_TELEPORT_POINTS
        else -> null
    }

    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, permission)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            if (permission != null) {
                CommunityPermissionPolicy.canExecuteOperationInProto(player, community, permission)
            } else {
                PermissionResult.Allowed
            }
        }
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
}

fun runAdmChangeJoinPolicy(player: ServerPlayerEntity, community: Community, policy: CommunityJoinPolicy, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canChangeJoinPolicy(player, community) }
    ) {
        val oldPolicy = community.joinPolicy
        community.joinPolicy = when (policy) {
            CommunityJoinPolicy.OPEN -> CommunityJoinPolicy.APPLICATION
            CommunityJoinPolicy.APPLICATION -> CommunityJoinPolicy.INVITE_ONLY
            CommunityJoinPolicy.INVITE_ONLY -> CommunityJoinPolicy.OPEN
        }
        
        val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
        val notification = com.imyvm.community.util.Translator.tr(
            "community.notification.join_policy_changed",
            oldPolicy.name,
            community.joinPolicy.name,
            player.name.string,
            communityName
        ) ?: net.minecraft.text.Text.literal("Join policy changed from ${oldPolicy.name} to ${community.joinPolicy.name} in $communityName by ${player.name.string}")
        com.imyvm.community.application.interaction.common.notifyOfficials(community, player.server, notification, player)
        
        com.imyvm.community.infra.CommunityDatabase.save()
        runBackToCommunityAdministrationMenu(player, community, runBack)
    }
}

fun runBackToCommunityAdministrationMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityAdministrationMenu(syncId, community, player, runBack)
    }
}