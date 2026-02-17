package com.imyvm.community.entrypoints.screen.inner_community.affairs.annoucement

import com.imyvm.community.application.interaction.screen.inner_community.affairs.onViewAnnouncementContent
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.Announcement
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getFormattedMillsHour
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*

class MemberAnnouncementDetailsMenu(
    syncId: Int,
    val community: Community,
    val player: ServerPlayerEntity,
    val announcementId: UUID,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.announcement_details.title"),
    runBack = runBack
) {
    init {
        val announcement = community.getAnnouncementById(announcementId)
        if (announcement != null) {
            announcement.markAsRead(player.uuid)
            addAnnouncementDisplay(announcement)
        }
    }

    private fun addAnnouncementDisplay(announcement: Announcement) {
        val authorName = UtilApi.getPlayerName(player, announcement.authorUUID)
        val timeFormatted = getFormattedMillsHour(announcement.timestamp)

        addButton(
            slot = 13,
            itemStack = getLoreButton(
                ItemStack(Items.PAPER),
                listOf(
                    Translator.tr("ui.community.announcement_details.lore.author", authorName) ?: Text.of("By: $authorName"),
                    Translator.tr("ui.community.announcement_details.lore.time", timeFormatted) ?: Text.of("Time: $timeFormatted")
                )
            ),
            name = Translator.tr("ui.community.announcement_details.content")?.string ?: "Content"
        ) { onViewAnnouncementContent(player, announcement) }
    }
}
