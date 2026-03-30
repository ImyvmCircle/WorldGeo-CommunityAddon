package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.model.Community
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.Region
import net.minecraft.server.level.ServerPlayer

fun onCommunityRegionInteraction(
    player: ServerPlayer,
    community: Community,
    onInteract: (ServerPlayer, Community, Region) -> Int
): Int {
    val region = community.getRegion()
    return if (region == null) {
        player.sendSystemMessage(Translator.tr("community.not_found.region"))
        0
    } else {
        onInteract(player, community, region)
    }
}