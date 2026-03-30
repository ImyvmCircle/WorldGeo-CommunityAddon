package com.imyvm.community.entrypoint.command.helper

import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.infra.CommunityDatabase.communities
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import java.util.concurrent.CompletableFuture

val LIST_TYPE_PROVIDER: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
    listOf("recruiting", "auditing", "active", "all", "revoked", "join_able").forEach { builder.suggest(it) }
    CompletableFuture.completedFuture(builder.build())
}

val COMMUNITY_TYPE_PROVIDER: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
    listOf("manor", "realm").forEach { builder.suggest(it) }
    CompletableFuture.completedFuture(builder.build())
}

val BINARY_CHOICE_SUGGESTION_PROVIDER: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
    listOf("yes", "no").forEach { builder.suggest(it) }
    CompletableFuture.completedFuture(builder.build())
}

val JOINABLE_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities
        .filter { it.status != CommunityStatus.REVOKED_MANOR && it.status != CommunityStatus.REVOKED_REALM }
        .mapNotNull { it.getRegion()?.name }
    names.forEach { name -> if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name) }
    builder.buildFuture()
}

val PENDING_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.PENDING_MANOR || it.status == CommunityStatus.PENDING_REALM }
        .mapNotNull { it.getRegion()?.name }
    names.forEach { name -> if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name) }
    builder.buildFuture()
}

val RECRUITING_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.RECRUITING_REALM }
        .mapNotNull { it.getRegion()?.name }
    names.forEach { name -> if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name) }
    builder.buildFuture()
}

val ACTIVE_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.ACTIVE_REALM }
        .mapNotNull { it.getRegion()?.name }
    names.forEach { name -> if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name) }
    builder.buildFuture()
}

val ALL_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities.mapNotNull { it.getRegion()?.name }
    names.forEach { name -> if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name) }
    builder.buildFuture()
}
