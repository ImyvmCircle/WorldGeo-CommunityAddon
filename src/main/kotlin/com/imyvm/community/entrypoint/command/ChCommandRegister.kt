package com.imyvm.community.entrypoint.command

import com.imyvm.community.application.interaction.common.ChatRoomHandler
import com.imyvm.community.entrypoint.command.helper.ALL_COMMUNITY_PROVIDER
import com.imyvm.community.entrypoint.command.helper.identifierHandler
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource

fun registerCh(dispatcher: CommandDispatcher<ServerCommandSource>) {
    dispatcher.register(
        literal("ch")
            .then(
                argument("communityIdentifier", StringArgumentType.string())
                    .suggests(ALL_COMMUNITY_PROVIDER)
                    .then(
                        argument("message", StringArgumentType.greedyString())
                            .executes { context ->
                                val player = context.source.player ?: return@executes 0
                                val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
                                val message = StringArgumentType.getString(context, "message")
                                identifierHandler(player, communityIdentifier) { targetCommunity ->
                                    ChatRoomHandler.sendChatMessage(player, targetCommunity, message)
                                    1
                                }
                            }
                    )
            )
    )
}
