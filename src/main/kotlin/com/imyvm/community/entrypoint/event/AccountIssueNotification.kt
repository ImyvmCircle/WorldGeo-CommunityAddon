package com.imyvm.community.entrypoint.event

import com.imyvm.community.application.communication.appendOpExceptionNotification
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.util.Translator
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.MinecraftServer
import java.util.UUID

fun registerAccountIssueNotification() {
    AccountSubsystem.onReady { runtime ->
        scanForOpExceptions(runtime, runtime.server, null)
    }
    ServerPlayConnectionEvents.JOIN.register join@ { handler, _, _ ->
        val player = handler.player
        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) return@join
        findIssue(player.level().server, player.uuid, null)
    }
}

private fun scanForOpExceptions(runtime: AccountSubsystem.Runtime, server: MinecraftServer, token: String?) {
    runtime.store.scanUnresolved(token, 64).whenComplete { page, error ->
        if (error != null) return@whenComplete
        page.items.filter { it.status == AccountTransactionStatus.NEEDS_OP }.forEach { state ->
            appendOpExceptionNotification(0,
                "NEEDS_OP:${state.transaction.shortId}:${state.transaction.subjectName ?: "?"}:${state.failureStage ?: "UNKNOWN"}"
            )
        }
        if (page.items.size == 64 && page.nextToken != null) {
            scanForOpExceptions(runtime, server, page.nextToken)
        }
    }
}

private fun findIssue(server: MinecraftServer, playerUuid: UUID, token: String?) {
    val runtime = AccountSubsystem.runtimeOrNull() ?: return
    runtime.store.scanUnresolved(token, 64).whenComplete { page, error ->
        if (error != null) return@whenComplete
        if (page.items.any { it.status == AccountTransactionStatus.NEEDS_OP }) {
            server.execute {
                val online = server.playerList.getPlayer(playerUuid) ?: return@execute
                if (!Commands.LEVEL_GAMEMASTERS.check(online.permissions())) return@execute
                online.sendSystemMessage(Translator.tr("command.community.money.issues.login").copy().withStyle { style ->
                    style.withClickEvent(ClickEvent.RunCommand("/community money issues"))
                        .withHoverEvent(HoverEvent.ShowText(Translator.tr("command.community.money.issue.hover")))
                })
            }
        } else if (page.items.size == 64 && page.nextToken != null) {
            findIssue(server, playerUuid, page.nextToken)
        }
    }
}
