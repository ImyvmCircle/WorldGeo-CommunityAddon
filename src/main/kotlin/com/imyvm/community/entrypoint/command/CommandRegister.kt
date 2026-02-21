package com.imyvm.community.entrypoint.command

import com.imyvm.community.application.interaction.command.*
import com.imyvm.community.application.interaction.common.*
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.entrypoint.command.helper.*
import com.imyvm.community.entrypoint.screen.outer_community.MainMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.PlayerInteractionApi.resetSelection
import com.imyvm.iwg.inter.api.PlayerInteractionApi.startSelection
import com.imyvm.iwg.inter.api.PlayerInteractionApi.stopSelection
import com.imyvm.iwg.inter.register.command.helper.SHAPE_TYPE_SUGGESTION_PROVIDER
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
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
                literal("leave")
                    .then(
                        argument("communityIdentifier", StringArgumentType.greedyString())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runLeave(it) }
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
            .then(
                literal("announcement")
                    .then(
                        literal("create")
                            .then(
                                argument("communityIdentifier", StringArgumentType.word())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("content", StringArgumentType.greedyString())
                                            .executes { context ->
                                                val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
                                                val content = StringArgumentType.getString(context, "content")
                                                runAnnouncementCreate(context, communityIdentifier, content)
                                            }
                                    )
                            )
                    )
                    .then(
                        literal("delete")
                            .then(
                                argument("communityIdentifier", StringArgumentType.word())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("announcementId", StringArgumentType.word())
                                            .executes { context ->
                                                val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
                                                val announcementId = StringArgumentType.getString(context, "announcementId")
                                                runAnnouncementDelete(context, communityIdentifier, announcementId)
                                            }
                                    )
                            )
                    )
                    .then(
                        literal("list")
                            .then(
                                argument("communityIdentifier", StringArgumentType.word())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .executes { context ->
                                        val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
                                        runAnnouncementList(context, communityIdentifier)
                                    }
                            )
                    )
                    .then(
                        literal("view")
                            .then(
                                argument("communityIdentifier", StringArgumentType.word())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("announcementId", StringArgumentType.word())
                                            .executes { context ->
                                                val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
                                                val announcementId = StringArgumentType.getString(context, "announcementId")
                                                runAnnouncementView(context, communityIdentifier, announcementId)
                                            }
                                    )
                            )
                    )
                    .then(
                        literal("op")
                            .requires { it.hasPermissionLevel(2) }
                            .then(
                                literal("list")
                                    .executes { context ->
                                        runAnnouncementOpList(context)
                                    }
                            )
                            .then(
                                literal("delete")
                                    .then(
                                        argument("communityIdentifier", StringArgumentType.word())
                                            .suggests(ALL_COMMUNITY_PROVIDER)
                                            .then(
                                                argument("announcementId", StringArgumentType.word())
                                                    .executes { context ->
                                                        val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
                                                        val announcementId = StringArgumentType.getString(context, "announcementId")
                                                        runAnnouncementOpDelete(context, communityIdentifier, announcementId)
                                                    }
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("accept_invitation")
                    .then(
                        argument("communityIdentifier", StringArgumentType.greedyString())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes { runAcceptInvitation(it) }
                    )
            )
            .then(
                literal("reject_invitation")
                    .then(
                        argument("communityIdentifier", StringArgumentType.greedyString())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes { runRejectInvitation(it) }
                    )
            )
            .then(
                literal("chat")
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .then(
                                argument("message", StringArgumentType.greedyString())
                                    .executes { runSendChatMessage(it) }
                            )
                    )
            )
            .then(
                literal("chat_channel")
                    .then(
                        argument("communityIdentifier", StringArgumentType.greedyString())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes { runToggleChatChannel(it) }
                    )
            )
            .then(
                literal("confirm_creation")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runConfirmCommunityCreation(it) }
                    )
            )
            .then(
                literal("cancel_creation")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runCancelCommunityCreation(it) }
                    )
            )
            .then(
                literal("confirm_modification")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.greedyString())
                                    .executes { runConfirmScopeModification(it) }
                            )
                    )
            )
            .then(
                literal("cancel_modification")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.greedyString())
                                    .executes { runCancelScopeModification(it) }
                            )
                    )
            )
            .then(
                literal("confirm_teleport_point_set")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.greedyString())
                                    .executes { runConfirmTeleportPointSetting(it) }
                            )
                    )
            )
            .then(
                literal("cancel_teleport_point_set")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.greedyString())
                                    .executes { runCancelTeleportPointSetting(it) }
                            )
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
    val result = startSelection(player)
    if (result == 1) {
        player.sendMessage(Translator.tr("community.selection_mode.enabled"))
    }
    return result
}

private fun runStopSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val result = stopSelection(player)
    if (result == 1) {
        player.sendMessage(Translator.tr("community.selection_mode.disabled"))
    }
    return result
}

private fun runResetSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val result = resetSelection(player)
    if (result == 1) {
        player.sendMessage(Translator.tr("community.selection_mode.reset"))
    }
    return result
}

private fun runCreateCommunity(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityType = StringArgumentType.getString(context, "communityType").lowercase(Locale.getDefault())
    val name = StringArgumentType.getString(context, "name")
    val shapeName = StringArgumentType.getString(context, "shapeType").uppercase(Locale.getDefault())
    return onCreateCommunityRequest(player, communityType, name, shapeName)
}

private fun runConfirmCommunityCreation(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return onConfirmCommunityCreation(player, regionId)
}

private fun runCancelCommunityCreation(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return onCancelCommunityCreation(player, regionId)
}

private fun runConfirmScopeModification(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return onConfirmScopeModification(player, regionId, scopeName)
}

private fun runCancelScopeModification(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return onCancelScopeModification(player, regionId, scopeName)
}

private fun runConfirmTeleportPointSetting(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return com.imyvm.community.application.interaction.screen.inner_community.administration_only.onConfirmTeleportPointSetting(player, regionId, scopeName)
}

private fun runCancelTeleportPointSetting(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return com.imyvm.community.application.interaction.screen.inner_community.administration_only.onCancelTeleportPointSetting(player, regionId, scopeName)
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
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onJoinCommunity(
            player,
            targetCommunity
        )
    }
}

private fun runLeave(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onLeaveCommunity(
            player,
            targetCommunity
        )
    }
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

private fun runAnnouncementCreate(context: CommandContext<ServerCommandSource>, communityIdentifier: String, content: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementCreateCommand(context, targetCommunity, content)
    }
}

private fun runAnnouncementDelete(context: CommandContext<ServerCommandSource>, communityIdentifier: String, announcementId: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementDeleteCommand(context, targetCommunity, announcementId)
    }
}

private fun runAnnouncementList(context: CommandContext<ServerCommandSource>, communityIdentifier: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementListCommand(context, targetCommunity)
    }
}

private fun runAnnouncementView(context: CommandContext<ServerCommandSource>, communityIdentifier: String, announcementId: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementViewCommand(context, targetCommunity, announcementId)
    }
}

private fun runAnnouncementOpList(context: CommandContext<ServerCommandSource>): Int {
    return onAnnouncementOpListCommand(context)
}

private fun runAnnouncementOpDelete(context: CommandContext<ServerCommandSource>, communityIdentifier: String, announcementId: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementOpDeleteCommand(context, targetCommunity, announcementId)
    }
}

private fun runAcceptInvitation(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        com.imyvm.community.application.interaction.common.onAcceptInvitation(player, targetCommunity)
        1
    }
}

private fun runRejectInvitation(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        com.imyvm.community.application.interaction.common.onRejectInvitation(player, targetCommunity)
        1
    }
}

private fun runSendChatMessage(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    val message = StringArgumentType.getString(context, "message")
    
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        com.imyvm.community.application.interaction.common.ChatRoomHandler.sendChatMessage(player, targetCommunity, message)
        1
    }
}

private fun runToggleChatChannel(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        com.imyvm.community.application.interaction.common.ChatRoomHandler.toggleChatChannel(player, targetCommunity)
        1
    }
}
