package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.inner_community.OnlinePlayerListMenu
import net.minecraft.server.level.ServerPlayer

fun runOpenInviteMemberMenu(
    player: ServerPlayer,
    community: Community,
    runBack: ((ServerPlayer) -> Unit)
) {
    if (!com.imyvm.community.application.interaction.common.validateInvitationSender(player, community)) {
        return
    }
    
    CommunityMenuOpener.open(player) { syncId ->
        OnlinePlayerListMenu.create(
            syncId = syncId,
            community = community,
            playerExecutor = player,
            page = 0,
            runBack = runBack
        )!!
    }
}
