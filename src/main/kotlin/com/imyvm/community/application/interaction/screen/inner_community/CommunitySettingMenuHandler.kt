package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityMemberListMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import net.minecraft.server.level.ServerPlayer

fun runOpenSettingRegional(playerExecutor: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = playerExecutor,
            community = community,
            geographicFunctionType = GeographicFunctionType.SETTING_ADJUSTMENT
        ) { runBackToSettingMenu(playerExecutor,community,runBackGrandfather) }
    }
}

fun runOpenSettingPlayerTargeted(playerExecutor: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(playerExecutor) {syncId ->
        CommunityMemberListMenu(syncId, community, playerExecutor) { runBackToSettingMenu(playerExecutor, community, runBackGrandfather) }
    }
}

private fun runBackToSettingMenu(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    runOpenSettingMenu(player, community, runBack)
}