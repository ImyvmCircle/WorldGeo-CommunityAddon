package com.imyvm.community.entrypoints.command.helper

import com.imyvm.community.domain.Community
import com.imyvm.community.infra.CommunityDatabase.communities
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.RegionDataApi.getRegionList
import net.minecraft.server.network.ServerPlayerEntity

fun identifierHandler(
    player: ServerPlayerEntity,
    communityIdentifier: String,
    onCommunityFound: (community: Community) -> Unit
) : Int {
    val targetCommunity = if (communityIdentifier.matches("\\d+".toRegex())) {
        val id = communityIdentifier.toInt()
        getCommunityById(id)
    } else {
        getCommunityByName(communityIdentifier)
    }

    return if (targetCommunity != null) {
        onCommunityFound(targetCommunity)
        1
    } else {
        if (communityIdentifier.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("community.notfound.id", communityIdentifier))
        } else {
            player.sendMessage(Translator.tr("community.notfound.name", communityIdentifier))
        }
        0
    }
}

private fun getCommunityById(id: Int): Community? {
    val targetCommunity = communities.find {
        it.regionNumberId == id
    } ?: return null

    return targetCommunity
}

private fun getCommunityByName(name: String): Community? {
    val targetRegion = getRegionList().find {
        it.name.equals(name, ignoreCase = true)
    } ?: return null
    val targetCommunity = communities.find {
        it.regionNumberId == targetRegion.numberID
    } ?: return null

    return targetCommunity
}
