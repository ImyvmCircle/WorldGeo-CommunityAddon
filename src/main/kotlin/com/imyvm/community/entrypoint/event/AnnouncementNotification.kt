package com.imyvm.community.entrypoint.event

import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerPlayer

fun registerAnnouncementNotification() {
    ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        notifyPlayerOfUnreadAnnouncements(handler.player)
    }
}

private fun notifyPlayerOfUnreadAnnouncements(player: ServerPlayer) {
    val playerUuid = player.uuid
    var totalUnreadCount = 0
    
    for (community in CommunityDatabase.communities) {
        if (!community.member.containsKey(playerUuid)) continue
        
        val unreadAnnouncements = community.getUnreadAnnouncementsFor(playerUuid)
        if (unreadAnnouncements.isEmpty()) continue
        
        totalUnreadCount += unreadAnnouncements.size
        
        val communityName = community.getRegion()?.name ?: "Community#${community.regionNumberId}"
        player.sendSystemMessage(
            Translator.tr(
                "announcement.notification.unread.community",
                communityName,
                unreadAnnouncements.size
            )
        )
    }
    
    if (totalUnreadCount > 0) {
        player.sendSystemMessage(
            Translator.tr(
                "announcement.notification.unread.total",
                totalUnreadCount
            )
        )
    }
}
