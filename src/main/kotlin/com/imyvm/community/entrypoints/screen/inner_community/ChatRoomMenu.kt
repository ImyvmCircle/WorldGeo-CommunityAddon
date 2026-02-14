package com.imyvm.community.entrypoints.screen.inner_community

import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity

class ChatRoomMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val community: Community,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.chat.title"),
    runBack = runBack
) {
    init {
        addToggleSendButton()
        addToggleViewButton()
        addViewHistoryButton()
        addSendInstructionButton()
    }

    private fun addToggleSendButton() {
        val memberAccount = community.member[player.uuid] ?: return
        val isEnabled = memberAccount.chatRoomSendEnabled
        
        val statusText = if (isEnabled) "Enabled" else "Disabled"
        val itemType = if (isEnabled) Items.GREEN_DYE else Items.GRAY_DYE
        
        addButton(
            slot = 10,
            name = (Translator.tr("ui.community.chat.button.toggle_send")?.string ?: "Toggle Send Mode") + " - §7$statusText",
            item = itemType
        ) {
            com.imyvm.community.application.interaction.screen.inner_community.chat.runToggleChatSend(player, community, runBack)
        }
    }

    private fun addToggleViewButton() {
        val memberAccount = community.member[player.uuid] ?: return
        val isEnabled = memberAccount.chatHistoryEnabled
        
        val statusText = if (isEnabled) "Enabled" else "Disabled"
        val itemType = if (isEnabled) Items.GREEN_DYE else Items.GRAY_DYE
        
        addButton(
            slot = 11,
            name = (Translator.tr("ui.community.chat.button.toggle_view")?.string ?: "Toggle View Mode") + " - §7$statusText",
            item = itemType
        ) {
            com.imyvm.community.application.interaction.screen.inner_community.chat.runToggleChatView(player, community, runBack)
        }
    }

    private fun addViewHistoryButton() {
        addButton(
            slot = 13,
            name = Translator.tr("ui.community.chat.button.history")?.string ?: "View Chat History",
            item = Items.BOOK
        ) {
            com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatHistory(player, community, runBack)
        }
    }

    private fun addSendInstructionButton() {
        addButton(
            slot = 22,
            name = Translator.tr("ui.community.chat.button.instruction")?.string ?: "How to Send Messages",
            item = Items.PAPER
        ) {
            // Just info button, no action
        }
    }
}
