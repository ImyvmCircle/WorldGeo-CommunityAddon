package com.imyvm.community.entrypoints.command.helper

import com.imyvm.community.domain.community.CommunityStatus
import com.imyvm.community.infra.CommunityDatabase.communities
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.server.command.ServerCommandSource
import java.util.concurrent.CompletableFuture

val LIST_TYPE_PROVIDER: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
    listOf("recruiting", "auditing", "active", "all", "revoked", "join_able").forEach { builder.suggest(it) }
    CompletableFuture.completedFuture(builder.build())
}

val COMMUNITY_TYPE_PROVIDER: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
    listOf("manor", "realm").forEach { builder.suggest(it) }
    CompletableFuture.completedFuture(builder.build())
}

val BINARY_CHOICE_SUGGESTION_PROVIDER: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
    listOf("yes", "no").forEach { builder.suggest(it) }
    CompletableFuture.completedFuture(builder.build())
}

val JOINABLE_COMMUNITY_PROVIDER = SuggestionProvider<ServerCommandSource> { _, builder ->
    val names = communities
        .filter { it.status != CommunityStatus.REVOKED_MANOR && it.status != CommunityStatus.REVOKED_REALM }
        .mapNotNull { it.getRegion()?.name }
    names.forEach { builder.suggest(it) }
    builder.buildFuture()
}

val PENDING_COMMUNITY_PROVIDER = SuggestionProvider<ServerCommandSource> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.PENDING_MANOR || it.status == CommunityStatus.PENDING_REALM }
        .mapNotNull { it.getRegion()?.name }
    names.forEach { builder.suggest(it) }
    builder.buildFuture()
}

val RECRUITING_COMMUNITY_PROVIDER = SuggestionProvider<ServerCommandSource> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.RECRUITING_REALM }
        .mapNotNull { it.getRegion()?.name }
    names.forEach { builder.suggest(it) }
    builder.buildFuture()
}

val ACTIVE_COMMUNITY_PROVIDER = SuggestionProvider<ServerCommandSource> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.ACTIVE_REALM }
        .mapNotNull { it.getRegion()?.name }
    names.forEach { builder.suggest(it) }
    builder.buildFuture()
}

val ALL_COMMUNITY_PROVIDER = SuggestionProvider<ServerCommandSource> { _, builder ->
    val names = communities.mapNotNull { it.getRegion()?.name }
    names.forEach { builder.suggest(it) }
    builder.buildFuture()
}
