package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.runTeleportCommunity
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.GeographicFunctionType
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.element.TargetSettingMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationTeleportPointMenu
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity

fun runExecuteRegion(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    playerObject: GameProfile? = null,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    if (geographicFunctionType == GeographicFunctionType.SETTING_ADJUSTMENT) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            TargetSettingMenu(
                syncId = syncId,
                playerExecutor = playerExecutor,
                community = community,
                playerObject = playerObject
            ) { runBackRegionScopeMenu(playerExecutor, community, geographicFunctionType, runBackGrandfatherMenu) }
        }
    } else if (geographicFunctionType == GeographicFunctionType.TELEPORT_POINT_EXECUTION) {
        runTeleportCommunity(playerExecutor, community)
    }
}

fun runExecuteScope(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    scope: GeoScope,
    geographicFunctionType: GeographicFunctionType,
    playerObject: GameProfile? = null,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    when (geographicFunctionType){
        GeographicFunctionType.GEOMETRY_MODIFICATION -> {
            val communityRegion = community.getRegion()
            communityRegion?.let { PlayerInteractionApi.modifyScope(playerExecutor, it, scope.scopeName) }
            playerExecutor.closeHandledScreen()
        }
        GeographicFunctionType.SETTING_ADJUSTMENT -> {
            CommunityMenuOpener.open(playerExecutor) { syncId ->
                TargetSettingMenu(
                    syncId = syncId,
                    playerExecutor = playerExecutor,
                    community = community,
                    scope = scope,
                    playerObject = playerObject
                ) { runBackRegionScopeMenu(playerExecutor, community, geographicFunctionType, runBackGrandfatherMenu) }
            }
        }
        GeographicFunctionType.TELEPORT_POINT_LOCATING -> {
            CommunityMenuOpener.open(playerExecutor) { syncId ->
                AdministrationTeleportPointMenu(
                    syncId = syncId,
                    playerExecutor = playerExecutor,
                    community = community,
                    scope = scope
                ) { runBackRegionScopeMenu(playerExecutor, community, geographicFunctionType, runBackGrandfatherMenu) }
            }
        }
        GeographicFunctionType.TELEPORT_POINT_EXECUTION -> {
            val communityRegion = community.getRegion()
            communityRegion?.let { PlayerInteractionApi.teleportPlayerToScope(playerExecutor, it, scope) }
            playerExecutor.closeHandledScreen()
        }
    }
}

private fun runBackRegionScopeMenu(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = playerExecutor,
            community = community,
            geographicFunctionType = geographicFunctionType,
            runBack = runBack
        )
    }
}