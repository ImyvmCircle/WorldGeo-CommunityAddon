package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.inner_community.affairs.CommunitySpaceInfoMenu
import com.imyvm.community.entrypoint.screen.inner_community.affairs.ScopeSubSpaceInfoMenu
import com.imyvm.iwg.domain.WorldGeoSpaceSnapshot
import net.minecraft.server.level.ServerPlayer

@Deprecated("Temporary V4 composition while waiting for ImyvmWorldGeoApi administrative-area read APIs.")
fun runOpenCommunitySpaceInfoMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    runOpenCommunitySpaceInfoMenu(player, community, 0, runBackGrandfather)
}

@Deprecated("Temporary V4 composition while waiting for ImyvmWorldGeoApi administrative-area read APIs.")
fun runOpenCommunitySpaceInfoMenu(player: ServerPlayer, community: Community, page: Int = 0, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunitySpaceInfoMenu(syncId, player, community, page) { runBackGrandfather(it) }
    }
}

@Deprecated("Temporary V4 composition while waiting for ImyvmWorldGeoApi administrative-area read APIs.")
fun runOpenScopeSubSpaceInfoMenu(player: ServerPlayer, community: Community, scopeSnapshot: WorldGeoSpaceSnapshot, runBack: (ServerPlayer) -> Unit) {
    runOpenScopeSubSpaceInfoMenu(player, community, scopeSnapshot, 0, runBack)
}

@Deprecated("Temporary V4 composition while waiting for ImyvmWorldGeoApi administrative-area read APIs.")
fun runOpenScopeSubSpaceInfoMenu(player: ServerPlayer, community: Community, scopeSnapshot: WorldGeoSpaceSnapshot, page: Int, runBack: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        ScopeSubSpaceInfoMenu(syncId, player, community, scopeSnapshot, page, runBack)
    }
}
