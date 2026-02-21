package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.entrypoint.screen.outer_community.CommunityListMenu
import net.minecraft.server.network.ServerPlayerEntity

fun runSwitchFilterMode(
    player: ServerPlayerEntity,
    mode: CommunityListFilterType,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityListMenu(
            syncId = syncId,
            mode = mode,
            runBack = runBack
        )
    }
}