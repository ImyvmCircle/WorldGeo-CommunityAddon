package com.imyvm.community.entrypoints.command

import com.imyvm.community.application.interaction.command.*
import com.imyvm.community.application.interaction.common.onCreateCommunity
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.community.CommunityListFilterType
import com.imyvm.community.entrypoints.command.helper.*
import com.imyvm.community.entrypoints.screen.outer_community.MainMenu
import com.imyvm.iwg.inter.api.PlayerInteractionApi.resetSelection
import com.imyvm.iwg.inter.api.PlayerInteractionApi.startSelection
import com.imyvm.iwg.inter.api.PlayerInteractionApi.stopSelection
import com.imyvm.iwg.inter.register.command.helper.SHAPE_TYPE_SUGGESTION_PROVIDER
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import java.util.*

fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
    dispatcher.register(
        literal("community")
            .executes{ runInitialUI(it) }
            .then(
                literal("select")
                    .then(
                        literal("start")
                            .executes { runStartSelect(it) }
                    )
                    .then(
                        literal("stop")
                            .executes { runStopSelect(it) }
                    )
                    .then(
                        literal("reset")
                            .executes { runResetSelect(it) }
                    )
            )
            .then(
                literal("create")
                    .then(
                        argument("shapeType", StringArgumentType.word())
                            .suggests(SHAPE_TYPE_SUGGESTION_PROVIDER)
                            .then(
                                argument("communityType", StringArgumentType.word())
                                    .suggests(COMMUNITY_TYPE_PROVIDER)
                                    .then(
                                        argument("name", StringArgumentType.greedyString())
                                            .executes { runCreateCommunity(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("force_delete")
                    .requires{ it.hasPermissionLevel(2)}
                    .then(
                        argument("communityIdentifier", StringArgumentType.greedyString())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runForceDeleteCommunity(it) }
                    )
            )
            .then(
                literal("audit")
                    .requires{ it.hasPermissionLevel(2)}
                    .then(
                        argument("choice", StringArgumentType.word())
                            .suggests(BINARY_CHOICE_SUGGESTION_PROVIDER)
                            .then(
                                argument("communityIdentifier", StringArgumentType.greedyString())
                                    .suggests(PENDING_COMMUNITY_PROVIDER)
                                    .executes{ runAudit(it) }
                            )
                    )
            )
            .then(
                literal("force_revoke")
                    .requires{ it.hasPermissionLevel(2)}
                    .then(
                        argument("communityIdentifier", StringArgumentType.greedyString())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runForceRevoke(it) }
                    )
            )
            .then(
                literal("force_active")
                    .requires{ it.hasPermissionLevel(2)}
                    .then(
                        argument("communityIdentifier", StringArgumentType.greedyString())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runForceActive(it) }
                    )
            )
            .then(
                literal("join")
                    .then(
                        argument("communityIdentifier", StringArgumentType.greedyString())
                            .suggests(JOINABLE_COMMUNITY_PROVIDER)
                            .executes{ runJoin(it) }
                    )
            )
            .then(
                literal("help")
                    .executes{ runHelpCommand(it) }
            )
            .then(
                literal("list")
                    .executes{ runListCommand(it) }
                    .then(
                        argument("communityType", StringArgumentType.word())
                            .suggests(LIST_TYPE_PROVIDER)
                            .executes{ runListCommand(it) }
                    )
            )
            .then(
                literal("query")
                    .then(
                        argument("communityIdentifier", StringArgumentType.word())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runQueryCommunityRegion(it) }
                    )
            )
    )
}

private fun runInitialUI(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    CommunityMenuOpener.open(player) { syncId ->
        MainMenu(
            syncId,
            player
        )
    }
    return 1
}

private fun runStartSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return startSelection(player)
}

private fun runStopSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return stopSelection(player)
}

private fun runResetSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return resetSelection(player)
}

private fun runCreateCommunity(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityType = StringArgumentType.getString(context, "communityType").lowercase(Locale.getDefault())
    val name = StringArgumentType.getString(context, "name")
    val shapeName = StringArgumentType.getString(context, "shapeType").uppercase(Locale.getDefault())
    return onCreateCommunity(player, communityType, name, shapeName)
}

private fun runForceDeleteCommunity(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onForceDeleteCommunity(player, targetCommunity) }
}

private fun runAudit(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val choice = StringArgumentType.getString(context, "choice").lowercase(Locale.getDefault())
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onAudit(player, choice, targetCommunity) }
}

private fun runForceRevoke(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onForceRevoke(player, targetCommunity) }
}

private fun runForceActive(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onForceActive(player, targetCommunity) }
}

private fun runJoin(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onJoinCommunity(player, targetCommunity) }
}

private fun runHelpCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return onHelpCommand(player)
}

private fun runListCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val type = try {
        val communityTypeString = StringArgumentType.getString(context, "communityType").uppercase()
        CommunityListFilterType.valueOf(communityTypeString)
    } catch (e: IllegalArgumentException) {
        CommunityListFilterType.ALL
    }
    return onListCommunities(player, type)
}

private fun runQueryCommunityRegion(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        val region = targetCommunity.getRegion() ?: return@identifierHandler
        onQueryCommunityRegion(player, region)
    }
}
