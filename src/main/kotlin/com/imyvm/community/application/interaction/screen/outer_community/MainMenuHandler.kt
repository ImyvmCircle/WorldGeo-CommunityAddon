package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.entrypoint.screen.inner_community.CommunityMenu
import com.imyvm.community.entrypoint.screen.outer_community.CommunityListMenu
import com.imyvm.community.entrypoint.screen.outer_community.MainMenu
import com.imyvm.community.entrypoint.screen.outer_community.MyCommunityListMenu
import com.imyvm.community.entrypoint.screen.outer_community.TerritoryMenu
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getColoredDimensionName
import com.imyvm.community.util.getPlayerDimensionId
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.onToggleActionBar
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.level.ServerPlayer

fun runList(player: ServerPlayer) {
    val mode = CommunityListFilterType.JOIN_ABLE
    CommunityMenuOpener.open(player) { syncId ->
        CommunityListMenu(
            syncId = syncId,
            mode = mode,
            runBack = { runBackOrRefreshMainMenu(it) }
        )
    }
}

fun runGeoOperation(player: ServerPlayer) {
    CommunityMenuOpener.open(player) { syncId ->
        TerritoryMenu(syncId, player, runBack = { runBackOrRefreshMainMenu(it) })
    }
}

fun runMyCommunity(player: ServerPlayer) {
    val joinedCommunities = CommunityDatabase.communities.filter {
        it.member.any { m -> m.key == player.uuid &&
                it.getMemberRole(m.key) != MemberRoleType.APPLICANT &&
                it.getMemberRole(m.key) != MemberRoleType.REFUSED }
    }

    when {
        joinedCommunities.isEmpty() -> {
            player.sendSystemMessage(Translator.tr("ui.main.message.no_community"))
            player.closeContainer()
        }

        joinedCommunities.size == 1 -> {
            val community = joinedCommunities.first()
            CommunityMenuOpener.open(player) { syncId ->
                CommunityMenu(
                    syncId = syncId,
                    player = player,
                    community = community,
                    runBack = { runBackOrRefreshMainMenu(it) }
                )
            }
        }

        else -> {
            val joinedCommunities: List<Community> = joinedCommunities.toList()
            CommunityMenuOpener.open(player) { syncId ->
                MyCommunityListMenu(
                    syncId = syncId,
                    joinedCommunities = joinedCommunities,
                    runBack = { runBackOrRefreshMainMenu(it) }
                )
            }
        }
    }
}

fun runToggleSelectionMode(player: ServerPlayer) {
    val isSelectionModeEnabled = ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)
    if (isSelectionModeEnabled) {
        PlayerInteractionApi.stopSelection(player)
        player.sendSystemMessage(Translator.tr("community.selection_mode.disabled"))
    } else {
        PlayerInteractionApi.startSelection(player)
        player.sendSystemMessage(Translator.tr("community.selection_mode.enabled"))
        player.sendSystemMessage(Translator.tr("community.selection_mode.dimension_hint", getColoredDimensionName(getPlayerDimensionId(player))))
    }
    runBackOrRefreshMainMenu(player)
}

fun runResetSelection(player: ServerPlayer) {
    PlayerInteractionApi.resetSelection(player)
    player.sendSystemMessage(Translator.tr("community.selection_mode.reset"))
    runBackOrRefreshMainMenu(player)
}

@Deprecated("Temporary function call inside before the function being added to PlayerInteractionApi",
    replaceWith = ReplaceWith("PlayerInteractionApi.toggleActionBar(player)"))
fun runToggleActionBar(player: ServerPlayer) {
    onToggleActionBar(player)
    runBackOrRefreshMainMenu(player)
}

internal fun runBackOrRefreshMainMenu(player: ServerPlayer) {
    CommunityMenuOpener.open(player) { syncId ->
        MainMenu(
            syncId = syncId,
            playerExecutor = player
        )
    }
}
