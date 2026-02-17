package com.imyvm.community.application.interaction.command

import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdministrationPermission
import com.imyvm.community.domain.model.community.Announcement
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.TextParser
import com.imyvm.community.util.Translator
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun onAnnouncementCreateCommand(context: CommandContext<ServerCommandSource>, community: Community, content: String): Int {
    val player = getPlayerOrNull(context) ?: return 0
    if (!canManage(player, community)) return 0

    if (content.isBlank()) {
        player.sendMessage(Translator.tr("ui.community.administration.announcement.error.empty"))
        return 0
    }

    val announcement = Announcement(content = TextParser.parse(content), authorUUID = player.uuid)
    community.addAnnouncement(announcement)
    CommunityDatabase.save()

    community.member.keys.mapNotNull { context.source.server.playerManager.getPlayer(it) }.forEach { member ->
        member.sendMessage(Translator.tr("announcement.notification.new"))
        member.sendMessage(announcement.content)
    }

    player.sendMessage(Translator.tr("ui.community.administration.announcement.created"))
    return 1
}

fun onAnnouncementDeleteCommand(context: CommandContext<ServerCommandSource>, community: Community, announcementId: String): Int {
    val player = getPlayerOrNull(context) ?: return 0
    if (!canManage(player, community)) return 0
    val uuid = parseUUID(player, announcementId) ?: return 0
    return performDelete(player, community, uuid)
}

fun onAnnouncementOpDeleteCommand(context: CommandContext<ServerCommandSource>, community: Community, announcementId: String): Int {
    val player = getPlayerOrNull(context) ?: return 0
    val uuid = parseUUID(player, announcementId) ?: return 0
    return performDelete(player, community, uuid)
}

fun onAnnouncementListCommand(context: CommandContext<ServerCommandSource>, community: Community): Int {
    val player = getPlayerOrNull(context) ?: return 0
    if (!isMember(player, community)) return 0

    val announcements = community.getActiveAnnouncements().sortedByDescending { it.timestamp }
    if (announcements.isEmpty()) {
        player.sendMessage(Translator.tr("community.announcement.list.empty"))
        return 1
    }

    player.sendMessage(Translator.tr("community.announcement.list.header", community.generateCommunityMark()))
    announcements.forEachIndexed { index, announcement ->
        val status = if (announcement.isReadBy(player.uuid)) "[READ]" else "[UNREAD]"
        val preview = announcement.content.string.let { if (it.length > 50) it.take(50) + "..." else it }
        player.sendMessage(Translator.tr("community.announcement.list.entry", index + 1, status, announcement.id.toString().substring(0, 8), preview))
    }
    return 1
}

fun onAnnouncementViewCommand(context: CommandContext<ServerCommandSource>, community: Community, announcementId: String): Int {
    val player = getPlayerOrNull(context) ?: return 0
    if (!isMember(player, community)) return 0
    val uuid = parseUUID(player, announcementId) ?: return 0

    val announcement = community.getAnnouncementById(uuid)?.takeIf { !it.isDeleted }
        ?: run {
            player.sendMessage(Translator.tr("ui.community.administration.announcement.error.not_found"))
            return 0
        }

    announcement.markAsRead(player.uuid)
    CommunityDatabase.save()

    player.sendMessage(Translator.tr("ui.community.announcement_details.header"))
    player.sendMessage(announcement.content)
    player.sendMessage(Translator.tr("ui.community.announcement_details.footer"))
    return 1
}

fun onAnnouncementOpListCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = getPlayerOrNull(context) ?: return 0

    val summary = CommunityDatabase.communities.mapNotNull { comm ->
        comm.getAllAnnouncements().takeIf { it.isNotEmpty() }?.let { comm to it.size }
    }

    if (summary.isEmpty()) {
        player.sendMessage(Translator.tr("community.announcement.administration.list.empty"))
        return 1
    }

    summary.forEach { (comm, count) ->
        player.sendMessage(Translator.tr("community.announcement.administration.list.community", comm.generateCommunityMark(), comm.regionNumberId, count))
    }
    player.sendMessage(Translator.tr("community.announcement.op.list.total", summary.sumOf { it.second }))
    return 1
}

private fun getPlayerOrNull(context: CommandContext<ServerCommandSource>): ServerPlayerEntity? {
    return context.source.player
}

private fun parseUUID(player: ServerPlayerEntity, idString: String): UUID? {
    return try {
        UUID.fromString(idString)
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr("community.announcement.error.invalid_id"))
        null
    }
}

private fun canManage(player: ServerPlayerEntity, community: Community): Boolean {
    if (CommunityPermissionPolicy.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS).isAllowed()) {
        return true
    }
    player.sendMessage(Translator.tr("community.announcement.error.no_permission"))
    return false
}

private fun isMember(player: ServerPlayerEntity, community: Community): Boolean {
    if (CommunityPermissionPolicy.canViewCommunity(player, community).isAllowed()) {
        return true
    }
    player.sendMessage(Translator.tr("community.announcement.error.not_member"))
    return false
}

private fun performDelete(player: ServerPlayerEntity, community: Community, uuid: UUID): Int {
    return if (community.softDeleteAnnouncement(uuid)) {
        CommunityDatabase.save()
        player.sendMessage(Translator.tr("ui.community.administration.announcement.deleted"))
        1
    } else {
        player.sendMessage(Translator.tr("ui.community.administration.announcement.error.not_found"))
        0
    }
}