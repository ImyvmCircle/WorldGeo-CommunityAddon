package com.imyvm.community.entrypoints.screen.inner_community

import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getFormattedMillsHour
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity

class ChatHistoryMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val community: Community,
    page: Int = 0,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractListMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.chat.history.title"),
    page = page,
    runBack = runBack
) {
    companion object {
        const val MESSAGES_PER_PAGE = 50
    }

    init {
        displayChatHistory()
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatHistoryPage(
            player, community, newPage, runBack
        )
    }

    private fun displayChatHistory() {
        val chatMessages = community.getChatMessages()
        val totalMessages = chatMessages.size
        val totalPages = (totalMessages + MESSAGES_PER_PAGE - 1) / MESSAGES_PER_PAGE
        
        if (chatMessages.isEmpty()) {
            addButton(
                slot = 13,
                name = Translator.tr("ui.community.chat.history.empty")?.string ?: "No messages yet",
                item = Items.BARRIER
            ) {}
            return
        }

        val startIndex = page * MESSAGES_PER_PAGE
        val endIndex = minOf(startIndex + MESSAGES_PER_PAGE, totalMessages)
        val messagesToDisplay = chatMessages.subList(startIndex, endIndex)

        addButton(
            slot = 4,
            name = Translator.tr("ui.community.chat.history.page", page + 1, totalPages)?.string ?: "Page ${page + 1}/$totalPages",
            item = Items.PAPER
        ) {}

        var slot = 9
        for (message in messagesToDisplay) {
            if (slot >= 45) break
            
            val senderName = community.member[message.senderUUID]?.let {
                player.server.userCache?.getByUuid(message.senderUUID)?.get()?.name ?: "Unknown"
            } ?: "Unknown"
            
            val role = community.getMemberRole(message.senderUUID)?.name ?: "MEMBER"
            val timestamp = getFormattedMillsHour(message.timestamp)
            val messageText = message.content.string
            
            // Truncate long messages for button display
            val displayText = if (messageText.length > 30) {
                messageText.substring(0, 27) + "..."
            } else {
                messageText
            }
            
            addButton(
                slot = slot,
                name = "§7[$timestamp] §f$senderName §7($role): $displayText",
                item = Items.WRITABLE_BOOK
            ) {}
            
            slot++
        }

        if (page > 0) {
            addButton(
                slot = 48,
                name = Translator.tr("ui.community.chat.history.prev")?.string ?: "Previous Page",
                item = Items.ARROW
            ) {
                com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatHistoryPage(
                    player, community, page - 1, runBack
                )
            }
        }

        if (page < totalPages - 1) {
            addButton(
                slot = 50,
                name = Translator.tr("ui.community.chat.history.next")?.string ?: "Next Page",
                item = Items.ARROW
            ) {
                com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatHistoryPage(
                    player, community, page + 1, runBack
                )
            }
        }
    }
}
