package com.imyvm.community.entrypoint.command

import com.imyvm.community.application.interaction.command.*
import com.imyvm.community.application.interaction.common.*
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.entrypoint.command.helper.*
import com.imyvm.community.entrypoint.screen.outer_community.MainMenu
import com.imyvm.community.util.SelectionReturnContext
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
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
                                        argument("name", StringArgumentType.string())
                                            .executes { runCreateCommunity(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("force_delete")
                    .requires{ it.hasPermissionLevel(2)}
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
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
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(PENDING_COMMUNITY_PROVIDER)
                                    .executes{ runAudit(it) }
                            )
                    )
            )
            .then(
                literal("force_revoke")
                    .requires{ it.hasPermissionLevel(2)}
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runForceRevoke(it) }
                    )
            )
            .then(
                literal("force_active")
                    .requires{ it.hasPermissionLevel(2)}
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runForceActive(it) }
                    )
            )
            .then(
                literal("join")
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(JOINABLE_COMMUNITY_PROVIDER)
                            .executes{ runJoin(it) }
                    )
            )
            .then(
                literal("leave")
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
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
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runQueryCommunityRegion(it) }
                    )
            )
            .then(
                literal("announcement")
                    .then(
                        literal("create")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
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
                                argument("communityIdentifier", StringArgumentType.string())
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
                                argument("communityIdentifier", StringArgumentType.string())
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
                                argument("communityIdentifier", StringArgumentType.string())
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
                                        argument("communityIdentifier", StringArgumentType.string())
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
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes { runAcceptInvitation(it) }
                    )
            )
            .then(
                literal("reject_invitation")
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
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
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes { runToggleChatChannel(it) }
                    )
            )
            .then(
                literal("open_menu")
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
                            .executes { runOpenMenuCommand(it) }
                    )
            )
            .then(
                literal("open_announcements")
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
                            .executes { runOpenAnnouncementsCommand(it) }
                    )
            )
            .then(
                literal("open_teleport_admin")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runOpenTeleportAdminCommand(it) }
                            )
                    )
            )
            .then(
                literal("open_rename_menu")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runOpenRenameMenuCommand(it) }
                    )
            )
            .then(
                literal("open_modify_menu")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runOpenModifyMenuCommand(it) }
                    )
            )
    )
}

private fun runInitialUI(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val selectionContext = SelectionReturnContext.getContext(player.uuid)
    if (selectionContext != null && ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)) {
        when (selectionContext) {
            is SelectionReturnContext.Context.CreateScope -> {
                val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(selectionContext.regionNumberId)
                if (community != null) {
                    val runBack: (net.minecraft.server.network.ServerPlayerEntity) -> Unit = { p ->
                        CommunityMenuOpener.open(p) { s -> MainMenu(s, p) }
                    }
                    CommunityMenuOpener.open(player) { syncId ->
                        com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationMenu(
                            syncId, community, selectionContext.scopeName, player, runBack
                        )
                    }
                    return 1
                }
            }
            is SelectionReturnContext.Context.ModifyScope -> {
                val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(selectionContext.regionNumberId)
                val scope = community?.getRegion()?.geometryScope?.find { it.scopeName == selectionContext.scopeName }
                if (community != null && scope != null) {
                    val runBackToScopeList: (net.minecraft.server.network.ServerPlayerEntity) -> Unit = { p ->
                        CommunityMenuOpener.open(p) { s ->
                            com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu(
                                syncId = s,
                                playerExecutor = p,
                                community = community,
                                geographicFunctionType = com.imyvm.community.domain.model.GeographicFunctionType.GEOMETRY_MODIFICATION,
                                runBack = { pp -> CommunityMenuOpener.open(pp) { ss -> MainMenu(ss, pp) } }
                            )
                        }
                    }
                    com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runOpenScopeModificationConfirmation(
                        player, community, scope, runBackToScopeList
                    )
                    return 1
                }
            }
        }
    }
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
    return onCreateCommunityRequest(player, communityType, name)
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

private fun runConfirmScopeDeletion(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return onConfirmScopeDeletion(player, regionId, scopeName)
}

private fun runCancelScopeDeletion(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return onCancelScopeDeletion(player, regionId, scopeName)
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

private fun runConfirmSettingChange(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.onConfirmSettingChange(player, regionId)
}

private fun runCancelSettingChange(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.onCancelSettingChange(player, regionId)
}

private fun runConfirmRenameCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val nameKey = StringArgumentType.getString(context, "nameKey")
    return com.imyvm.community.application.interaction.screen.inner_community.multi_parent.onConfirmRename(player, regionId, nameKey)
}

private fun runCancelRenameCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val nameKey = StringArgumentType.getString(context, "nameKey")
    return com.imyvm.community.application.interaction.screen.inner_community.multi_parent.onCancelRename(player, regionId, nameKey)
}

private fun runOpenMenuCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        val role = community.getMemberRole(player.uuid)
        val isFormalMember = role != null &&
            role != com.imyvm.community.domain.model.community.MemberRoleType.APPLICANT &&
            role != com.imyvm.community.domain.model.community.MemberRoleType.REFUSED
        CommunityMenuOpener.open(player) { syncId ->
            if (isFormalMember) {
                com.imyvm.community.entrypoint.screen.inner_community.CommunityMenu(
                    syncId, player, community
                ) { p -> CommunityMenuOpener.open(p) { s -> MainMenu(s, p) } }
            } else {
                com.imyvm.community.entrypoint.screen.outer_community.NonMemberCommunityMenu(
                    syncId, player, community
                ) { p -> CommunityMenuOpener.open(p) { s -> MainMenu(s, p) } }
            }
        }
        1
    }
}

private fun runOpenAnnouncementsCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        val role = community.getMemberRole(player.uuid)
        val isAdmin = role == com.imyvm.community.domain.model.community.MemberRoleType.OWNER ||
            role == com.imyvm.community.domain.model.community.MemberRoleType.ADMIN
        val isFormalMember = role == com.imyvm.community.domain.model.community.MemberRoleType.MEMBER
        val runBack: (net.minecraft.server.network.ServerPlayerEntity) -> Unit = { p ->
            CommunityMenuOpener.open(p) { s -> MainMenu(s, p) }
        }
        if (isAdmin) {
            CommunityMenuOpener.open(player) { syncId ->
                com.imyvm.community.entrypoint.screen.inner_community.administration_only.annoucement.AdministrationAnnouncementListMenu(
                    syncId, community, player, 0, runBack
                )
            }
        } else if (isFormalMember) {
            CommunityMenuOpener.open(player) { syncId ->
                com.imyvm.community.entrypoint.screen.inner_community.affairs.annoucement.MemberAnnouncementListMenu(
                    syncId, community, player, 0, runBack
                )
            }
        } else {
            player.sendMessage(Translator.tr("community.notfound.name", communityIdentifier))
            return@identifierHandler
        }
    }
}

private fun runOpenTeleportAdminCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(regionId) ?: run {
        player.sendMessage(Translator.tr("community.notfound.id", regionId.toString()))
        return 0
    }
    val region = community.getRegion() ?: return 0
    val scope = region.geometryScope.find { it.scopeName == scopeName } ?: return 0
    CommunityMenuOpener.open(player) { syncId ->
        com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationTeleportPointMenu(
            syncId = syncId,
            playerExecutor = player,
            community = community,
            scope = scope,
            runBack = { p -> CommunityMenuOpener.open(p) { s -> MainMenu(s, p) } }
        )
    }
    return 1
}

private fun runOpenRenameMenuCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(regionId) ?: run {
        player.sendMessage(Translator.tr("community.notfound.id", regionId.toString()))
        return 0
    }
    val runBack: (net.minecraft.server.network.ServerPlayerEntity) -> Unit = { p ->
        CommunityMenuOpener.open(p) { s -> MainMenu(s, p) }
    }
    CommunityMenuOpener.open(player) { syncId ->
        com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = player,
            community = community,
            geographicFunctionType = com.imyvm.community.domain.model.GeographicFunctionType.NAME_MODIFICATION,
            runBack = runBack
        )
    }
    return 1
}

private fun runOpenModifyMenuCommand(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(regionId) ?: run {
        player.sendMessage(Translator.tr("community.notfound.id", regionId.toString()))
        return 0
    }
    val runBack: (net.minecraft.server.network.ServerPlayerEntity) -> Unit = { p ->
        CommunityMenuOpener.open(p) { s -> MainMenu(s, p) }
    }
    CommunityMenuOpener.open(player) { syncId ->
        com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = player,
            community = community,
            geographicFunctionType = com.imyvm.community.domain.model.GeographicFunctionType.GEOMETRY_MODIFICATION,
            runBack = runBack
        )
    }
    return 1
}

fun registerCommun(dispatcher: CommandDispatcher<ServerCommandSource>) {
    dispatcher.register(
        literal("commun")
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
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runConfirmScopeModification(it) }
                            )
                    )
            )
            .then(
                literal("cancel_modification")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runCancelScopeModification(it) }
                            )
                    )
            )
            .then(
                literal("confirm_delete_scope")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runConfirmScopeDeletion(it) }
                            )
                    )
            )
            .then(
                literal("cancel_delete_scope")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runCancelScopeDeletion(it) }
                            )
                    )
            )
            .then(
                literal("confirm_teleport_point_set")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runConfirmTeleportPointSetting(it) }
                            )
                    )
            )
            .then(
                literal("cancel_teleport_point_set")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runCancelTeleportPointSetting(it) }
                            )
                    )
            )
            .then(
                literal("confirm_setting")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runConfirmSettingChange(it) }
                    )
            )
            .then(
                literal("cancel_setting")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runCancelSettingChange(it) }
                    )
            )
            .then(
                literal("confirm_rename")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("nameKey", StringArgumentType.string())
                                    .executes { runConfirmRenameCommand(it) }
                            )
                    )
            )
            .then(
                literal("cancel_rename")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("nameKey", StringArgumentType.string())
                                    .executes { runCancelRenameCommand(it) }
                            )
                    )
            )
    )
}
