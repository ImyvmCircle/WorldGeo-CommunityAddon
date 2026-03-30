package com.imyvm.community.entrypoint.event

import com.imyvm.community.application.interaction.common.ChatChannelManager
import com.imyvm.community.application.interaction.common.ChatRoomHandler
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.server.level.ServerPlayer

fun registerChatInterceptor() {
    ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, sender, params ->
        handleChatMessage(message, sender)
    }
}

private fun handleChatMessage(message: PlayerChatMessage, sender: ServerPlayer): Boolean {
    val activeCommunity = ChatChannelManager.getActiveCommunity(sender.uuid) ?: return true

    val messageContent = message.signedContent()
    val success = ChatRoomHandler.sendChatMessage(sender, activeCommunity, messageContent)

    return !success
}
