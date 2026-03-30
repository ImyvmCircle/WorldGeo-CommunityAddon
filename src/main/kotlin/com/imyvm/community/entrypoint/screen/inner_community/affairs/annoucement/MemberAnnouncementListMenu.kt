package com.imyvm.community.entrypoint.screen.inner_community.affairs.annoucement

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.affairs.onViewMemberAnnouncementListItem
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getFormattedMillsHour
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class MemberAnnouncementListMenu(
    syncId: Int,
    val community: Community,
    val player: ServerPlayer,
    page: Int = 0,
    val runBack: ((ServerPlayer) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.community.announcement_list.title"),
    page = page,
    runBack = runBack
) {

    private val announcementsPerPage = 45
    private val startSlot = 0

    init {
        val announcements = community.getActiveAnnouncements().sortedByDescending { it.timestamp }
        renderList(announcements, announcementsPerPage, startSlot) { announcement, slot, _ ->
            val authorName = UtilApi.getPlayerName(player, announcement.authorUUID)
            val timeFormatted = getFormattedMillsHour(announcement.timestamp)
            val preview = announcement.content.string.take(30) + if (announcement.content.string.length > 30) "..." else ""
            val isRead = announcement.isReadBy(player.uuid)

            addButton(
                slot = slot,
                itemStack = getLoreButton(
                    ItemStack(if (isRead) Items.PAPER else Items.WRITABLE_BOOK),
                    listOf(
                        if (isRead) Translator.tr("ui.community.announcement_list.lore.read") ?: Component.literal("[READ]") 
                        else Translator.tr("ui.community.announcement_list.lore.unread") ?: Component.literal("[UNREAD]"),
                        Translator.tr("ui.community.announcement_list.lore.author", authorName) ?: Component.literal("By: $authorName"),
                        Translator.tr("ui.community.announcement_list.lore.time", timeFormatted) ?: Component.literal("Time: $timeFormatted"),
                        Translator.tr("ui.community.announcement_list.lore.preview", preview) ?: Component.literal(preview)
                    )
                ),
                name = Translator.tr("ui.community.announcement_list.item").string ?: "Announcement"
            ) { onViewMemberAnnouncementListItem(player, community, announcement.id, runBack) }
        }
        handlePageWithSize(announcements.size, announcementsPerPage)
    }

    override fun openNewPage(player: ServerPlayer, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            MemberAnnouncementListMenu(syncId, community, player, newPage, runBack)
        }
    }
}
