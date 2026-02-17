package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdministrationPermission
import com.imyvm.community.domain.model.community.Announcement
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.annoucement.AdministrationAnnouncementDetailsMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.annoucement.AdministrationAnnouncementInputMenuAnvil
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.annoucement.AdministrationAnnouncementListMenu
import com.imyvm.community.entrypoints.screen.inner_community.affairs.annoucement.MemberAnnouncementDetailsMenu
import com.imyvm.community.entrypoints.screen.inner_community.affairs.annoucement.MemberAnnouncementListMenu
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.TextParser
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun runOpenAnnouncementListMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAnnouncementListMenu(syncId, community, player, 0, runBack)
        }
    } ?: player.closeHandledScreen()
}

fun runOpenAnnouncementDetailsMenu(
    player: ServerPlayerEntity,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAnnouncementDetailsMenu(syncId, community, player, announcementId) {
                runOpenAnnouncementListMenu(player, community, runBack)
            }
        }
    } ?: player.closeHandledScreen()
}

fun runCreateAnnouncement(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        player.closeHandledScreen()
        AdministrationAnnouncementInputMenuAnvil(player, community, runBack).open()
    }
}

fun onCreateAnnouncementConfirm(
    player: ServerPlayerEntity,
    community: Community,
    content: String,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        if (content.isBlank()) {
            player.sendMessage(Translator.tr("ui.community.administration.announcement.error.empty"))
            player.closeHandledScreen()
            return@executeWithPermission
        }

        val parsedContent = TextParser.parse(content)
        val announcement = Announcement(
            content = parsedContent,
            authorUUID = player.uuid
        )

        community.addAnnouncement(announcement)
        CommunityDatabase.save()

        notifyMembersOfNewAnnouncement(community, announcement)

        player.sendMessage(Translator.tr("ui.community.administration.announcement.created"))
        runBack(player)
    } ?: player.closeHandledScreen()
}

fun onDeleteAnnouncement(
    player: ServerPlayerEntity,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        if (community.softDeleteAnnouncement(announcementId)) {
            CommunityDatabase.save()
            player.sendMessage(Translator.tr("ui.community.administration.announcement.deleted"))
        } else {
            player.sendMessage(Translator.tr("ui.community.administration.announcement.error.not_found"))
        }
        runBack(player)
    }
}

fun runOpenMemberAnnouncementListMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canViewCommunity(player, community) }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            MemberAnnouncementListMenu(syncId, community, player, 0, runBack)
        }
    } ?: player.closeHandledScreen()
}

fun runOpenMemberAnnouncementDetailsMenu(
    player: ServerPlayerEntity,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canViewCommunity(player, community) }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            MemberAnnouncementDetailsMenu(syncId, community, player, announcementId) {
                runOpenMemberAnnouncementListMenu(player, community, runBack)
            }
        }
    } ?: player.closeHandledScreen()
}

fun onViewAnnouncementContent(
    player: ServerPlayerEntity,
    announcement: Announcement
) {
    player.sendMessage(Translator.tr("ui.community.administration.announcement_details.header"))
    player.sendMessage(announcement.content)
    player.sendMessage(Translator.tr("ui.community.administration.announcement_details.footer"))
    player.closeHandledScreen()
}

fun onViewMemberAnnouncementListItem(
    player: ServerPlayerEntity,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayerEntity) -> Unit
) {
    runOpenMemberAnnouncementDetailsMenu(player, community, announcementId, runBack)
}

fun onViewAdministrationAnnouncementListItem(
    player: ServerPlayerEntity,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayerEntity) -> Unit
) {
    runOpenAnnouncementDetailsMenu(player, community, announcementId, runBack)
}

private fun notifyMembersOfNewAnnouncement(community: Community, announcement: Announcement) {
    val server = WorldGeoCommunityAddon.server ?: return
    
    for (memberUUID in community.member.keys) {
        val player = server.playerManager.getPlayer(memberUUID)
        if (player != null) {
            player.sendMessage(Translator.tr("announcement.notification.new"))
            player.sendMessage(announcement.content)
        }
    }
}
