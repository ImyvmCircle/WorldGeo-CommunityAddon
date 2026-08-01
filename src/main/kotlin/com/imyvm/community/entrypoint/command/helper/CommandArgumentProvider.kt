package com.imyvm.community.entrypoint.command.helper

import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.community.infra.CommunityDatabase.communities
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack

val LIST_TYPE_PROVIDER: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
    suggestMatching(builder, listOf("recruiting", "auditing", "active", "all", "revoked", "join_able"), builder.remaining, contains = false)
    builder.buildFuture()
}

val COMMUNITY_TYPE_PROVIDER: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
    suggestMatching(builder, listOf("manor", "realm"), builder.remaining, contains = false)
    builder.buildFuture()
}

val BINARY_CHOICE_SUGGESTION_PROVIDER: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
    suggestMatching(builder, listOf("yes", "no"), builder.remaining, contains = false)
    builder.buildFuture()
}

val BUILDING_SURVIVAL_BLOCK_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    suggestMatching(builder, CommunityBuildingService.listSurvivalBlockSuggestions(), builder.remaining)
    builder.buildFuture()
}

val BUILDING_SELECTABLE_BLOCK_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val suggestions = CommunityBuildingService.getSelectablePool()
        .flatMap { listOf(it.baseBlockId.substringAfter(":"), it.baseBlockId) }
        .distinct()
    suggestMatching(builder, suggestions, builder.remaining)
    builder.buildFuture()
}

val BUILDING_LINKED_BLOCKS_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val remaining = builder.remaining
    val separator = maxOf(remaining.lastIndexOf(','), remaining.lastIndexOf(' '))
    val prefix = if (separator >= 0) remaining.substring(0, separator + 1) else ""
    val token = if (separator >= 0) remaining.substring(separator + 1) else remaining
    suggestMatching(builder, CommunityBuildingService.listSurvivalBlockSuggestions(), token, prefix)
    builder.buildFuture()
}

private fun suggestMatching(
    builder: SuggestionsBuilder,
    suggestions: Iterable<String>,
    token: String,
    prefix: String = "",
    contains: Boolean = true,
    limit: Int = 50
) {
    val normalizedToken = token.trim().trim('"').lowercase()
    suggestions
        .filter {
            val value = it.lowercase()
            normalizedToken.isEmpty() || if (contains) value.contains(normalizedToken) else value.startsWith(normalizedToken)
        }
        .take(limit)
        .forEach { builder.suggest(prefix + it) }
}

private fun suggestCommunityNames(builder: SuggestionsBuilder, names: Iterable<String>) {
    val token = builder.remaining.trim().trim('"')
    val suggestions = names.map { name -> if (name.all { it.isLetterOrDigit() && it.code < 128 }) name else "\"$name\"" }
    suggestMatching(builder, suggestions, token, contains = true, limit = 100)
}

val FORMAL_MEMBER_UUID_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    communities.flatMap { community ->
        community.member.entries
            .filter { (_, account) -> account.basicRoleType == MemberRoleType.OWNER || account.basicRoleType == MemberRoleType.ADMIN || account.basicRoleType == MemberRoleType.MEMBER }
            .map { it.key.toString() }
    }.distinct().let { suggestMatching(builder, it, builder.remaining, contains = true, limit = 100) }
    builder.buildFuture()
}

val FISCAL_POLICY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    suggestMatching(builder, CommunityFiscalPolicy.entries.map { it.name.lowercase() }, builder.remaining, contains = false)
    builder.buildFuture()
}

val FISCAL_WEEK_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val suggestions = buildList {
        RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK]
            ?.let { add("${it.timelineId}:${it.periodId}") }
        addAll(communities.flatMap { community ->
            community.fiscalState.memberObservations.values.map { it.weekKey } +
                community.fiscalState.settlements.map { it.weekKey } +
                community.fiscalState.settledWeekKeys
        })
    }.distinct()
    suggestMatching(builder, suggestions, builder.remaining, contains = true, limit = 100)
    builder.buildFuture()
}

val JOINABLE_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities
        .filter { it.status != CommunityStatus.REVOKED_MANOR && it.status != CommunityStatus.REVOKED_REALM }
        .mapNotNull { it.getRegion()?.name }
    suggestCommunityNames(builder, names)
    builder.buildFuture()
}

val PENDING_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.PENDING_MANOR || it.status == CommunityStatus.PENDING_REALM }
        .mapNotNull { it.getRegion()?.name }
    suggestCommunityNames(builder, names)
    builder.buildFuture()
}

val RECRUITING_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.RECRUITING_REALM }
        .mapNotNull { it.getRegion()?.name }
    suggestCommunityNames(builder, names)
    builder.buildFuture()
}

val ACTIVE_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities
        .filter { it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.ACTIVE_REALM }
        .mapNotNull { it.getRegion()?.name }
    suggestCommunityNames(builder, names)
    builder.buildFuture()
}

val ALL_COMMUNITY_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
    val names = communities.mapNotNull { it.getRegion()?.name }
    suggestCommunityNames(builder, names)
    builder.buildFuture()
}
