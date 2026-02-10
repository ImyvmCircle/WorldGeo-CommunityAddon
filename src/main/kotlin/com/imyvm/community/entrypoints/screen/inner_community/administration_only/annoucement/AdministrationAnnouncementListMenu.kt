package com.imyvm.community.entrypoints.screen.inner_community.administration_only.annoucement

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.affairs.onViewAdministrationAnnouncementListItem
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runCreateAnnouncement
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

class AdministrationAnnouncementListMenu(
    syncId: Int,
    val community: Community,
    val playerExecutor: ServerPlayerEntity,
    page: Int = 0,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.community.operation.announcement_list.title"),
    page = page,
    runBack = runBack
) {

    private val announcementsPerPage = 35
    private val startSlot = 10

    init {
        addCreateButton()
        addAnnouncementButtons()
        handlePage(community.getActiveAnnouncements().size)
    }

    override fun calculateTotalPages(listSize: Int): Int {
        return (listSize + announcementsPerPage - 1) / announcementsPerPage
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAnnouncementListMenu(syncId, community, playerExecutor, newPage, runBack)
        }
    }

    private fun addCreateButton() {
        addButton(
            slot = 4,
            name = Translator.tr("ui.community.operation.announcement_list.create")?.string ?: "Create Announcement",
            item = Items.WRITABLE_BOOK
        ) {
            runCreateAnnouncement(playerExecutor, community, runBack)
        }
    }

    private fun addAnnouncementButtons() {
        val announcements = community.getActiveAnnouncements().sortedByDescending { it.timestamp }
        val startIndex = page * announcementsPerPage
        val endIndex = minOf(startIndex + announcementsPerPage, announcements.size)

        for (i in startIndex until endIndex) {
            val announcement = announcements[i]
            val authorName = UtilApi.getPlayerName(playerExecutor, announcement.authorUUID)
            val timeFormatted = getFormattedMillsHour(announcement.timestamp)
            val preview = announcement.content.string.take(30) + if (announcement.content.string.length > 30) "..." else ""

            val slot = startSlot + (i - startIndex)
            addButton(
                slot = slot,
                itemStack = getLoreButton(
                    ItemStack(Items.PAPER),
                    listOf(
                        Translator.tr("ui.community.operation.announcement_list.lore.author", authorName) ?: Text.of("By: $authorName"),
                        Translator.tr("ui.community.operation.announcement_list.lore.time", timeFormatted) ?: Text.of("Time: $timeFormatted"),
                        Translator.tr("ui.community.operation.announcement_list.lore.preview", preview) ?: Text.of(preview)
                    )
                ),
                name = Translator.tr("ui.community.operation.announcement_list.item")?.string ?: "Announcement"
            ) { onViewAdministrationAnnouncementListItem(playerExecutor, community, announcement.id, runBack) }
        }
    }
}
