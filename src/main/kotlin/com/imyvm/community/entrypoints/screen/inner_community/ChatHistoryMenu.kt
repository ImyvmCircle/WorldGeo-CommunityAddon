package com.imyvm.community.entrypoints.screen.inner_community

import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getFormattedMillsHour
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ChatHistoryMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val community: Community,
    page: Int = 0,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractListMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.chat.history.title") ?: Text.literal("Chat History"),
    page = page,
    runBack = runBack
) {
    companion object {
        const val MESSAGES_PER_PAGE = 36
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
        val totalPages = if (totalMessages > 0) ((totalMessages + MESSAGES_PER_PAGE - 1) / MESSAGES_PER_PAGE) else 1

        val pageInfoStack = ItemStack(Items.WRITTEN_BOOK)
        val pageInfoLore = listOf(
            Text.literal(Translator.tr("ui.community.chat.history.page", page + 1, totalPages)?.string ?: "§7Page ${page + 1}/$totalPages"),
            Text.literal(Translator.tr("ui.community.chat.history.total", totalMessages)?.string ?: "§7Total messages: $totalMessages")
        )
        val pageInfoButton = getLoreButton(pageInfoStack, pageInfoLore)
        
        addButton(
            slot = 4,
            itemStack = pageInfoButton,
            name = Translator.tr("ui.community.chat.history.page_info")?.string ?: "Chat History"
        ) {}
        
        if (chatMessages.isEmpty()) {
            val emptyStack = ItemStack(Items.BARRIER)
            val emptyLore = listOf(
                Text.literal(Translator.tr("ui.community.chat.history.empty_hint")?.string ?: 
                    "§7Use /community chat to send messages!")
            )
            val emptyButton = getLoreButton(emptyStack, emptyLore)
            
            addButton(
                slot = 22,
                itemStack = emptyButton,
                name = Translator.tr("ui.community.chat.history.empty")?.string ?: "No messages yet"
            ) {}
            return
        }

        val startIndex = page * MESSAGES_PER_PAGE
        val endIndex = minOf(startIndex + MESSAGES_PER_PAGE, totalMessages)
        val messagesToDisplay = chatMessages.subList(startIndex, endIndex)

        var slot = 9
        for (message in messagesToDisplay) {
            if (slot >= 45) break
            
            val senderName = community.member[message.senderUUID]?.let {
                player.server.userCache?.getByUuid(message.senderUUID)?.get()?.name ?: "Unknown"
            } ?: "Unknown"
            
            val role = community.getMemberRole(message.senderUUID)
            val roleDisplay = when (role?.name) {
                "OWNER" -> Translator.tr("community.role.owner")?.string ?: "Owner"
                "ADMIN" -> Translator.tr("community.role.admin")?.string ?: "Admin"
                "MEMBER" -> Translator.tr("community.role.member")?.string ?: "Member"
                else -> "Member"
            }
            val timestamp = getFormattedMillsHour(message.timestamp)
            val messageText = message.content.string

            val loreLines = mutableListOf<Text>()
            loreLines.add(Text.literal("§7Time: §f$timestamp"))
            loreLines.add(Text.literal("§7Sender: §f$senderName §7($roleDisplay)"))
            loreLines.add(Text.literal(""))
            loreLines.add(Text.literal("§7Message:"))

            val words = messageText.split(" ")
            var currentLine = "§f"
            for (word in words) {
                if ((currentLine.length + word.length) > 40) {
                    loreLines.add(Text.literal(currentLine))
                    currentLine = "§f$word "
                } else {
                    currentLine += "$word "
                }
            }
            if (currentLine.isNotBlank()) {
                loreLines.add(Text.literal(currentLine.trimEnd()))
            }

            val displayName = if (messageText.length > 30) {
                messageText.substring(0, 27) + "..."
            } else {
                messageText
            }
            
            val messageStack = ItemStack(Items.PAPER)
            val messageButton = getLoreButton(messageStack, loreLines)
            
            addButton(
                slot = slot,
                itemStack = messageButton,
                name = "§f$senderName: §7$displayName"
            ) {}
            
            slot++
        }

        if (page > 0) {
            val prevStack = ItemStack(Items.ARROW)
            val prevLore = listOf(
                Text.literal(Translator.tr("ui.common.page", page)?.string ?: "§7Go to page $page")
            )
            val prevButton = getLoreButton(prevStack, prevLore)
            
            addButton(
                slot = 48,
                itemStack = prevButton,
                name = Translator.tr("ui.common.button.previous")?.string ?: "Previous Page"
            ) {
                com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatHistoryPage(
                    player, community, page - 1, runBack
                )
            }
        }

        if (page < totalPages - 1) {
            val nextStack = ItemStack(Items.ARROW)
            val nextLore = listOf(
                Text.literal(Translator.tr("ui.common.page", page + 2)?.string ?: "§7Go to page ${page + 2}")
            )
            val nextButton = getLoreButton(nextStack, nextLore)
            
            addButton(
                slot = 50,
                itemStack = nextButton,
                name = Translator.tr("ui.common.button.next")?.string ?: "Next Page"
            ) {
                com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatHistoryPage(
                    player, community, page + 1, runBack
                )
            }
        }
    }
}
