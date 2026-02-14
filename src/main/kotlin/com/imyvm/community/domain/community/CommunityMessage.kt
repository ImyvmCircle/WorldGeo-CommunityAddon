package com.imyvm.community.domain.community

import net.minecraft.text.Text
import java.util.*

data class CommunityMessage(
    val id: UUID = UUID.randomUUID(),
    val type: MessageType,
    val content: Text,
    val senderUUID: UUID,
    val timestamp: Long = System.currentTimeMillis(),
    var isDeleted: Boolean = false,
    val readBy: MutableSet<UUID> = mutableSetOf(),
    val recipientUUID: UUID? = null  // For MAIL type only
) {
    fun isReadBy(playerUUID: UUID): Boolean {
        return readBy.contains(playerUUID)
    }

    fun markAsRead(playerUUID: UUID) {
        readBy.add(playerUUID)
    }
}

enum class MessageType(val value: Int) {
    ANNOUNCEMENT(1),
    MAIL(2),
    CHAT(3)
}
