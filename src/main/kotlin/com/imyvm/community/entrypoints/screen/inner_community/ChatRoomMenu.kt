package com.imyvm.community.entrypoints.screen.inner_community

import com.imyvm.community.application.interaction.common.ChatChannelManager
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ChatRoomMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val community: Community,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.chat.title") ?: Text.literal("Chat Room"),
    runBack = runBack
) {
    init {
        addToggleChatChannelButton()
        addViewHistoryButton()
        addSendInstructionButton()
    }

    private fun addToggleChatChannelButton() {
        val activeChannel = ChatChannelManager.getActiveChannel(player.uuid)
        val isActive = activeChannel == community.regionNumberId
        
        val statusText = if (isActive) 
            Translator.tr("ui.community.chat.status.enabled")?.string ?: "Active" 
        else 
            Translator.tr("ui.community.chat.status.disabled")?.string ?: "Inactive"
        
        val itemType = if (isActive) Items.RECOVERY_COMPASS else Items.COMPASS
        val itemStack = ItemStack(itemType)
        
        val loreLines = mutableListOf<Text>()
        loreLines.add(Text.literal(Translator.tr("ui.community.chat.status.current", statusText)?.string ?: "§7Current: $statusText"))
        loreLines.add(Text.literal(""))
        
        if (isActive) {
            loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.channel_active")?.string ?: 
                "§aYour messages are sent to this community"))
            loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.channel_active2")?.string ?: 
                "§aType normally to send messages here"))
            loreLines.add(Text.literal(""))
            loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.channel_click")?.string ?: 
                "§7Click to disable and return to global chat"))
        } else {
            if (activeChannel != null) {
                loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.channel_other")?.string ?: 
                    "§7You are currently in another community's chat"))
                loreLines.add(Text.literal(""))
            }
            loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.channel_inactive")?.string ?: 
                "§7Your messages go to global"))
            loreLines.add(Text.literal(""))
            loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.channel_click2")?.string ?: 
                "§7Click to switch to this community's chat"))
        }
        
        val buttonStack = getLoreButton(itemStack, loreLines)
        
        addButton(
            slot = 13,
            itemStack = buttonStack,
            name = Translator.tr("ui.community.chat.button.toggle_channel")?.string ?: "Toggle Default Chat Channel"
        ) {
            com.imyvm.community.application.interaction.screen.inner_community.chat.runToggleChatChannel(player, community, runBack)
        }
    }

    private fun addViewHistoryButton() {
        val itemStack = ItemStack(Items.BOOK)
        val loreLines = listOf(
            Text.literal(Translator.tr("ui.community.chat.lore.history")?.string ?: "§7View all chat messages in this community")
        )
        val buttonStack = getLoreButton(itemStack, loreLines)
        
        addButton(
            slot = 11,
            itemStack = buttonStack,
            name = Translator.tr("ui.community.chat.button.history")?.string ?: "View Chat History"
        ) {
            com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatHistory(player, community, runBack)
        }
    }

    private fun addSendInstructionButton() {
        val communityName = community.generateCommunityMark()
        val itemStack = ItemStack(Items.PAPER)
        
        val loreLines = mutableListOf<Text>()
        loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.instruction1")?.string ?: "§7To send messages:"))
        loreLines.add(Text.literal(""))
        loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.instruction2")?.string ?: "§e1. Enable Chat Channel above"))
        loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.instruction3")?.string ?: "§e2. Type normally in chat"))
        loreLines.add(Text.literal(""))
        loreLines.add(Text.literal(Translator.tr("ui.community.chat.lore.instruction4")?.string ?: "§7Or use command:"))
        loreLines.add(Text.literal("§e/community chat §b$communityName §f<message>"))
        loreLines.add(Text.literal("§7or"))
        loreLines.add(Text.literal("§e/community chat §b${community.regionNumberId} §f<message>"))
        
        val buttonStack = getLoreButton(itemStack, loreLines)
        
        addButton(
            slot = 15,
            itemStack = buttonStack,
            name = Translator.tr("ui.community.chat.button.instruction")?.string ?: "How to Send Messages"
        ) {

        }
    }
}
