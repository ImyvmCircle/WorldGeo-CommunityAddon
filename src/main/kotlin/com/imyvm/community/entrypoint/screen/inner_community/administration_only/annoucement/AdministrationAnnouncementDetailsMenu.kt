package com.imyvm.community.entrypoint.screen.inner_community.administration_only.annoucement

import com.imyvm.community.application.interaction.screen.inner_community.affairs.onDeleteAnnouncement
import com.imyvm.community.application.interaction.screen.inner_community.affairs.onViewAnnouncementContent
import com.imyvm.community.domain.model.community.Announcement
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getFormattedMillsHour
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import java.util.*

class AdministrationAnnouncementDetailsMenu(
    syncId: Int,
    val community: Community,
    val playerExecutor: ServerPlayer,
    val announcementId: UUID,
    val runBack: ((ServerPlayer) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.admin.announcement_details.title"),
    runBack = runBack
) {
    init {
        val announcement = community.getAnnouncementById(announcementId)
        if (announcement != null) {
            addAnnouncementDisplay(announcement)
            addDeleteButton(announcement)
        }
    }

    private fun addAnnouncementDisplay(announcement: Announcement) {
        val authorName = UtilApi.getPlayerName(playerExecutor, announcement.authorUUID)
        val timeFormatted = getFormattedMillsHour(announcement.timestamp)
        val readCount = announcement.readBy.size
        val totalMembers = community.member.size

        addButton(
            slot = 13,
            itemStack = getLoreButton(
                ItemStack(Items.PAPER),
                listOf(
                    Translator.tr("ui.admin.announcement_details.lore.author", authorName) ?: Component.literal("By: $authorName"),
                    Translator.tr("ui.admin.announcement_details.lore.time", timeFormatted) ?: Component.literal("Time: $timeFormatted"),
                    Translator.tr("ui.admin.announcement_details.lore.read", readCount, totalMembers) ?: Component.literal("Read: $readCount/$totalMembers")
                )
            ),
            name = Translator.tr("ui.admin.announcement_details.content").string ?: "Content"
        ) { onViewAnnouncementContent(playerExecutor, community, announcement) }
    }

    private fun addDeleteButton(announcement: Announcement) {
        addButton(
            slot = 31,
            name = Translator.tr("ui.admin.announcement_details.delete").string ?: "Delete",
            item = Items.BARRIER
        ) {
            onDeleteAnnouncement(playerExecutor, community, announcement.id, runBack)
        }
    }
}
