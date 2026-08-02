package com.imyvm.community.entrypoint.screen.inner_community

import com.imyvm.community.application.interaction.common.ChatChannelManager
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

class ChatRoomMenu(
    syncId: Int,
    val player: ServerPlayer,
    val community: Community,
    val runBack: ((ServerPlayer) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.chat.title") ?: Component.literal("Chat Room"),
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
            Translator.tr("ui.community.chat.status.enabled").string ?: "Active" 
        else 
            Translator.tr("ui.community.chat.status.disabled").string ?: "Inactive"
        
        val itemType = if (isActive) Items.RECOVERY_COMPASS else Items.COMPASS
        val itemStack = ItemStack(itemType)
        
        val loreLines = mutableListOf<Component>()
        loreLines.add(Component.literal(Translator.tr("ui.community.chat.status.current", statusText).string ?: "§7Current: $statusText"))
        loreLines.add(Component.literal(""))
        
        if (isActive) {
            loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.channel_active").string ?: 
                "§aYour messages are sent to this community"))
            loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.channel_active2").string ?: 
                "§aType normally to send messages here"))
            loreLines.add(Component.literal(""))
            loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.channel_click").string ?: 
                "§7Click to disable and return to global chat"))
        } else {
            if (activeChannel != null) {
                loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.channel_other").string ?: 
                    "§7You are currently in another community's chat"))
                loreLines.add(Component.literal(""))
            }
            loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.channel_inactive").string ?: 
                "§7Your messages go to global"))
            loreLines.add(Component.literal(""))
            loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.channel_click2").string ?: 
                "§7Click to switch to this community's chat"))
        }
        
        val buttonStack = getLoreButton(itemStack, loreLines)
        
        addButton(
            slot = 13,
            itemStack = buttonStack,
            name = Translator.tr("ui.community.chat.button.toggle_channel").string ?: "Toggle Default Chat Channel"
        ) {
            com.imyvm.community.application.interaction.screen.inner_community.chat.runToggleChatChannel(player, community, runBack)
        }
    }

    private fun addViewHistoryButton() {
        val itemStack = ItemStack(Items.BOOK)
        val loreLines = listOf(
            Component.literal(Translator.tr("ui.community.chat.lore.history").string ?: "§7View all chat messages in this community")
        )
        val buttonStack = getLoreButton(itemStack, loreLines)
        
        addButton(
            slot = 11,
            itemStack = buttonStack,
            name = Translator.tr("ui.community.chat.button.history").string ?: "View Chat History"
        ) {
            com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatHistory(player, community, runBack)
        }
    }

    private fun addSendInstructionButton() {
        val regionId = community.regionNumberId
        val itemStack = ItemStack(Items.PAPER)
        
        val loreLines = mutableListOf<Component>()
        loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.instruction1").string ?: "§7To send messages:"))
        loreLines.add(Component.literal(""))
        loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.instruction2").string ?: "§e1. Enable Chat Channel above"))
        loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.instruction3").string ?: "§e2. Type normally in chat"))
        loreLines.add(Component.literal(""))
        loreLines.add(Component.literal(Translator.tr("ui.community.chat.lore.instruction4").string ?: "§7Click to fill the chat command"))
        
        val buttonStack = getLoreButton(itemStack, loreLines)
        
        addButton(
            slot = 15,
            itemStack = buttonStack,
            name = Translator.tr("ui.community.chat.button.instruction").string ?: "How to Send Messages"
        ) {
            if (regionId != null) {
                it.closeContainer()
                val button = Translator.tr("ui.community.chat.command.button").copy().withStyle { style ->
                    style.withClickEvent(ClickEvent.SuggestCommand("/community chat $regionId "))
                        .withHoverEvent(HoverEvent.ShowText(Translator.tr("ui.community.chat.command.hover")))
                }
                it.sendSystemMessage(Component.empty().append(Translator.tr("ui.community.chat.command.prompt")).append(button))
            }
        }
    }
}
