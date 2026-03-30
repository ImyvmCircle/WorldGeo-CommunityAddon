package com.imyvm.community.entrypoint.screen.inner_community.affairs.annoucement

import com.imyvm.community.application.interaction.screen.inner_community.affairs.onViewAnnouncementContent
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.Announcement
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

class MemberAnnouncementDetailsMenu(
    syncId: Int,
    val community: Community,
    val player: ServerPlayer,
    val announcementId: UUID,
    val runBack: ((ServerPlayer) -> Unit)
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
                    Translator.tr("ui.community.announcement_details.lore.author", authorName) ?: Component.literal("By: $authorName"),
                    Translator.tr("ui.community.announcement_details.lore.time", timeFormatted) ?: Component.literal("Time: $timeFormatted")
                )
            ),
            name = Translator.tr("ui.community.announcement_details.content").string ?: "Content"
        ) { onViewAnnouncementContent(player, community, announcement) }
    }
}
