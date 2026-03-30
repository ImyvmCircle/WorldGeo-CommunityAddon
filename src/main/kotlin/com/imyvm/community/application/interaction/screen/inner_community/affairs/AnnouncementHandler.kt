package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.model.community.Announcement
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.annoucement.AdministrationAnnouncementDetailsMenu
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.annoucement.AdministrationAnnouncementInputMenuAnvil
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.annoucement.AdministrationAnnouncementListMenu
import com.imyvm.community.entrypoint.screen.inner_community.affairs.annoucement.MemberAnnouncementDetailsMenu
import com.imyvm.community.entrypoint.screen.inner_community.affairs.annoucement.MemberAnnouncementListMenu
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.TextParser
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.ClickEvent
import java.util.*

fun runOpenAnnouncementListMenu(player: ServerPlayer, community: Community, runBack: (ServerPlayer) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAnnouncementListMenu(syncId, community, player, 0, runBack)
        }
    } ?: player.closeContainer()
}

fun runOpenAnnouncementDetailsMenu(
    player: ServerPlayer,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayer) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAnnouncementDetailsMenu(syncId, community, player, announcementId) {
                runOpenAnnouncementListMenu(player, community, runBack)
            }
        }
    } ?: player.closeContainer()
}

fun runCreateAnnouncement(player: ServerPlayer, community: Community, runBack: (ServerPlayer) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        player.closeContainer()
        AdministrationAnnouncementInputMenuAnvil(player, community, runBack).open()
    }
}

fun onCreateAnnouncementConfirm(
    player: ServerPlayer,
    community: Community,
    content: String,
    runBack: (ServerPlayer) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        if (content.isBlank()) {
            player.sendSystemMessage(Translator.tr("ui.admin.announcement.error.empty"))
            player.closeContainer()
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

        player.sendSystemMessage(Translator.tr("ui.admin.announcement.created"))
        runBack(player)
    } ?: player.closeContainer()
}

fun onDeleteAnnouncement(
    player: ServerPlayer,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayer) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { 
            val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
            if (!adminCheck.isAllowed()) return@executeWithPermission adminCheck
            CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_ANNOUNCEMENTS)
        }
    ) {
        if (community.softDeleteAnnouncement(announcementId)) {
            CommunityDatabase.save()
            player.sendSystemMessage(Translator.tr("ui.admin.announcement.deleted"))
        } else {
            player.sendSystemMessage(Translator.tr("ui.admin.announcement.error.not_found"))
        }
        runBack(player)
    }
}

fun runOpenMemberAnnouncementListMenu(player: ServerPlayer, community: Community, runBack: (ServerPlayer) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canViewCommunity(player, community) }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            MemberAnnouncementListMenu(syncId, community, player, 0, runBack)
        }
    } ?: player.closeContainer()
}

fun runOpenMemberAnnouncementDetailsMenu(
    player: ServerPlayer,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayer) -> Unit
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
    } ?: player.closeContainer()
}

fun onViewAnnouncementContent(
    player: ServerPlayer,
    community: Community,
    announcement: Announcement
) {
    player.sendSystemMessage(Translator.tr("ui.admin.announcement_details.header"))
    player.sendSystemMessage(announcement.content)
    player.sendSystemMessage(Translator.tr("ui.admin.announcement_details.footer"))
    val regionId = community.regionNumberId
    if (regionId != null) {
        player.sendSystemMessage(
            Translator.tr("ui.button.return_to_menu").copy().withStyle { style ->
                style.withClickEvent(ClickEvent.RunCommand( "/community open_announcements $regionId"))
            }
        )
    }
    player.closeContainer()
}

fun onViewMemberAnnouncementListItem(
    player: ServerPlayer,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayer) -> Unit
) {
    runOpenMemberAnnouncementDetailsMenu(player, community, announcementId, runBack)
}

fun onViewAdministrationAnnouncementListItem(
    player: ServerPlayer,
    community: Community,
    announcementId: UUID,
    runBack: (ServerPlayer) -> Unit
) {
    runOpenAnnouncementDetailsMenu(player, community, announcementId, runBack)
}

private fun notifyMembersOfNewAnnouncement(community: Community, announcement: Announcement) {
    val server = WorldGeoCommunityAddon.server ?: return
    
    for (memberUUID in community.member.keys) {
        val player = server.playerList.getPlayer(memberUUID)
        if (player != null) {
            player.sendSystemMessage(Translator.tr("announcement.notification.new"))
            player.sendSystemMessage(announcement.content)
        }
    }
}
