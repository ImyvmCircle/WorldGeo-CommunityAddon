package com.imyvm.community.application.interaction.screen.inner_community.chat

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.inner_community.ChatHistoryMenu
import com.imyvm.community.entrypoints.screen.inner_community.ChatRoomMenu
import com.imyvm.community.infra.CommunityDatabase
import net.minecraft.server.network.ServerPlayerEntity

fun runOpenChatRoomMenu(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    player.closeHandledScreen()
    CommunityMenuOpener.open(player) { syncId ->
        ChatRoomMenu(syncId, player, community, runBack)
    }
}

fun runOpenChatHistory(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    player.closeHandledScreen()
    CommunityMenuOpener.open(player) { syncId ->
        ChatHistoryMenu(syncId, player, community, 0, runBack)
    }
}

fun runOpenChatHistoryPage(
    player: ServerPlayerEntity,
    community: Community,
    page: Int,
    runBack: (ServerPlayerEntity) -> Unit
) {
    player.closeHandledScreen()
    CommunityMenuOpener.open(player) { syncId ->
        ChatHistoryMenu(syncId, player, community, page, runBack)
    }
}

fun runToggleChatChannel(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    com.imyvm.community.application.interaction.common.ChatRoomHandler.toggleChatChannel(player, community)
    
    // Reopen menu to show updated status
    runOpenChatRoomMenu(player, community, runBack)
}

fun runToggleChatView(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val memberAccount = community.member[player.uuid] ?: return
    memberAccount.chatHistoryEnabled = !memberAccount.chatHistoryEnabled
    CommunityDatabase.save()
    
    // Reopen menu to show updated status
    runOpenChatRoomMenu(player, community, runBack)
}
