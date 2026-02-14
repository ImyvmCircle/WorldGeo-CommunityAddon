package com.imyvm.community.application.interaction.common

import com.imyvm.community.infra.CommunityDatabase
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ChatChannelManager {
    private val activeChannels: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap()

    fun setActiveChannel(playerUUID: UUID, communityRegionId: Int?) {
        if (communityRegionId == null) {
            activeChannels.remove(playerUUID)
        } else {
            activeChannels[playerUUID] = communityRegionId
        }
    }

    fun getActiveChannel(playerUUID: UUID): Int? {
        return activeChannels[playerUUID]
    }

    fun isChannelActive(playerUUID: UUID): Boolean {
        return activeChannels.containsKey(playerUUID)
    }

    fun clearChannel(playerUUID: UUID) {
        activeChannels.remove(playerUUID)
    }

    fun getActiveCommunity(playerUUID: UUID) = 
        activeChannels[playerUUID]?.let { CommunityDatabase.getCommunityById(it) }
}
