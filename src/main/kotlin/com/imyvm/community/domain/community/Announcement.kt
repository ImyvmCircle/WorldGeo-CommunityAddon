package com.imyvm.community.domain.community

import net.minecraft.text.Text
import java.util.*

data class Announcement(
    val id: UUID = UUID.randomUUID(),
    val content: Text,
    val authorUUID: UUID,
    val timestamp: Long = System.currentTimeMillis(),
    var isDeleted: Boolean = false,
    val readBy: MutableSet<UUID> = mutableSetOf()
) {
    fun isReadBy(playerUUID: UUID): Boolean {
        return readBy.contains(playerUUID)
    }

    fun markAsRead(playerUUID: UUID) {
        readBy.add(playerUUID)
    }
}