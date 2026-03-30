package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.executeScopeModification
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoint.screen.outer_community.CommunityScopeSelectionMenu
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.HypotheticalShape
import net.minecraft.server.level.ServerPlayer

fun runModifyScope(player: ServerPlayer, runBack: (ServerPlayer) -> Unit) {
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    val hypotheticalShape = selectionState?.hypotheticalShape

    if (hypotheticalShape is HypotheticalShape.Normal) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("ui.territory.modify.busy_creating"))
        return
    }

    if (hypotheticalShape is HypotheticalShape.ModifyExisting) {
        val targetScope = hypotheticalShape.scope
        val community = findCommunityOwningScope(player, targetScope)
        if (community == null) {
            player.closeContainer()
            player.sendSystemMessage(Translator.tr("ui.territory.modify.scope_not_found"))
            return
        }
        val permResult = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
        if (permResult.isDenied()) {
            player.closeContainer()
            permResult.sendSuccess(player)
            return
        }
        runExecuteScopeModification(player, community, targetScope)
        return
    }

    runCommunitySelectionForScopeModify(player, runBack)
}

fun runAddScope(player: ServerPlayer, runBack: (ServerPlayer) -> Unit) {
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (selectionState?.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("ui.territory.add_scope.busy_modifying"))
        return
    }

    runCommunitySelectionForScopeAdd(player, runBack)
}

fun runAddScopeForCommunity(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (selectionState?.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("ui.territory.add_scope.busy_modifying"))
        return
    }
    openScopeCreationForCommunity(player, community, runBack)
}

fun runDeleteScopeForCommunity(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        {
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
            if (adminCheck.isDenied()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
        }
    ) {
        val region = community.getRegion()
        if (region == null) {
            player.closeContainer()
            player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
            return@executeWithPermission
        }
        if (region.geometryScope.size <= 1) {
            player.closeContainer()
            player.sendSystemMessage(Translator.tr("community.scope_delete.error.last_scope"))
            return@executeWithPermission
        }
        CommunityMenuOpener.open(player) { syncId ->
            CommunityRegionScopeMenu(
                syncId = syncId,
                playerExecutor = player,
                community = community,
                geographicFunctionType = GeographicFunctionType.SCOPE_DELETION,
                runBack = runBack
            )
        }
    }
}

fun runModifyScopeForCommunity(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (selectionState?.hypotheticalShape is HypotheticalShape.Normal) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("ui.territory.modify.busy_creating"))
        return
    }

    if (selectionState?.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        val targetScope = (selectionState.hypotheticalShape as HypotheticalShape.ModifyExisting).scope
        if (community.getRegion()?.geometryScope?.any { it.scopeName == targetScope.scopeName } == true) {
            runExecuteScopeModification(player, community, targetScope)
            return
        }
    }

    openScopeListForModify(player, community, runBack)
}

internal fun runExecuteScopeModification(
    player: ServerPlayer,
    community: Community,
    scope: GeoScope
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        {
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
            if (adminCheck.isDenied()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
        }
    ) {
        executeScopeModification(player, community, scope)
    }
}

private fun runCommunitySelectionForScopeModify(
    player: ServerPlayer,
    runBack: (ServerPlayer) -> Unit
) {
    val joinedCommunities = getJoinedCommunities(player)
    if (joinedCommunities.isEmpty()) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("ui.main.message.no_community"))
        return
    }
    if (joinedCommunities.size == 1) {
        openScopeListForModify(player, joinedCommunities.first(), runBack)
        return
    }
    val title = Translator.tr("ui.territory.modify.select_community") ?: net.minecraft.network.chat.Component.literal("Select Community to Modify")
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeSelectionMenu(
            syncId = syncId,
            communities = joinedCommunities,
            player = player,
            title = title,
            runBack = runBack
        ) { p, community ->
            openScopeListForModify(p, community, runBack)
        }
    }
}

private fun runCommunitySelectionForScopeAdd(
    player: ServerPlayer,
    runBack: (ServerPlayer) -> Unit
) {
    val joinedCommunities = getJoinedCommunities(player)
    if (joinedCommunities.isEmpty()) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("ui.main.message.no_community"))
        return
    }
    if (joinedCommunities.size == 1) {
        openScopeCreationForCommunity(player, joinedCommunities.first(), runBack)
        return
    }
    val title = Translator.tr("ui.territory.add_scope.select_community") ?: net.minecraft.network.chat.Component.literal("Select Community to Add Scope")
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeSelectionMenu(
            syncId = syncId,
            communities = joinedCommunities,
            player = player,
            title = title,
            runBack = runBack
        ) { p, community ->
            openScopeCreationForCommunity(p, community, runBack)
        }
    }
}

private fun openScopeListForModify(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        {
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
            if (adminCheck.isDenied()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityRegionScopeMenu(
                syncId = syncId,
                playerExecutor = player,
                community = community,
                geographicFunctionType = GeographicFunctionType.GEOMETRY_MODIFICATION,
                runBack = runBack
            )
        }
    }
}

private fun openScopeCreationForCommunity(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        {
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
            if (adminCheck.isDenied()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityScopeCreationMenu(syncId, community, playerExecutor = player, runBack = runBack)
        }
    }
}

private fun getJoinedCommunities(player: ServerPlayer): List<Community> =
    CommunityDatabase.communities.filter {
        val role = it.getMemberRole(player.uuid)
        role != null && role != MemberRoleType.APPLICANT && role != MemberRoleType.REFUSED
    }

private fun findCommunityOwningScope(player: ServerPlayer, scope: GeoScope): Community? =
    CommunityDatabase.communities.find { community ->
        val role = community.getMemberRole(player.uuid)
        role != null && role != MemberRoleType.APPLICANT && role != MemberRoleType.REFUSED &&
            community.getRegion()?.geometryScope?.any { it.scopeName == scope.scopeName } == true
    }
