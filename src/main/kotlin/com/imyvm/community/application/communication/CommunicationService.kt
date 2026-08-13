package com.imyvm.community.application.communication

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.Announcement
import com.imyvm.community.domain.model.community.CommunityMessage
import com.imyvm.community.domain.model.communication.CommunicationCategory
import com.imyvm.community.domain.model.communication.CommunicationRecord
import com.imyvm.community.domain.model.communication.CommunicationRecordType
import com.imyvm.community.domain.model.communication.CommunicationVisibility
import com.imyvm.community.infra.communication.CommunicationShardStore
import net.minecraft.network.chat.Component
import java.util.UUID

internal fun Community.addChatMessageWithShard(message: CommunityMessage): Boolean {
    val rid = regionNumberId ?: return false
    return CommunicationShardStore.append(
        CommunicationRecord(rid, message.timestamp, message.senderUUID.toString(), null,
            CommunicationRecordType.CHAT, legacyText = message.content.string),
        CommunicationCategory.CHAT
    )
}

internal fun Community.addSystemMessageWithShard(message: CommunityMessage) {
    messages.add(message)
    val rid = regionNumberId ?: return
    CommunicationShardStore.append(
        CommunicationRecord(rid, message.timestamp, message.senderUUID.toString(), null,
            CommunicationRecordType.SYSTEM, legacyText = message.content.string),
        CommunicationCategory.SYSTEM
    )
}

internal fun Community.addAnnouncementWithShard(announcement: Announcement) {
    announcements.add(announcement)
    val rid = regionNumberId ?: return
    CommunicationShardStore.append(
        CommunicationRecord(rid, announcement.timestamp, announcement.authorUUID.toString(), null,
            CommunicationRecordType.ANNOUNCEMENT, legacyText = announcement.content.string),
        CommunicationCategory.SYSTEM
    )
}

internal fun Community.addMailWithShard(recipientUuid: UUID, mail: Component) {
    member[recipientUuid]?.mail?.add(mail)
    val rid = regionNumberId ?: return
    CommunicationShardStore.append(
        CommunicationRecord(rid, System.currentTimeMillis(), null, null,
            CommunicationRecordType.SYSTEM, legacyText = mail.string),
        CommunicationCategory.SYSTEM
    )
}

internal fun appendOpExceptionNotification(regionId: Int, text: String) {
    CommunicationShardStore.append(
        CommunicationRecord(regionId, System.currentTimeMillis(), null, null,
            CommunicationRecordType.OP_EXCEPTION, legacyText = text, visibility = CommunicationVisibility.OP),
        CommunicationCategory.OP_EXCEPTION_UNCLOSED
    )
}


fun migrateLegacyCommunicationsToShards(communities: Iterable<Community>) {
    communities.forEach { community ->
        val regionId = community.regionNumberId ?: return@forEach
        var migrated = true
        community.messages.forEach { message ->
            val type = when (message.type) {
                com.imyvm.community.domain.model.community.MessageType.CHAT -> CommunicationRecordType.CHAT
                com.imyvm.community.domain.model.community.MessageType.ANNOUNCEMENT -> CommunicationRecordType.ANNOUNCEMENT
                else -> CommunicationRecordType.SYSTEM
            }
            val category = if (type == CommunicationRecordType.CHAT) CommunicationCategory.CHAT else CommunicationCategory.SYSTEM
            if (!CommunicationShardStore.appendSynchronously(
                    CommunicationRecord(regionId, message.timestamp, message.senderUUID.toString(), null, type, legacyText = message.content.string),
                    category
                )) migrated = false
        }
        if (migrated) community.messages.clear()
    }
}
