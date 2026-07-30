package com.imyvm.community.application.interaction.command

import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountInspection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionState
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.account.ManualAccountAction
import com.imyvm.community.domain.model.account.MoneyAmount
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.util.Translator
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.CompletableFuture

fun runMoneyTest(context: CommandContext<CommandSourceStack>, direction: AccountDirection): Int {
    val op = context.source.player ?: return 0
    val runtime = AccountSubsystem.runtimeOrNull() ?: return unavailable(op)
    val targetName = context.getArgument("target", String::class.java)
    val amount = try {
        MoneyAmount.parseCents(context.getArgument("amount", String::class.java))
    } catch (_: RuntimeException) {
        op.sendSystemMessage(Translator.tr("command.community.money.invalid_amount"))
        return 0
    }
    resolveTarget(context.source, targetName).whenComplete { target, error ->
        context.source.server.execute {
            if (error != null || target == null) {
                opIfOnline(op)?.sendSystemMessage(Translator.tr("command.community.money.player_not_found", targetName))
                return@execute
            }
            val id = UUID.randomUUID()
            val transaction = AccountTransaction(
                id,
                id.toString().replace("-", "").take(12).uppercase(),
                System.currentTimeMillis(),
                "manual",
                target.first,
                target.second,
                amount,
                direction,
                "OP_TEST",
                "op-test:$id"
            )
            runtime.service.submit(transaction) { terminal ->
                val initiator = opIfOnline(op) ?: return@submit
                if (terminal.status == AccountTransactionStatus.SUCCEEDED) {
                    initiator.sendSystemMessage(Translator.tr(
                        "command.community.money.test.succeeded",
                        terminal.transaction.shortId,
                        terminal.transaction.subjectName,
                        format(terminal.transaction.amount),
                        format(terminal.finalBalance)
                    ))
                } else {
                    initiator.sendSystemMessage(Translator.tr(
                        "command.community.money.test.rejected",
                        terminal.transaction.shortId,
                        terminal.failureStage ?: "BUSINESS_REJECTED"
                    ))
                }
            }.whenComplete { accepted, submitError ->
                context.source.server.execute {
                    val initiator = opIfOnline(op) ?: return@execute
                    if (submitError != null) {
                        initiator.sendSystemMessage(Translator.tr("command.community.money.failed"))
                    } else {
                        initiator.sendSystemMessage(detailLink(
                            Translator.tr("command.community.money.test.accepted", accepted.transaction.shortId),
                            accepted.transaction.shortId
                        ))
                    }
                }
            }
        }
    }
    return 1
}

fun runMoneyIssues(context: CommandContext<CommandSourceStack>): Int {
    val op = context.source.player ?: return 0
    val runtime = AccountSubsystem.runtimeOrNull() ?: return unavailable(op)
    collectIssues(runtime, null, ArrayList()).whenComplete { issues, error ->
        context.source.server.execute {
            val player = opIfOnline(op) ?: return@execute
            if (error != null) player.sendSystemMessage(Translator.tr("command.community.money.failed"))
            else if (issues.isEmpty()) player.sendSystemMessage(Translator.tr("command.community.money.issues.none"))
            else {
                player.sendSystemMessage(Translator.tr("command.community.money.issues.header", issues.size))
                issues.forEach { state ->
                    player.sendSystemMessage(detailLink(Translator.tr(
                        "command.community.money.issues.entry",
                        state.transaction.shortId,
                        state.transaction.subjectName ?: "?",
                        format(state.transaction.amount),
                        state.transaction.direction.name
                    ), state.transaction.shortId))
                }
            }
        }
    }
    return 1
}

fun runMoneyIssue(context: CommandContext<CommandSourceStack>): Int {
    val op = context.source.player ?: return 0
    val runtime = AccountSubsystem.runtimeOrNull() ?: return unavailable(op)
    val shortId = context.getArgument("shortId", String::class.java)
    runtime.operator.inspect(shortId).thenCombine(runtime.operator.audit(shortId, 10)) { inspection, audit ->
        inspection to audit.size
    }.whenComplete { result, error ->
        context.source.server.execute {
            val player = opIfOnline(op) ?: return@execute
            if (error != null) {
                player.sendSystemMessage(Translator.tr("command.community.money.issue.not_found", shortId))
                return@execute
            }
            sendInspection(player, result.first, result.second)
        }
    }
    return 1
}

fun runMoneyAction(context: CommandContext<CommandSourceStack>): Int {
    val op = context.source.player ?: return 0
    val runtime = AccountSubsystem.runtimeOrNull() ?: return unavailable(op)
    val shortId = context.getArgument("shortId", String::class.java)
    val action = when (context.getArgument("action", String::class.java).lowercase()) {
        "confirm_applied" -> ManualAccountAction.CONFIRM_APPLIED
        "close_unchanged" -> ManualAccountAction.CLOSE_UNCHANGED
        "retry_original" -> ManualAccountAction.RETRY_ORIGINAL
        else -> {
            op.sendSystemMessage(Translator.tr("command.community.money.action.invalid"))
            return 0
        }
    }
    runtime.operator.act(shortId, action, op.gameProfile.name).whenComplete { updated, error ->
        context.source.server.execute {
            val player = opIfOnline(op) ?: return@execute
            if (error != null) player.sendSystemMessage(Translator.tr("command.community.money.action.rejected"))
            else player.sendSystemMessage(Translator.tr(
                "command.community.money.action.completed", shortId, updated.status.name
            ))
        }
    }
    return 1
}

fun runMoneyReconcile(context: CommandContext<CommandSourceStack>): Int {
    val op = context.source.player ?: return 0
    val runtime = AccountSubsystem.runtimeOrNull() ?: return unavailable(op)
    reconcilePage(runtime, op.gameProfile.name, null, 0, 0).whenComplete { counts, error ->
        context.source.server.execute {
            val player = opIfOnline(op) ?: return@execute
            if (error != null) player.sendSystemMessage(Translator.tr("command.community.money.failed"))
            else player.sendSystemMessage(Translator.tr(
                "command.community.money.reconcile.completed", counts.first, counts.second
            ))
        }
    }
    return 1
}

private fun resolveTarget(source: CommandSourceStack, name: String): CompletableFuture<Pair<UUID, String>?> {
    val online = source.server.playerList.getPlayerByName(name)
    if (online != null) return CompletableFuture.completedFuture(online.uuid to online.gameProfile.name)
    val runtime = AccountSubsystem.runtimeOrNull() ?: return CompletableFuture.completedFuture(null)
    return runtime.writer.submit { runtime.identities.findByName(name) }
        .thenApply { it?.let { identity -> identity.uuid to identity.trustedName } }
}

private fun collectIssues(
    runtime: AccountSubsystem.Runtime,
    token: String?,
    result: ArrayList<AccountTransactionState>
): CompletableFuture<List<AccountTransactionState>> = runtime.store.scanUnresolved(token, 64).thenCompose { page ->
    page.items.filterTo(result) { it.status == AccountTransactionStatus.NEEDS_OP }
    if (result.size >= 20 || page.items.size < 64 || page.nextToken == null) {
        CompletableFuture.completedFuture(result.take(20))
    } else collectIssues(runtime, page.nextToken, result)
}

private fun reconcilePage(
    runtime: AccountSubsystem.Runtime,
    actorName: String,
    token: String?,
    examined: Int,
    confirmed: Int
): CompletableFuture<Pair<Int, Int>> = runtime.store.scanUnresolved(token, 64).thenCompose { page ->
    val issueFutures = page.items.filter { it.status == AccountTransactionStatus.NEEDS_OP }.map { state ->
        runtime.operator.inspect(state.transaction.shortId).thenCompose { inspection ->
            val expected = inspection.state.attempts.lastOrNull { it.callStartedAtMillis != null }?.expectedBalance
            if (expected == inspection.currentBalance) {
                runtime.operator.act(state.transaction.shortId, ManualAccountAction.CONFIRM_APPLIED, actorName)
                    .thenApply { 1 }
            } else CompletableFuture.completedFuture(0)
        }
    }
    CompletableFuture.allOf(*issueFutures.toTypedArray()).thenCompose {
        val nextExamined = examined + issueFutures.size
        val nextConfirmed = confirmed + issueFutures.sumOf(CompletableFuture<Int>::join)
        if (page.items.size == 64 && page.nextToken != null) {
            reconcilePage(runtime, actorName, page.nextToken, nextExamined, nextConfirmed)
        } else CompletableFuture.completedFuture(nextExamined to nextConfirmed)
    }
}

private fun sendInspection(player: ServerPlayer, inspection: AccountInspection, auditCount: Int) {
    val state = inspection.state
    val attempt = state.attempts.lastOrNull { it.callStartedAtMillis != null }
    player.sendSystemMessage(Translator.tr(
        "command.community.money.issue.detail",
        state.transaction.shortId,
        inspection.trustedName,
        state.transaction.direction.name,
        format(state.transaction.amount),
        format(attempt?.balanceBefore),
        format(attempt?.expectedBalance),
        format(inspection.currentBalance),
        formatTime(attempt?.callStartedAtMillis),
        auditCount
    ))
    if (state.status == AccountTransactionStatus.NEEDS_OP) {
        player.sendSystemMessage(actionLink("command.community.money.action.confirm_applied", state.transaction.shortId, "confirm_applied"))
        player.sendSystemMessage(actionLink("command.community.money.action.close_unchanged", state.transaction.shortId, "close_unchanged"))
        player.sendSystemMessage(actionLink("command.community.money.action.retry_original", state.transaction.shortId, "retry_original"))
    }
}

private fun detailLink(label: Component, shortId: String): Component = label.copy().withStyle { style ->
    style.withClickEvent(ClickEvent.RunCommand("/community money issue $shortId"))
        .withHoverEvent(HoverEvent.ShowText(Translator.tr("command.community.money.issue.hover")))
}

private fun actionLink(key: String, shortId: String, action: String): Component = Translator.tr(key).copy().withStyle { style ->
    style.withClickEvent(ClickEvent.RunCommand("/community money action $shortId $action"))
        .withHoverEvent(HoverEvent.ShowText(Translator.tr("command.community.money.action.hover")))
}

private fun opIfOnline(original: ServerPlayer): ServerPlayer? = original.level().server.playerList
    .getPlayer(original.uuid)?.takeIf { Commands.LEVEL_GAMEMASTERS.check(it.permissions()) }

private fun unavailable(player: ServerPlayer): Int {
    player.sendSystemMessage(Translator.tr("command.community.money.unavailable"))
    return 0
}

private fun format(value: Long?): String = value?.let { "%.2f".format(java.util.Locale.ROOT, it / 100.0) } ?: "-"

private fun formatTime(value: Long?): String = value?.let {
    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
    )
} ?: "not_started"
