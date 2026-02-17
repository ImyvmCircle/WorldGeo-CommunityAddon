package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.model.Community
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.Region
import net.minecraft.server.network.ServerPlayerEntity

fun onCommunityRegionInteraction(
    player: ServerPlayerEntity,
    community: Community,
    onInteract: (ServerPlayerEntity, Community, Region) -> Int
): Int {
    val region = community.getRegion()
    return if (region == null) {
        player.sendMessage(Translator.tr("community.not_found.region", community.generateCommunityMark()))
        0
    } else {
        onInteract(player, community, region)
    }
}