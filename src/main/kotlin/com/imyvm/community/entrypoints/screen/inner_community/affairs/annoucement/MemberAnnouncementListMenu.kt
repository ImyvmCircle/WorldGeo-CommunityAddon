package com.imyvm.community.entrypoints.screen.inner_community.affairs.annoucement

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.affairs.onViewMemberAnnouncementListItem
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getFormattedMillsHour
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class MemberAnnouncementListMenu(
    syncId: Int,
    val community: Community,
    val player: ServerPlayerEntity,
    page: Int = 0,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.community.announcement_list.title"),
    page = page,
    runBack = runBack
) {

    private val announcementsPerPage = 45
    private val startSlot = 0

    init {
        addAnnouncementButtons()
        handlePage(community.getActiveAnnouncements().size)
    }

    override fun calculateTotalPages(listSize: Int): Int {
        return (listSize + announcementsPerPage - 1) / announcementsPerPage
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            MemberAnnouncementListMenu(syncId, community, player, newPage, runBack)
        }
    }

    private fun addAnnouncementButtons() {
        val announcements = community.getActiveAnnouncements().sortedByDescending { it.timestamp }
        val startIndex = page * announcementsPerPage
        val endIndex = minOf(startIndex + announcementsPerPage, announcements.size)

        for (i in startIndex until endIndex) {
            val announcement = announcements[i]
            val authorName = UtilApi.getPlayerName(player, announcement.authorUUID)
            val timeFormatted = getFormattedMillsHour(announcement.timestamp)
            val preview = announcement.content.string.take(30) + if (announcement.content.string.length > 30) "..." else ""
            val isRead = announcement.isReadBy(player.uuid)

            val slot = startSlot + (i - startIndex)
            addButton(
                slot = slot,
                itemStack = getLoreButton(
                    ItemStack(if (isRead) Items.PAPER else Items.WRITABLE_BOOK),
                    listOf(
                        if (isRead) Translator.tr("ui.community.announcement_list.lore.read") ?: Text.of("[READ]") 
                        else Translator.tr("ui.community.announcement_list.lore.unread") ?: Text.of("[UNREAD]"),
                        Translator.tr("ui.community.announcement_list.lore.author", authorName) ?: Text.of("By: $authorName"),
                        Translator.tr("ui.community.announcement_list.lore.time", timeFormatted) ?: Text.of("Time: $timeFormatted"),
                        Translator.tr("ui.community.announcement_list.lore.preview", preview) ?: Text.of(preview)
                    )
                ),
                name = Translator.tr("ui.community.announcement_list.item")?.string ?: "Announcement"
            ) { onViewMemberAnnouncementListItem(player, community, announcement.id, runBack) }
        }
    }
}
