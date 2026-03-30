package com.imyvm.community.entrypoint.screen.inner_community.administration_only.annoucement

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.affairs.onViewAdministrationAnnouncementListItem
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runCreateAnnouncement
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

class AdministrationAnnouncementListMenu(
    syncId: Int,
    val community: Community,
    val playerExecutor: ServerPlayer,
    page: Int = 0,
    val runBack: ((ServerPlayer) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.admin.announcement_list.title"),
    page = page,
    runBack = runBack
) {

    private val announcementsPerPage = 35
    private val startSlot = 10

    init {
        addCreateButton()
        val announcements = community.getActiveAnnouncements().sortedByDescending { it.timestamp }
        renderList(announcements, announcementsPerPage, startSlot) { announcement, slot, _ ->
            val authorName = UtilApi.getPlayerName(playerExecutor, announcement.authorUUID)
            val timeFormatted = getFormattedMillsHour(announcement.timestamp)
            val preview = announcement.content.string.take(30) + if (announcement.content.string.length > 30) "..." else ""

            addButton(
                slot = slot,
                itemStack = getLoreButton(
                    ItemStack(Items.PAPER),
                    listOf(
                        Translator.tr("ui.admin.announcement_list.lore.author", authorName) ?: Component.literal("By: $authorName"),
                        Translator.tr("ui.admin.announcement_list.lore.time", timeFormatted) ?: Component.literal("Time: $timeFormatted"),
                        Translator.tr("ui.admin.announcement_list.lore.preview", preview) ?: Component.literal(preview)
                    )
                ),
                name = Translator.tr("ui.admin.announcement_list.item").string ?: "Announcement"
            ) { onViewAdministrationAnnouncementListItem(playerExecutor, community, announcement.id, runBack) }
        }
        handlePageWithSize(announcements.size, announcementsPerPage)
    }

    override fun openNewPage(player: ServerPlayer, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAnnouncementListMenu(syncId, community, playerExecutor, newPage, runBack)
        }
    }

    private fun addCreateButton() {
        addButton(
            slot = 4,
            name = Translator.tr("ui.admin.announcement_list.create").string ?: "Create Announcement",
            item = Items.WRITABLE_BOOK
        ) {
            runCreateAnnouncement(playerExecutor, community, runBack)
        }
    }
}
