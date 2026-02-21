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
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*

class AdministrationAnnouncementDetailsMenu(
    syncId: Int,
    val community: Community,
    val playerExecutor: ServerPlayerEntity,
    val announcementId: UUID,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.administration.announcement_details.title"),
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
                    Translator.tr("ui.community.administration.announcement_details.lore.author", authorName) ?: Text.of("By: $authorName"),
                    Translator.tr("ui.community.administration.announcement_details.lore.time", timeFormatted) ?: Text.of("Time: $timeFormatted"),
                    Translator.tr("ui.community.administration.announcement_details.lore.read", readCount, totalMembers) ?: Text.of("Read: $readCount/$totalMembers")
                )
            ),
            name = Translator.tr("ui.community.administration.announcement_details.content")?.string ?: "Content"
        ) { onViewAnnouncementContent(playerExecutor, announcement) }
    }

    private fun addDeleteButton(announcement: Announcement) {
        addButton(
            slot = 31,
            name = Translator.tr("ui.community.administration.announcement_details.delete")?.string ?: "Delete",
            item = Items.BARRIER
        ) {
            onDeleteAnnouncement(playerExecutor, community, announcement.id, runBack)
        }
    }
}
