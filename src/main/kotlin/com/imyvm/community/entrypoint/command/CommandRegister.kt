package com.imyvm.community.entrypoint.command

import com.imyvm.community.application.interaction.command.*
import com.imyvm.community.application.interaction.common.*
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.runCancelCommunityBuildingOperation
import com.imyvm.community.application.interaction.screen.inner_community.runConfirmCommunityBuildingOperation
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingCandidates
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingMenu
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingPoolMenu
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.command.helper.*
import com.imyvm.community.entrypoint.screen.outer_community.MainMenu
import com.imyvm.community.util.getColoredDimensionName
import com.imyvm.community.util.getPlayerDimensionId
import com.imyvm.community.util.SelectionReturnContext
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.inter.api.PlayerInteractionApi.resetSelection
import com.imyvm.iwg.inter.api.PlayerInteractionApi.startSelection
import com.imyvm.iwg.inter.api.PlayerInteractionApi.stopSelection
import com.imyvm.iwg.inter.register.command.helper.SHAPE_TYPE_SUGGESTION_PROVIDER
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.CommandSourceStack
import java.util.*

fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
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
                    .requires{ net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions())}
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runForceDeleteCommunity(it) }
                    )
            )
            .then(
                literal("audit")
                    .requires{ net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions())}
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
                    .requires{ net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions())}
                    .then(
                        argument("communityIdentifier", StringArgumentType.string())
                            .suggests(ALL_COMMUNITY_PROVIDER)
                            .executes{ runForceRevoke(it) }
                    )
            )
            .then(
                literal("force_active")
                    .requires{ net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions())}
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
                            .requires { net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }
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
            .then(
                literal("money")
                    .requires { net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }
                    .then(
                        literal("test")
                            .then(
                                literal("credit")
                                    .then(
                                        argument("target", StringArgumentType.word())
                                            .suggests(MONEY_PLAYER_PROVIDER)
                                            .then(
                                                argument("amount", StringArgumentType.word())
                                                    .executes { runMoneyTest(it, AccountDirection.CREDIT) }
                                            )
                                    )
                            )
                            .then(
                                literal("debit")
                                    .then(
                                        argument("target", StringArgumentType.word())
                                            .suggests(MONEY_PLAYER_PROVIDER)
                                            .then(
                                                argument("amount", StringArgumentType.word())
                                                    .executes { runMoneyTest(it, AccountDirection.DEBIT) }
                                            )
                                    )
                            )
                    )
                    .then(literal("issues").executes(::runMoneyIssues))
                    .then(
                        literal("issue")
                            .then(
                                argument("shortId", StringArgumentType.word())
                                    .suggests(MONEY_ISSUE_PROVIDER)
                                    .executes(::runMoneyIssue)
                            )
                    )
                    .then(
                        literal("action")
                            .then(
                                argument("shortId", StringArgumentType.word())
                                    .suggests(MONEY_ISSUE_PROVIDER)
                                    .then(
                                        argument("action", StringArgumentType.word())
                                            .suggests(MONEY_ACTION_PROVIDER)
                                            .executes(::runMoneyAction)
                                    )
                            )
                    )
                    .then(literal("reconcile").executes(::runMoneyReconcile))
            )
            .then(
                literal("building")
                    .then(
                        literal("status")
                            .executes { runBuildingStatus(it, null) }
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ALL_COMMUNITY_PROVIDER)
                                    .executes { runBuildingStatus(it, StringArgumentType.getString(it, "communityIdentifier")) }
                            )
                    )
                    .then(
                        literal("open")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ALL_COMMUNITY_PROVIDER)
                                    .executes { runOpenBuildingMenuCommand(it) }
                            )
                    )
                    .then(
                        literal("confirm")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ALL_COMMUNITY_PROVIDER)
                                    .executes { runBuildingConfirm(it) }
                            )
                    )
                    .then(
                        literal("cancel")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ALL_COMMUNITY_PROVIDER)
                                    .executes { runBuildingCancel(it) }
                            )
                    )
                    .then(
                        literal("candidates")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ALL_COMMUNITY_PROVIDER)
                                    .executes { runOpenBuildingCandidatesCommand(it) }
                            )
                    )
                    .then(
                        literal("settle")
                            .requires { net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }
                            .then(
                                literal("hour")
                                    .executes { runBuildingSettleHour(it) }
                            )
                            .then(
                                literal("week")
                                    .executes { runBuildingSettleWeek(it) }
                            )
                    )
                    .then(
                        literal("pool")
                            .requires { net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }
                            .then(
                                literal("menu")
                                    .executes { runOpenBuildingPoolMenuCommand(it) }
                            )
                            .then(
                                literal("list")
                                    .executes { runBuildingPoolList(it) }
                            )
                            .then(
                                literal("remove")
                                    .then(
                                        argument("blockId", StringArgumentType.string())
                                            .suggests(BUILDING_SELECTABLE_BLOCK_PROVIDER)
                                            .executes { runBuildingPoolRemove(it) }
                                    )
                            )
                            .then(
                                literal("add")
                                    .then(
                                        argument("blockId", StringArgumentType.string())
                                            .suggests(BUILDING_SURVIVAL_BLOCK_PROVIDER)
                                            .then(
                                                argument("unitCost", IntegerArgumentType.integer(1))
                                                    .then(
                                                        argument("reward", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                                                            .executes { runBuildingPoolAdd(it, null) }
                                                            .then(
                                                                argument("linkedBlocks", StringArgumentType.greedyString())
                                                                    .executes { runBuildingPoolAdd(it, StringArgumentType.getString(it, "linkedBlocks")) }
                                                            )
                                                    )
                                            )
                                    )
                            )
                        )
                    )


            .then(
                literal("fiscal")
                    .requires { net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }
                    .then(
                        literal("policy")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("policy", StringArgumentType.word())
                                            .suggests(FISCAL_POLICY_PROVIDER)
                                            .then(
                                                argument("currentWeek", StringArgumentType.word())
                                                    .then(
                                                        argument("nextWeek", StringArgumentType.word())
                                                            .then(
                                                                argument("cooldownUntilWeek", StringArgumentType.word())
                                                                    .executes { runFiscalPolicy(it) }
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("observe")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("targetUuid", StringArgumentType.word())
                                            .then(
                                                argument("weekKey", StringArgumentType.word())
                                                    .then(
                                                        argument("balance", com.mojang.brigadier.arguments.LongArgumentType.longArg(0L))
                                                            .executes { runFiscalObserve(it) }
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("tax_preview")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(argument("weekKey", StringArgumentType.word()).suggests(FISCAL_WEEK_PROVIDER).executes { runFiscalTaxPreview(it) })
                            )
                    )
                    .then(
                        literal("welfare_preview")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(argument("weekKey", StringArgumentType.word()).suggests(FISCAL_WEEK_PROVIDER).executes { runFiscalWelfarePreview(it) })
                            )
                    )
                    .then(
                        literal("settle")
                            .then(argument("weekKey", StringArgumentType.word()).suggests(FISCAL_WEEK_PROVIDER).executes { runFiscalSettle(it) })
                    )
                    .then(
                        literal("history")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .executes { runFiscalHistory(it) }
                            )
                    )
            )
            .then(
                literal("development")
                    .then(
                        literal("status")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .executes { runDevelopmentStatus(it) }
                            )
                    )
                    .then(
                        literal("refresh")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .executes { runDevelopmentRefresh(it) }
                            )
                    )
                    .then(
                        literal("preview")
                            .requires { net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("weekKey", StringArgumentType.word())
                                            .then(
                                                argument("totalBuildingIncome", com.mojang.brigadier.arguments.LongArgumentType.longArg(0L))
                                                    .then(
                                                        argument("weekBuildingIncome", com.mojang.brigadier.arguments.LongArgumentType.longArg(0L))
                                                            .then(
                                                                argument("weekActiveMembers", IntegerArgumentType.integer(0))
                                                                    .then(
                                                                        argument("totalHabitationMillis", com.mojang.brigadier.arguments.LongArgumentType.longArg(0L))
                                                                            .then(
                                                                                argument("averageHabitationMillis", com.mojang.brigadier.arguments.LongArgumentType.longArg(0L))
                                                                                    .executes { runDevelopmentPreview(it) }
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("land_price")
                    .then(
                        literal("status")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .executes { runLandPriceStatus(it) }
                            )
                    )
                    .then(
                        literal("refresh")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .executes { runLandPriceRefresh(it) }
                            )
                    )
                    .then(
                        literal("preview")
                            .requires { net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("area", StringArgumentType.word())
                                            .then(
                                                argument("total25HabitationMillis", com.mojang.brigadier.arguments.LongArgumentType.longArg(0L))
                                                    .then(
                                                        argument("theoreticalBuildingIncome", com.mojang.brigadier.arguments.LongArgumentType.longArg(0L))
                                                            .executes { runLandPricePreview(it) }
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("title")
                    .then(
                        literal("status")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ALL_COMMUNITY_PROVIDER)
                                    .executes { runTitleStatus(it) }
                            )
                    )
                    .then(
                        literal("buy_foreman_slot")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .executes { runTitleBuyForemanSlot(it) }
                            )
                    )
                    .then(
                        literal("grant_foreman")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("targetUuid", StringArgumentType.word())
                                            .executes { runTitleGrantForeman(it) }
                                    )
                            )
                    )
                    .then(
                        literal("revoke_foreman")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("targetUuid", StringArgumentType.word())
                                            .executes { runTitleRevokeForeman(it) }
                                    )
                            )
                    )
                    .then(
                        literal("select")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ACTIVE_COMMUNITY_PROVIDER)
                                    .executes { runTitleSelect(it) }
                            )
                    )
            )
            .then(
                literal("treasury")
                    .requires { net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }
                    .then(
                        literal("deposit")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ALL_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                                            .executes { runAdminTreasuryDeposit(it, null) }
                                            .then(
                                                argument("description", StringArgumentType.greedyString())
                                                    .executes { runAdminTreasuryDeposit(it, StringArgumentType.getString(it, "description")) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("withdraw")
                            .then(
                                argument("communityIdentifier", StringArgumentType.string())
                                    .suggests(ALL_COMMUNITY_PROVIDER)
                                    .then(
                                        argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                                            .executes { runAdminTreasuryWithdraw(it, null) }
                                            .then(
                                                argument("description", StringArgumentType.greedyString())
                                                    .executes { runAdminTreasuryWithdraw(it, StringArgumentType.getString(it, "description")) }
                                            )
                                    )
                            )
                    )
            )
    )
}


private fun runInitialUI(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val selectionContext = SelectionReturnContext.getContext(player.uuid)
    if (selectionContext != null && ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)) {
        when (selectionContext) {
            is SelectionReturnContext.Context.CreateScope -> {
                val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(selectionContext.regionNumberId)
                if (community != null) {
                    val runBack: (net.minecraft.server.level.ServerPlayer) -> Unit = { p ->
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
                    val runBackToScopeList: (net.minecraft.server.level.ServerPlayer) -> Unit = { p ->
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

private fun runStartSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val result = startSelection(player)
    if (result == 1) {
        player.sendSystemMessage(Translator.tr("community.selection_mode.enabled"))
        player.sendSystemMessage(Translator.tr("community.selection_mode.dimension_hint", getColoredDimensionName(getPlayerDimensionId(player))))
    }
    return result
}

private fun runStopSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val result = stopSelection(player)
    if (result == 1) {
        player.sendSystemMessage(Translator.tr("community.selection_mode.disabled"))
    }
    return result
}

private fun runResetSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val result = resetSelection(player)
    if (result == 1) {
        player.sendSystemMessage(Translator.tr("community.selection_mode.reset"))
    }
    return result
}

private fun runCreateCommunity(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityType = StringArgumentType.getString(context, "communityType").lowercase(Locale.getDefault())
    val name = StringArgumentType.getString(context, "name")
    val shapeName = StringArgumentType.getString(context, "shapeType").uppercase(Locale.getDefault())
    val shapeType = GeoShapeType.entries.find { it.name == shapeName } ?: GeoShapeType.RECTANGLE
    return onCreateCommunityRequest(player, communityType, name, shapeType)
}

private fun runConfirmCommunityCreation(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return onConfirmCommunityCreation(player, regionId)
}

private fun runCancelCommunityCreation(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return onCancelCommunityCreation(player, regionId)
}

private fun runConfirmScopeModification(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return onConfirmScopeModification(player, regionId, scopeName)
}

private fun runCancelScopeModification(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return onCancelScopeModification(player, regionId, scopeName)
}

private fun runConfirmScopeDeletion(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return onConfirmScopeDeletion(player, regionId, scopeName)
}

private fun runCancelScopeDeletion(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return onCancelScopeDeletion(player, regionId, scopeName)
}

private fun runConfirmTeleportPointSetting(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return com.imyvm.community.application.interaction.screen.inner_community.administration_only.onConfirmTeleportPointSetting(player, regionId, scopeName)
}

private fun runCancelTeleportPointSetting(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return com.imyvm.community.application.interaction.screen.inner_community.administration_only.onCancelTeleportPointSetting(player, regionId, scopeName)
}

private fun runForceDeleteCommunity(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onForceDeleteCommunity(player, targetCommunity) }
}

private fun runAudit(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val choice = StringArgumentType.getString(context, "choice").lowercase(Locale.getDefault())
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onAudit(player, choice, targetCommunity) }
}

private fun runForceRevoke(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onForceRevoke(player, targetCommunity) }
}

private fun runForceActive(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity -> onForceActive(player, targetCommunity) }
}

private fun runJoin(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onJoinCommunity(
            player,
            targetCommunity
        )
    }
}

private fun runLeave(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onLeaveCommunity(
            player,
            targetCommunity
        )
    }
}

private fun runHelpCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onHelpCommand(player)
}

private fun runListCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val type = try {
        val communityTypeString = StringArgumentType.getString(context, "communityType").uppercase()
        CommunityListFilterType.valueOf(communityTypeString)
    } catch (e: IllegalArgumentException) {
        CommunityListFilterType.ALL
    }
    return onListCommunities(player, type)
}

private fun runQueryCommunityRegion(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        val region = targetCommunity.getRegion() ?: return@identifierHandler
        onQueryCommunityRegion(player, region)
    }
}

private fun runAnnouncementCreate(context: CommandContext<CommandSourceStack>, communityIdentifier: String, content: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementCreateCommand(context, targetCommunity, content)
    }
}

private fun runAnnouncementDelete(context: CommandContext<CommandSourceStack>, communityIdentifier: String, announcementId: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementDeleteCommand(context, targetCommunity, announcementId)
    }
}

private fun runAnnouncementList(context: CommandContext<CommandSourceStack>, communityIdentifier: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementListCommand(context, targetCommunity)
    }
}

private fun runAnnouncementView(context: CommandContext<CommandSourceStack>, communityIdentifier: String, announcementId: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementViewCommand(context, targetCommunity, announcementId)
    }
}

private fun runAnnouncementOpList(context: CommandContext<CommandSourceStack>): Int {
    return onAnnouncementOpListCommand(context)
}

private fun runAnnouncementOpDelete(context: CommandContext<CommandSourceStack>, communityIdentifier: String, announcementId: String): Int {
    val player = context.source.player ?: return 0
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAnnouncementOpDeleteCommand(context, targetCommunity, announcementId)
    }
}

private fun runAcceptInvitation(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        com.imyvm.community.application.interaction.common.onAcceptInvitation(player, targetCommunity)
        1
    }
}

private fun runRejectInvitation(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        com.imyvm.community.application.interaction.common.onRejectInvitation(player, targetCommunity)
        1
    }
}

private fun runSendChatMessage(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    val message = StringArgumentType.getString(context, "message")
    
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        com.imyvm.community.application.interaction.common.ChatRoomHandler.sendChatMessage(player, targetCommunity, message)
        1
    }
}

private fun runToggleChatChannel(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        com.imyvm.community.application.interaction.common.ChatRoomHandler.toggleChatChannel(player, targetCommunity)
        1
    }
}

private fun runConfirmSettingChange(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.onConfirmSettingChange(player, regionId)
}

private fun runCancelSettingChange(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.onCancelSettingChange(player, regionId)
}

private fun runConfirmRenameCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val nameKey = StringArgumentType.getString(context, "nameKey")
    return com.imyvm.community.application.interaction.screen.inner_community.multi_parent.onConfirmRename(player, regionId, nameKey)
}

private fun runCancelRenameCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val nameKey = StringArgumentType.getString(context, "nameKey")
    return com.imyvm.community.application.interaction.screen.inner_community.multi_parent.onCancelRename(player, regionId, nameKey)
}

private fun runOpenMenuCommand(context: CommandContext<CommandSourceStack>): Int {
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

private fun runOpenAnnouncementsCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        val role = community.getMemberRole(player.uuid)
        val isAdmin = role == com.imyvm.community.domain.model.community.MemberRoleType.OWNER ||
            role == com.imyvm.community.domain.model.community.MemberRoleType.ADMIN
        val isFormalMember = role == com.imyvm.community.domain.model.community.MemberRoleType.MEMBER
        val runBack: (net.minecraft.server.level.ServerPlayer) -> Unit = { p ->
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
            player.sendSystemMessage(Translator.tr("community.notfound.name", communityIdentifier))
            return@identifierHandler
        }
    }
}

private fun runOpenTeleportAdminCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(regionId) ?: run {
        player.sendSystemMessage(Translator.tr("community.notfound.id", regionId.toString()))
        return 0
    }
    if (!canOpenAdministrationCommand(player, community, AdminPrivilege.MANAGE_TELEPORT_POINTS)) return 0
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

private fun runOpenRenameMenuCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(regionId) ?: run {
        player.sendSystemMessage(Translator.tr("community.notfound.id", regionId.toString()))
        return 0
    }
    if (!canOpenAdministrationCommand(player, community, AdminPrivilege.RENAME_COMMUNITY)) return 0
    val runBack: (net.minecraft.server.level.ServerPlayer) -> Unit = { p ->
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

private fun runOpenModifyMenuCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(regionId) ?: run {
        player.sendSystemMessage(Translator.tr("community.notfound.id", regionId.toString()))
        return 0
    }
    if (!canOpenAdministrationCommand(player, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)) return 0
    val runBack: (net.minecraft.server.level.ServerPlayer) -> Unit = { p ->
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

private fun canOpenAdministrationCommand(
    player: net.minecraft.server.level.ServerPlayer,
    community: Community,
    privilege: AdminPrivilege
): Boolean {
    val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(player, community, privilege)
    val result = if (adminCheck.isDenied()) {
        adminCheck
    } else {
        CommunityPermissionPolicy.canExecuteOperationInProto(player, community, privilege)
    }
    if (result.isAllowed()) return true
    result.sendSuccess(player)
    return false
}

private fun runAcceptTerritoryGrant(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return com.imyvm.community.application.interaction.common.onAcceptTerritoryGrant(player, regionId, scopeName)
}

private fun runDeclineTerritoryGrant(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return com.imyvm.community.application.interaction.common.onDeclineTerritoryGrant(player, regionId, scopeName)
}

private fun runCancelTerritoryGrant(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    val scopeName = StringArgumentType.getString(context, "scopeName")
    return com.imyvm.community.application.interaction.common.onCancelTerritoryGrant(player, regionId, scopeName)
}

private fun runAcceptTreasuryGrant(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return com.imyvm.community.application.interaction.screen.inner_community.administration_only.onAcceptTreasuryGrant(player, regionId)
}

private fun runDeclineTreasuryGrant(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return com.imyvm.community.application.interaction.screen.inner_community.administration_only.onDeclineTreasuryGrant(player, regionId)
}

private fun runCancelTreasuryGrant(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val regionId = IntegerArgumentType.getInteger(context, "regionId")
    return com.imyvm.community.application.interaction.screen.inner_community.administration_only.onCancelTreasuryGrant(player, regionId)
}

private fun runAdminTreasuryDeposit(context: CommandContext<CommandSourceStack>, description: String?): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    val amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "amount")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAdminTreasuryDeposit(player, targetCommunity, amount, description)
    }
}

private fun runAdminTreasuryWithdraw(context: CommandContext<CommandSourceStack>, description: String?): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    val amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "amount")
    return identifierHandler(player, communityIdentifier) { targetCommunity ->
        onAdminTreasuryWithdraw(player, targetCommunity, amount, description)
    }
}

fun registerCommun(dispatcher: CommandDispatcher<CommandSourceStack>) {
    dispatcher.register(
        literal("_commun")
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
            .then(
                literal("accept_territory_grant")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runAcceptTerritoryGrant(it) }
                            )
                    )
            )
            .then(
                literal("decline_territory_grant")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runDeclineTerritoryGrant(it) }
                            )
                    )
            )
            .then(
                literal("cancel_territory_grant")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runCancelTerritoryGrant(it) }
                            )
                    )
            )
            .then(
                literal("accept_treasury_grant")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runAcceptTreasuryGrant(it) }
                    )
            )
            .then(
                literal("decline_treasury_grant")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runDeclineTreasuryGrant(it) }
                    )
            )
            .then(
                literal("cancel_treasury_grant")
                    .then(
                        argument("regionId", IntegerArgumentType.integer())
                            .executes { runCancelTreasuryGrant(it) }
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
    )
}

private fun runBuildingStatus(context: CommandContext<CommandSourceStack>, communityIdentifier: String?): Int {
    val player = context.source.player ?: return 0
    if (communityIdentifier != null) {
        return identifierHandler(player, communityIdentifier) { community -> sendBuildingStatus(player, community) }
    }
    val current = CommunityBuildingService.findCommunityAt(player)
    if (current != null && CommunityBuildingService.canView(current, player.uuid)) return sendBuildingStatus(player, current)
    return sendBuildingSummary(player)
}

private fun sendBuildingStatus(player: net.minecraft.server.level.ServerPlayer, community: Community): Int {
    if (!CommunityBuildingService.canView(community, player.uuid)) {
        player.sendSystemMessage(Translator.tr("command.community.building.status.no_permission", community.generateCommunityMark()))
        return 0
    }
    val status = CommunityBuildingService.getPlayerBuildingStatus(community, player.uuid)
    val state = community.buildingState
    player.sendSystemMessage(Translator.tr("command.community.building.status.header", community.generateCommunityMark(), status.weekId))
    player.sendSystemMessage(Translator.tr("command.community.building.status.income", CommunityBuildingService.formatMoney(status.income), status.pendingPayouts.toString()))
    player.sendSystemMessage(Translator.tr("command.community.building.status.base_cap", CommunityBuildingService.formatMoney(status.baseUsed), CommunityBuildingService.formatMoney(status.baseCap), CommunityBuildingService.formatMoney(status.baseRemaining)))
    player.sendSystemMessage(Translator.tr("command.community.building.status.extra_cap", CommunityBuildingService.formatMoney(status.extraUsed), CommunityBuildingService.formatMoney(status.extraCap), CommunityBuildingService.formatMoney(status.extraRemaining), if (status.foreman) Translator.tr("community.building.value.yes").string else Translator.tr("community.building.value.no").string))
    player.sendSystemMessage(Translator.tr("command.community.building.status.package", state.activeEntries().size.toString(), state.usedCapacityUnits().toString(), state.capacityUnits.toString(), CommunityBuildingService.getNextHourSettlementText()))
    return 1
}

private fun sendBuildingSummary(player: net.minecraft.server.level.ServerPlayer): Int {
    val statuses = CommunityBuildingService.listPlayerBuildingStatuses(player.uuid)
    if (statuses.isEmpty()) {
        player.sendSystemMessage(Translator.tr("command.community.building.summary.empty"))
        return 1
    }
    val total = statuses.fold(0L) { acc, status -> Math.addExact(acc, status.income) }
    val baseRemaining = statuses.first().baseRemaining
    player.sendSystemMessage(Translator.tr("command.community.building.summary.header", CommunityBuildingService.formatMoney(total), CommunityBuildingService.formatMoney(baseRemaining), statuses.size.toString()))
    statuses.forEach { status ->
        player.sendSystemMessage(Translator.tr("command.community.building.summary.entry", status.community.generateCommunityMark(), CommunityBuildingService.formatMoney(status.income), CommunityBuildingService.formatMoney(status.extraRemaining), status.pendingPayouts.toString()))
    }
    return 1
}

private fun runOpenBuildingMenuCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        runOpenCommunityBuildingMenu(player, community) { p -> CommunityMenuOpener.open(p) { s -> MainMenu(s, p) } }
    }
}

private fun runBuildingConfirm(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> runConfirmCommunityBuildingOperation(player, community) }
}

private fun runBuildingCancel(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> runCancelCommunityBuildingOperation(player, community) }
}

private fun runOpenBuildingCandidatesCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        runOpenCommunityBuildingCandidates(player, community, 0) { p -> CommunityMenuOpener.open(p) { s -> MainMenu(s, p) } }
    }
}

private fun runOpenBuildingPoolMenuCommand(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    runOpenCommunityBuildingPoolMenu(player, 0) { p -> CommunityMenuOpener.open(p) { s -> MainMenu(s, p) } }
    return 1
}

private fun runBuildingPoolList(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val entries = CommunityBuildingService.getSelectablePool()
    if (entries.isEmpty()) {
        player.sendSystemMessage(Translator.tr("command.community.building.pool.list.empty"))
        return 1
    }
    player.sendSystemMessage(Translator.tr("command.community.building.pool.list.header", entries.size.toString()))
    entries.forEach { entry ->
        player.sendSystemMessage(
            Translator.tr(
                "command.community.building.pool.list.entry",
                entry.baseBlockId,
                entry.unitCost.toString(),
                CommunityBuildingService.formatMoney(entry.rewardPerBlock),
                if (entry.linkedBlockIds.isEmpty()) "-" else entry.linkedBlockIds.joinToString(",")
            )
        )
    }
    return 1
}

private fun runBuildingPoolRemove(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val blockId = StringArgumentType.getString(context, "blockId")
    val result = CommunityBuildingService.removeSelectableEntry(blockId)
    if (result.isSuccess) {
        player.sendSystemMessage(Translator.tr("command.community.building.pool.remove.success", blockId))
        return 1
    }
    player.sendSystemMessage(Translator.tr("command.community.building.pool.remove.failed", blockId, result.exceptionOrNull()?.message ?: "error"))
    return 0
}

private fun runBuildingPoolAdd(context: CommandContext<CommandSourceStack>, linkedBlocksArg: String?): Int {
    val player = context.source.player ?: return 0
    val blockId = StringArgumentType.getString(context, "blockId")
    val unitCost = IntegerArgumentType.getInteger(context, "unitCost")
    val reward = (com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "reward") * 100.0).toLong()
    val linkedBlocks = linkedBlocksArg
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: CommunityBuildingService.inferLinkedBlockIds(blockId)
    val result = CommunityBuildingService.addOrUpdateSelectableEntry(blockId, unitCost, reward, linkedBlocks)
    if (result.isSuccess) {
        player.sendSystemMessage(
            Translator.tr(
                "command.community.building.pool.add.success",
                blockId,
                unitCost.toString(),
                CommunityBuildingService.formatMoney(reward),
                if (linkedBlocks.isEmpty()) "-" else linkedBlocks.joinToString(",")
            )
        )
        return 1
    }
    player.sendSystemMessage(Translator.tr("command.community.building.pool.add.failed", blockId, result.exceptionOrNull()?.message ?: "error"))
    return 0
}


private fun runBuildingSettleHour(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val result = CommunityBuildingService.settleCurrentHour()
    if (result.isSuccess) {
        val summary = result.getOrThrow()
        player.sendSystemMessage(Translator.tr("command.community.building.settle.success", "hour", summary.settledCommunities.toString(), summary.skippedCommunities.toString(), summary.playerTransactions.toString(), CommunityBuildingService.formatMoney(summary.communityIncome)))
        return 1
    }
    player.sendSystemMessage(Translator.tr("command.community.building.settle.failed", result.exceptionOrNull()?.message ?: "error"))
    return 0
}

private fun runBuildingSettleWeek(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val result = CommunityBuildingService.settleCurrentWeek()
    if (result.isSuccess) {
        val summary = result.getOrThrow()
        player.sendSystemMessage(Translator.tr("command.community.building.settle.success", "week", summary.settledCommunities.toString(), summary.skippedCommunities.toString(), summary.playerTransactions.toString(), CommunityBuildingService.formatMoney(summary.communityIncome)))
        return 1
    }
    player.sendSystemMessage(Translator.tr("command.community.building.settle.failed", result.exceptionOrNull()?.message ?: "error"))
    return 0
}

private fun runTitleStatus(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> onTitleStatus(player, community) }
}

private fun runTitleBuyForemanSlot(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> onTitleBuyForemanSlot(player, community) }
}

private fun runTitleGrantForeman(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    val target = UUID.fromString(StringArgumentType.getString(context, "targetUuid"))
    return identifierHandler(player, communityIdentifier) { community -> onTitleGrantForeman(player, community, target) }
}

private fun runTitleRevokeForeman(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    val target = UUID.fromString(StringArgumentType.getString(context, "targetUuid"))
    return identifierHandler(player, communityIdentifier) { community -> onTitleRevokeForeman(player, community, target) }
}

private fun runTitleSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> onTitleSelect(player, community) }
}

private fun runFiscalPolicy(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        onFiscalPolicy(player, community, StringArgumentType.getString(context, "policy"), StringArgumentType.getString(context, "currentWeek"), StringArgumentType.getString(context, "nextWeek"), StringArgumentType.getString(context, "cooldownUntilWeek"))
    }
}

private fun runFiscalObserve(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    val target = UUID.fromString(StringArgumentType.getString(context, "targetUuid"))
    val balance = com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "balance")
    return identifierHandler(player, communityIdentifier) { community -> onFiscalObserve(player, community, target, StringArgumentType.getString(context, "weekKey"), balance) }
}

private fun runFiscalTaxPreview(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> onFiscalTaxPreview(player, community, StringArgumentType.getString(context, "weekKey")) }
}

private fun runFiscalWelfarePreview(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> onFiscalWelfarePreview(player, community, StringArgumentType.getString(context, "weekKey")) }
}

private fun runFiscalSettle(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onFiscalSettle(player, StringArgumentType.getString(context, "weekKey"))
}

private fun runFiscalHistory(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> onFiscalHistory(player, community) }
}

private fun runDevelopmentStatus(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> onDevelopmentStatus(player, community) }
}

private fun runDevelopmentRefresh(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        val permission = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_BUILDING)
        if (permission.isDenied() && !net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
            permission.sendSuccess(player)
        } else {
            onDevelopmentRefresh(player, community)
        }
    }
}

private fun runLandPriceStatus(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community -> onLandPriceStatus(player, community) }
}

private fun runLandPriceRefresh(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        val permission = CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_BUILDING)
        if (permission.isDenied() && !net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
            permission.sendSuccess(player)
        } else {
            onLandPriceRefresh(player, community)
        }
    }
}

private fun runDevelopmentPreview(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        onDevelopmentPreview(
            player,
            community,
            StringArgumentType.getString(context, "weekKey"),
            com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "totalBuildingIncome"),
            com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "weekBuildingIncome"),
            IntegerArgumentType.getInteger(context, "weekActiveMembers"),
            com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "totalHabitationMillis"),
            com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "averageHabitationMillis")
        )
    }
}

private fun runLandPricePreview(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val communityIdentifier = StringArgumentType.getString(context, "communityIdentifier")
    return identifierHandler(player, communityIdentifier) { community ->
        onLandPricePreview(
            player,
            community,
            StringArgumentType.getString(context, "area"),
            com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "total25HabitationMillis"),
            com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "theoreticalBuildingIncome")
        )
    }
}
