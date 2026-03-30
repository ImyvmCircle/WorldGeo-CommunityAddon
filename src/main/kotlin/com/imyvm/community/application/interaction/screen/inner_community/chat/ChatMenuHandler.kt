package com.imyvm.community.application.interaction.screen.inner_community.chat

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.inner_community.ChatRoomMenu
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

fun runOpenChatRoomMenu(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    player.closeContainer()
    CommunityMenuOpener.open(player) { syncId ->
        ChatRoomMenu(syncId, player, community, runBack)
    }
}

fun runOpenChatHistory(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    player.closeContainer()
    
    val chatMessages = community.getChatMessages()
    
    if (chatMessages.isEmpty()) {
        player.sendSystemMessage(Component.literal("§7No messages in chat history yet."))
        player.sendSystemMessage(Component.literal("§7Use §e/community chat ${community.generateCommunityMark()} <message>§7 to send messages!"))
        return
    }
    
    player.sendSystemMessage(Component.literal("§7§m                                                    "))
    player.sendSystemMessage(Component.literal("§6§lChat History: §r${community.generateCommunityMark()}"))
    player.sendSystemMessage(Component.literal("§7Showing last ${minOf(20, chatMessages.size)} messages"))
    player.sendSystemMessage(Component.literal(""))
    
    val messagesToShow = chatMessages.take(20)
    
    for (message in messagesToShow) {
        val senderName = community.member[message.senderUUID]?.let {
            player.level().server?.playerList?.getPlayer(message.senderUUID)?.gameProfile?.name ?: "Unknown"
        } ?: "Unknown"
        
        val role = community.getMemberRole(message.senderUUID)
        val roleDisplay = when (role?.name) {
            "OWNER" -> if (community.isManor()) "§6Landowner§r" else "§6Lord§r"
            "ADMIN" -> if (community.isManor()) "§5HouseKeeper§r" else "§5Steward§r"
            "MEMBER" -> if (community.isManor()) "§aResident§r" else "§aCitizen§r"
            else -> "§7Member§r"
        }
        
        val timestamp = com.imyvm.community.util.getFormattedMillsHour(message.timestamp)
        val messageText = message.content.string
        
        player.sendSystemMessage(Component.literal("§8[$timestamp]§r $roleDisplay §f$senderName§7: §r$messageText"))
    }
    
    player.sendSystemMessage(Component.literal(""))
    player.sendSystemMessage(Component.literal("§7§m                                                    "))
}

fun runToggleChatChannel(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) {
    com.imyvm.community.application.interaction.common.ChatRoomHandler.toggleChatChannel(player, community)
    
    runOpenChatRoomMenu(player, community, runBack)
}
