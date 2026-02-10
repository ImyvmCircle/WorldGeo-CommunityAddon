package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.GeographicFunctionType
import com.imyvm.community.entrypoints.screen.inner_community.*
import com.imyvm.community.entrypoints.screen.inner_community.CommunityAdministrationMenu
import com.imyvm.community.entrypoints.screen.inner_community.affairs.CommunitySettingMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityMemberListMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun runOpenOperationMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather : ((ServerPlayerEntity) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityAdministrationMenu(
            syncId, 
            community, 
            player, 
            runBack = {
                runBackToCommunityMenu(
                    player,
                    community,
                    runBackGrandfather
                )
            },
            voteCreationMode = false
        )
    }
}

fun runSendingCommunityDescription(player: ServerPlayerEntity, community: Community) {
    community.sendCommunityRegionDescription(player)
    player.closeHandledScreen()
}

fun runOpenMemberListMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather : ((ServerPlayerEntity) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMemberListMenu(syncId, community, player) { runBackToCommunityMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenSettingMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunitySettingMenu(syncId, player, community) {
            runBackToCommunityMenu(
                player,
                community,
                runBackGrandfather
            )
        }
    }
}

fun runTeleportCommunity(player: ServerPlayerEntity, community: Community) {
    player.closeHandledScreen()

    val region = community.getRegion()
    if (region == null) {
        player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_region"))
        return
    }

    val mainScope = region.geometryScope.firstOrNull()
    if (mainScope == null) {
        player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_scope"),)
        return
    }

    PlayerInteractionApi.teleportPlayerToScope(player, region, mainScope)
}

fun runTeleportToScope(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = player,
            community = community,
            geographicFunctionType = GeographicFunctionType.TELEPORT_POINT_EXECUTION,
        ) { runBackToCommunityMenu(player, community, runBackGrandfather) }
    }
}

private fun runBackToCommunityMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather : ((ServerPlayerEntity) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMenu(
            syncId = syncId,
            player = player,
            community = community,
            runBack = runBackGrandfather
        )
    }
}