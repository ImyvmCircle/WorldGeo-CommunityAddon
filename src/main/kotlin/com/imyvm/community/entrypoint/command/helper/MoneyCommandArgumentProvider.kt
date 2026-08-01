package com.imyvm.community.entrypoint.command.helper

import com.imyvm.community.infra.account.AccountSubsystem
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack

val MONEY_PLAYER_PROVIDER = SuggestionProvider<CommandSourceStack> { context, builder ->
    val prefix = builder.remaining.trim().trim('"')
    val online = context.source.server.playerList.players
        .map { it.gameProfile.name }
        .filter { it.contains(prefix, ignoreCase = true) }
    val runtime = AccountSubsystem.runtimeOrNull()
    if (runtime == null) {
        online.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).forEach(builder::suggest)
        builder.buildFuture()
    } else {
        runtime.writer.submit { runtime.identities.suggestNames(prefix, 100) }.thenApply { offline ->
            (online + offline).distinctBy(String::lowercase)
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .take(100)
                .forEach(builder::suggest)
            builder.build()
        }
    }
}

val MONEY_ACTION_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val prefix = builder.remaining.trim()
    listOf("confirm_applied", "close_unchanged", "retry_original")
        .filter { prefix.isEmpty() || it.startsWith(prefix, ignoreCase = true) }
        .forEach(builder::suggest)
    builder.buildFuture()
}


val MONEY_ISSUE_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val runtime = AccountSubsystem.runtimeOrNull()
    if (runtime == null) builder.buildFuture() else {
        runtime.store.scanUnresolved(null, 100).thenApply { page ->
            page.items.filter { it.status == com.imyvm.community.domain.model.account.AccountTransactionStatus.NEEDS_OP }
                .map { it.transaction.shortId }
                .filter { builder.remaining.isBlank() || it.contains(builder.remaining.trim(), ignoreCase = true) }
                .forEach(builder::suggest)
            builder.build()
        }
    }
}
