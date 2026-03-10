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
import net.minecraft.server.network.ServerPlayerEntity

fun runModifyScope(player: ServerPlayerEntity, runBack: (ServerPlayerEntity) -> Unit) {
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    val hypotheticalShape = selectionState?.hypotheticalShape

    if (hypotheticalShape is HypotheticalShape.Normal) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("ui.territory.modify.busy_creating"))
        return
    }

    if (hypotheticalShape is HypotheticalShape.ModifyExisting) {
        val targetScope = hypotheticalShape.scope
        val community = findCommunityOwningScope(player, targetScope)
        if (community == null) {
            player.closeHandledScreen()
            player.sendMessage(Translator.tr("ui.territory.modify.scope_not_found"))
            return
        }
        val permResult = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
        if (permResult.isDenied()) {
            player.closeHandledScreen()
            permResult.sendFeedback(player)
            return
        }
        runExecuteScopeModification(player, community, targetScope)
        return
    }

    runCommunitySelectionForScopeModify(player, runBack)
}

fun runAddScope(player: ServerPlayerEntity, runBack: (ServerPlayerEntity) -> Unit) {
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (selectionState?.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("ui.territory.add_scope.busy_modifying"))
        return
    }

    runCommunitySelectionForScopeAdd(player, runBack)
}

fun runAddScopeForCommunity(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (selectionState?.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("ui.territory.add_scope.busy_modifying"))
        return
    }
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeCreationMenu(syncId, community, playerExecutor = player, runBack = runBack)
    }
}

fun runModifyScopeForCommunity(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (selectionState?.hypotheticalShape is HypotheticalShape.Normal) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("ui.territory.modify.busy_creating"))
        return
    }

    if (selectionState?.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        val targetScope = (selectionState.hypotheticalShape as HypotheticalShape.ModifyExisting).scope
        if (community.getRegion()?.geometryScope?.any { it.scopeName == targetScope.scopeName } == true) {
            runExecuteScopeModification(player, community, targetScope)
            return
        }
    }

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

internal fun runExecuteScopeModification(
    player: ServerPlayerEntity,
    community: Community,
    scope: GeoScope
) {
    val permResult = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
    if (permResult.isDenied()) {
        player.closeHandledScreen()
        permResult.sendFeedback(player)
        return
    }
    executeScopeModification(player, community, scope)
}

private fun runCommunitySelectionForScopeModify(
    player: ServerPlayerEntity,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val joinedCommunities = getJoinedCommunities(player)
    if (joinedCommunities.isEmpty()) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("ui.main.message.no_community"))
        return
    }
    if (joinedCommunities.size == 1) {
        openScopeListForModify(player, joinedCommunities.first(), runBack)
        return
    }
    val title = Translator.tr("ui.territory.modify.select_community") ?: net.minecraft.text.Text.literal("Select Community to Modify")
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeSelectionMenu(
            syncId = syncId,
            communities = joinedCommunities,
            player = player,
            title = title,
            runBack = runBack
        ) { p, community ->
            val permResult = CommunityPermissionPolicy.canExecuteAdministration(p, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
            if (permResult.isDenied()) {
                p.closeHandledScreen()
                permResult.sendFeedback(p)
            } else {
                openScopeListForModify(p, community, runBack)
            }
        }
    }
}

private fun runCommunitySelectionForScopeAdd(
    player: ServerPlayerEntity,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val joinedCommunities = getJoinedCommunities(player)
    if (joinedCommunities.isEmpty()) {
        player.closeHandledScreen()
        player.sendMessage(Translator.tr("ui.main.message.no_community"))
        return
    }
    if (joinedCommunities.size == 1) {
        openScopeCreationForCommunity(player, joinedCommunities.first(), runBack)
        return
    }
    val title = Translator.tr("ui.territory.add_scope.select_community") ?: net.minecraft.text.Text.literal("Select Community to Add Scope")
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeSelectionMenu(
            syncId = syncId,
            communities = joinedCommunities,
            player = player,
            title = title,
            runBack = runBack
        ) { p, community ->
            val permResult = CommunityPermissionPolicy.canExecuteAdministration(p, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
            if (permResult.isDenied()) {
                p.closeHandledScreen()
                permResult.sendFeedback(p)
            } else {
                openScopeCreationForCommunity(p, community, runBack)
            }
        }
    }
}

private fun openScopeListForModify(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val permResult = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
    if (permResult.isDenied()) {
        player.closeHandledScreen()
        permResult.sendFeedback(player)
        return
    }
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

private fun openScopeCreationForCommunity(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeCreationMenu(syncId, community, playerExecutor = player, runBack = runBack)
    }
}

private fun getJoinedCommunities(player: ServerPlayerEntity): List<Community> =
    CommunityDatabase.communities.filter {
        val role = it.getMemberRole(player.uuid)
        role != null && role != MemberRoleType.APPLICANT && role != MemberRoleType.REFUSED
    }

private fun findCommunityOwningScope(player: ServerPlayerEntity, scope: GeoScope): Community? =
    CommunityDatabase.communities.find { community ->
        val role = community.getMemberRole(player.uuid)
        role != null && role != MemberRoleType.APPLICANT && role != MemberRoleType.REFUSED &&
            community.getRegion()?.geometryScope?.any { it.scopeName == scope.scopeName } == true
    }
