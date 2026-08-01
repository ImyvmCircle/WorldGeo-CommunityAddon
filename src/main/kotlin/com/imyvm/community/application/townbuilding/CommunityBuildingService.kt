package com.imyvm.community.application.townbuilding

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.account.mutateTreasury
import com.imyvm.community.application.title.CommunityTitleService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.community.CommunityBuildingCatalogEntry
import com.imyvm.community.domain.model.community.CommunityBuildingEntry
import com.imyvm.community.domain.model.community.CommunityBuildingState
import com.imyvm.community.domain.model.transaction.MemberLedgerFact
import com.imyvm.community.domain.model.transaction.PurposeCursorFact
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.CompleteNaturalPeriodTransition
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsCheckpointRequest
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsCheckpointStatus
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPageQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoPeriodDataStatus
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.math.ceil

object CommunityBuildingService {
    private const val MAX_RECOVERY_PERIODS = 256
    private val entryDrafts = mutableMapOf<UUID, CommunityBuildingDraft>()
    private val nonSurvivalBlockIds = setOf(
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:bedrock",
        "minecraft:barrier", "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw",
        "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
        "minecraft:light", "minecraft:debug_stick", "minecraft:test_block", "minecraft:test_instance_block"
    )
    private val woodFamilies = listOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak")
    private val stoneFamilies = listOf("stone_bricks", "deepslate_bricks", "deepslate_tiles", "mud_bricks", "nether_bricks", "red_nether_bricks", "end_stone_bricks", "prismarine", "prismarine_bricks", "dark_prismarine", "sandstone", "red_sandstone", "blackstone", "polished_blackstone_bricks", "tuff_bricks", "resin_bricks")
    val selectablePoolState: MutableList<CommunityBuildingCatalogEntry> = mutableListOf()

    fun register() {
        RegionDataApi.registerCompleteNaturalPeriodTransitionCallback(Consumer { transition ->
            val server = WorldGeoCommunityAddon.server ?: return@Consumer
            server.execute { settleTransition(transition) }
        })
        AccountSubsystem.onReady { runtime -> runtime.server.execute { recoverAvailablePeriods(runtime) } }
    }


    private fun recoverAvailablePeriods(runtime: AccountSubsystem.Runtime) {
        for (timeline in RegionDataApi.getAvailableNaturalPeriodTimelines()) {
            recoverAvailablePeriods(runtime, timeline.timelineId, timeline.closed, NaturalPeriodKind.HOUR)
            recoverAvailablePeriods(runtime, timeline.timelineId, timeline.closed, NaturalPeriodKind.WEEK)
        }
    }

    private fun recoverAvailablePeriods(runtime: AccountSubsystem.Runtime, timelineId: String, timelineClosed: Boolean, kind: NaturalPeriodKind) {
        val range = RegionDataApi.getAvailableNaturalPeriodRange(timelineId, kind) ?: return
        val latestClosed = if (timelineClosed) range.latest else previousPeriodKey(range.latest) ?: return
        if (comparePeriodIds(kind, latestClosed.periodId, range.earliest.periodId) < 0) return
        val start = firstRecoveryPeriod(runtime, latestClosed, range.earliest) ?: return
        for (periodKey in enumeratePeriodKeys(start, latestClosed).take(MAX_RECOVERY_PERIODS)) {
            val weekKey = if (kind == NaturalPeriodKind.WEEK) periodKey else weekKeyForPeriod(periodKey) ?: continue
            settlePeriod(periodKey, weekKey)
                .onFailure { WorldGeoCommunityAddon.logger.error("Failed to recover building ${kind.name.lowercase(Locale.ROOT)} ${periodLedgerKey(periodKey)}", it) }
        }
    }

    private fun firstRecoveryPeriod(runtime: AccountSubsystem.Runtime, latest: NaturalPeriodKey, earliest: NaturalPeriodKey): NaturalPeriodKey? {
        val activeRegionIds = CommunityDatabase.communities
            .mapNotNull { community -> community.regionNumberId?.takeIf { community.buildingState.activeEntries().isNotEmpty() } }
        if (activeRegionIds.isEmpty()) return null
        val cursorValues = activeRegionIds.mapNotNull { regionId ->
            val cursorUnit = "region:$regionId:${latest.timelineId}:${latest.kind.name.lowercase(Locale.ROOT)}"
            runtime.sharedStore.findCursor(regionId, "building", "region", cursorUnit).join()?.cursor
        }
        if (cursorValues.isEmpty()) return latest
        return cursorValues
            .mapNotNull { cursor -> cursor.removePrefix("${latest.timelineId}:").takeIf { it != cursor } }
            .mapNotNull { nextPeriodKey(latest.timelineId, latest.kind, it) }
            .filter { comparePeriodIds(latest.kind, it.periodId, earliest.periodId) >= 0 }
            .minWithOrNull { left, right -> comparePeriodIds(latest.kind, left.periodId, right.periodId) }
            ?: latest
    }

    private fun enumeratePeriodKeys(start: NaturalPeriodKey, latest: NaturalPeriodKey): Sequence<NaturalPeriodKey> = sequence {
        var current: NaturalPeriodKey? = start
        while (current != null && comparePeriodIds(latest.kind, current.periodId, latest.periodId) <= 0) {
            yield(current)
            current = nextPeriodKey(latest.timelineId, latest.kind, current.periodId)
        }
    }

    private fun nextPeriodKey(timelineId: String, kind: NaturalPeriodKind, periodId: String): NaturalPeriodKey? = runCatching {
        NaturalPeriodKey(timelineId, kind, when {
            periodId.startsWith("test:${kind.name.lowercase(Locale.ROOT)}:") -> {
                val prefix = "test:${kind.name.lowercase(Locale.ROOT)}:"
                prefix + (periodId.removePrefix(prefix).toLong() + 1L)
            }
            kind == NaturalPeriodKind.HOUR -> LocalDateTime.parse(periodId, HOUR_FORMATTER).plusHours(1).format(HOUR_FORMATTER)
            kind == NaturalPeriodKind.WEEK -> formatWeek(parseWeekStart(periodId).plusWeeks(1))
            else -> return null
        })
    }.getOrNull()

    private fun previousPeriodKey(key: NaturalPeriodKey): NaturalPeriodKey? = runCatching {
        NaturalPeriodKey(key.timelineId, key.kind, when {
            key.periodId.startsWith("test:${key.kind.name.lowercase(Locale.ROOT)}:") -> {
                val prefix = "test:${key.kind.name.lowercase(Locale.ROOT)}:"
                val previous = key.periodId.removePrefix(prefix).toLong() - 1L
                if (previous < 0L) return null
                prefix + previous
            }
            key.kind == NaturalPeriodKind.HOUR -> LocalDateTime.parse(key.periodId, HOUR_FORMATTER).minusHours(1).format(HOUR_FORMATTER)
            key.kind == NaturalPeriodKind.WEEK -> formatWeek(parseWeekStart(key.periodId).minusWeeks(1))
            else -> return null
        })
    }.getOrNull()

    private fun comparePeriodIds(kind: NaturalPeriodKind, left: String, right: String): Int = when {
        left.startsWith("test:") && right.startsWith("test:") -> left.substringAfterLast(':').toLong().compareTo(right.substringAfterLast(':').toLong())
        kind == NaturalPeriodKind.HOUR -> LocalDateTime.parse(left, HOUR_FORMATTER).compareTo(LocalDateTime.parse(right, HOUR_FORMATTER))
        kind == NaturalPeriodKind.WEEK -> parseWeekStart(left).compareTo(parseWeekStart(right))
        else -> left.compareTo(right)
    }

    private fun weekKeyForPeriod(periodKey: NaturalPeriodKey): NaturalPeriodKey? = when {
        periodKey.kind == NaturalPeriodKind.WEEK -> periodKey
        periodKey.kind != NaturalPeriodKind.HOUR -> null
        periodKey.periodId.startsWith("test:hour:") -> NaturalPeriodKey(
            periodKey.timelineId,
            NaturalPeriodKind.WEEK,
            "test:week:${periodKey.periodId.substringAfterLast(':').toLong() / 168L}"
        )
        else -> NaturalPeriodKey(periodKey.timelineId, NaturalPeriodKind.WEEK, formatWeek(LocalDateTime.parse(periodKey.periodId, HOUR_FORMATTER).toLocalDate()))
    }

    private fun parseWeekStart(periodId: String): LocalDate = LocalDate.parse("$periodId-1", DateTimeFormatter.ISO_WEEK_DATE)

    private fun formatWeek(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        return String.format(Locale.ROOT, "%04d-W%02d", date.get(weekFields.weekBasedYear()), date.get(weekFields.weekOfWeekBasedYear()))
    }

    private fun settleTransition(transition: CompleteNaturalPeriodTransition) {
        when (transition.previous.kind) {
            NaturalPeriodKind.HOUR -> {
                val weekKey = weekKeyForPeriod(transition.previous) ?: return
                settlePeriod(transition.previous, weekKey)
                    .onFailure { WorldGeoCommunityAddon.logger.error("Failed to settle building hour ${transition.previous.periodId}", it) }
            }
            NaturalPeriodKind.WEEK -> settlePeriod(transition.previous, transition.previous)
                .onFailure { WorldGeoCommunityAddon.logger.error("Failed to settle building week ${transition.previous.periodId}", it) }
            else -> Unit
        }
    }

    fun getState(community: Community): CommunityBuildingState = community.buildingState
    fun getDraft(playerUuid: UUID): CommunityBuildingDraft? = entryDrafts[playerUuid]
    fun setDraft(playerUuid: UUID, draft: CommunityBuildingDraft) { entryDrafts[playerUuid] = draft }
    fun clearDraft(playerUuid: UUID) { entryDrafts.remove(playerUuid) }
    fun getSelectablePool(): List<CommunityBuildingCatalogEntry> = selectablePoolState.sortedBy { it.baseBlockId }
    fun findSelectableEntry(baseBlockId: String): CommunityBuildingCatalogEntry? = selectablePoolState.firstOrNull { it.baseBlockId.equals(baseBlockId, ignoreCase = true) }

    fun addOrUpdateSelectableEntry(baseBlockId: String, unitCost: Int, rewardPerBlock: Long, linkedBlockIds: List<String>): Result<CommunityBuildingCatalogEntry> {
        if (!isValidBlockId(baseBlockId)) return Result.failure(IllegalArgumentException("invalid block id"))
        if (unitCost <= 0) return Result.failure(IllegalArgumentException("unit cost must be positive"))
        if (rewardPerBlock <= 0L) return Result.failure(IllegalArgumentException("reward must be positive"))
        val normalizedLinked = linkedBlockIds.distinct().filter { it != baseBlockId && isValidBlockId(it) }.toMutableList()
        val existing = findSelectableEntry(baseBlockId)
        val old = existing?.copy(linkedBlockIds = existing.linkedBlockIds.toMutableList())
        return try {
            val result = if (existing == null) {
                CommunityBuildingCatalogEntry(baseBlockId, unitCost, rewardPerBlock, normalizedLinked, 1L).also { selectablePoolState.add(it) }
            } else {
                existing.unitCost = unitCost
                existing.rewardPerBlock = rewardPerBlock
                existing.linkedBlockIds = normalizedLinked
                existing.templateVersion++
                existing
            }
            validateTemplatePool().getOrThrow()
            CommunityDatabase.save()
            Result.success(result)
        } catch (error: Exception) {
            if (existing == null) selectablePoolState.removeIf { it.baseBlockId.equals(baseBlockId, ignoreCase = true) }
            else if (old != null) {
                existing.unitCost = old.unitCost
                existing.rewardPerBlock = old.rewardPerBlock
                existing.linkedBlockIds = old.linkedBlockIds
                existing.templateVersion = old.templateVersion
            }
            Result.failure(error)
        }
    }

    fun removeSelectableEntry(baseBlockId: String): Result<Unit> {
        val existing = findSelectableEntry(baseBlockId) ?: return Result.failure(NoSuchElementException("entry not found"))
        return try {
            selectablePoolState.remove(existing)
            CommunityDatabase.save()
            Result.success(Unit)
        } catch (error: Exception) {
            selectablePoolState.add(existing)
            Result.failure(error)
        }
    }

    fun createDraftFromSelectable(baseBlockId: String): CommunityBuildingDraft? = findSelectableEntry(baseBlockId)?.let {
        CommunityBuildingDraft(it.baseBlockId, it.unitCost, it.rewardPerBlock, it.linkedBlockIds.toMutableList(), false)
    }

    fun canView(community: Community, playerUuid: UUID): Boolean = community.getMemberRole(playerUuid)?.name in setOf("OWNER", "ADMIN", "MEMBER")

    fun getPlayerWeekIncome(community: Community, playerUuid: UUID): Long {
        val currentWeekKey = RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK] ?: return 0L
        val ledger = community.buildingState.playerWeekLedgers[playerUuid] ?: return 0L
        return if (ledger.weekPeriodId == periodLedgerKey(currentWeekKey)) ledger.settledAmount else 0L
    }

    fun getPlayerWeekRemainingCap(community: Community, playerUuid: UUID): Long {
        val currentWeekKey = RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK] ?: return CommunityConfig.BUILDING_PLAYER_WEEKLY_CAP.value
        return getPlayerBuildingStatus(community, playerUuid, periodLedgerKey(currentWeekKey)).baseRemaining
    }

    fun getPlayerBuildingStatus(community: Community, playerUuid: UUID): PlayerBuildingStatus {
        val currentWeekKey = RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK]
        return getPlayerBuildingStatus(community, playerUuid, currentWeekKey?.let { periodLedgerKey(it) } ?: "-")
    }

    fun getPlayerBuildingStatus(community: Community, playerUuid: UUID, weekId: String): PlayerBuildingStatus {
        val ledger = community.buildingState.playerWeekLedgers[playerUuid]?.takeIf { it.weekPeriodId == weekId }
        val extraCap = CommunityTitleService.extraWeeklyCap(community, playerUuid)
        val extraUsed = ledger?.extraCapAmount ?: 0L
        val baseUsed = collectPlayerWeekUsage(weekId)[playerUuid] ?: 0L
        return PlayerBuildingStatus(
            community = community,
            weekId = weekId,
            income = ledger?.settledAmount ?: 0L,
            baseCap = CommunityConfig.BUILDING_PLAYER_WEEKLY_CAP.value,
            baseUsed = baseUsed,
            baseRemaining = (CommunityConfig.BUILDING_PLAYER_WEEKLY_CAP.value - baseUsed).coerceAtLeast(0L),
            extraCap = extraCap,
            extraUsed = extraUsed,
            extraRemaining = (extraCap - extraUsed).coerceAtLeast(0L),
            pendingPayouts = community.buildingState.pendingPayouts.count { it.playerUuid == playerUuid },
            foreman = extraCap > 0L
        )
    }

    fun listPlayerBuildingStatuses(playerUuid: UUID): List<PlayerBuildingStatus> {
        val weekId = RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK]?.let { periodLedgerKey(it) } ?: "-"
        return CommunityDatabase.communities
            .filter { canView(it, playerUuid) }
            .map { getPlayerBuildingStatus(it, playerUuid, weekId) }
            .filter { it.income > 0L || it.pendingPayouts > 0 || it.foreman || it.community.buildingState.activeEntries().isNotEmpty() }
    }

    fun findCommunityAt(player: ServerPlayer): Community? {
        val region = RegionDataApi.getRegionScopePairByLocation(player.level() as ServerLevel, player.blockPosition())?.first ?: return null
        return CommunityDatabase.getCommunityById(region.numberID)
    }

    fun getNextHourSettlementText(): String {
        val zoneId = ZoneId.of(CommunityConfig.TIMEZONE.value)
        val currentHourId = RegionDataApi.getCurrentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return "-"
        val next = LocalDateTime.parse(currentHourId, HOUR_FORMATTER).plusHours(1)
        return next.atZone(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00 z", Locale.ROOT))
    }

    fun listSurvivalBlockIds(): List<String> = BuiltInRegistries.ITEM
        .filterIsInstance<BlockItem>()
        .map { BuiltInRegistries.BLOCK.getKey(it.block).toString() }
        .filter { it !in nonSurvivalBlockIds }
        .distinct()
        .sorted()

    fun isValidBlockId(blockId: String): Boolean = BuiltInRegistries.BLOCK.any { BuiltInRegistries.BLOCK.getKey(it).toString() == blockId }
    fun getBlockItem(blockId: String): Item = BuiltInRegistries.ITEM.filterIsInstance<BlockItem>().firstOrNull { BuiltInRegistries.BLOCK.getKey(it.block).toString() == blockId } ?: net.minecraft.world.item.Items.BRICKS

    fun inferLinkedBlockIds(baseBlockId: String): List<String> {
        val path = baseBlockId.substringAfter(":", baseBlockId)
        val woodFamily = woodFamilies.firstOrNull { path == it || path.startsWith("${it}_") || path.endsWith("_${it}") }
        if (woodFamily != null) return listSurvivalBlockIds().filter { candidate ->
            val candidatePath = candidate.substringAfter(":", candidate)
            candidate != baseBlockId && (candidatePath == woodFamily || candidatePath.startsWith("${woodFamily}_") || candidatePath.endsWith("_${woodFamily}"))
        }
        val stoneFamily = stoneFamilies.firstOrNull { path.contains(it) }
        if (stoneFamily != null) return listSurvivalBlockIds().filter { candidate -> candidate != baseBlockId && candidate.substringAfter(":", candidate).contains(stoneFamily) }
        val suffixes = listOf("stairs", "slab", "wall", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate")
        val normalized = suffixes.firstOrNull { path.endsWith("_$it") }?.let { path.removeSuffix("_$it") } ?: return emptyList()
        return listSurvivalBlockIds().filter { candidate ->
            val candidatePath = candidate.substringAfter(":", candidate)
            candidate != baseBlockId && (candidatePath == normalized || candidatePath.startsWith("${normalized}_"))
        }
    }

    fun calculateSelectionCost(unitDelta: Int): Long = Math.multiplyExact(unitDelta.toLong(), PricingConfig.BUILDING_STYLE_UNIT_SELECTION_COST.value)

    fun calculateCapacityPurchaseCost(currentCapacity: Int, buyUnits: Int): Long {
        require(buyUnits > 0) { "buy units must be positive" }
        var total = 0L
        for (index in 1..buyUnits) {
            val newUnit = Math.addExact(currentCapacity, index)
            if (newUnit <= CommunityConfig.BUILDING_DEFAULT_CAPACITY_UNITS.value) continue
            val extraIndex = newUnit - CommunityConfig.BUILDING_DEFAULT_CAPACITY_UNITS.value
            val tier = ceil(extraIndex / 8.0).toInt()
            total = Math.addExact(total, Math.multiplyExact(PricingConfig.BUILDING_CAPACITY_UNIT_BASE_COST.value, tier.toLong()))
        }
        return total
    }


    fun settleCurrentHour(): Result<CommunityBuildingPeriodSettlementResult> {
        val keys = RegionDataApi.getCurrentNaturalPeriodKeys()
        val hourKey = keys[NaturalPeriodKind.HOUR] ?: return Result.failure(IllegalStateException("current hour period unavailable"))
        val weekKey = keys[NaturalPeriodKind.WEEK] ?: return Result.failure(IllegalStateException("current week period unavailable"))
        return settlePeriod(hourKey, weekKey)
    }

    fun settleCurrentWeek(): Result<CommunityBuildingPeriodSettlementResult> {
        val keys = RegionDataApi.getCurrentNaturalPeriodKeys()
        val weekKey = keys[NaturalPeriodKind.WEEK] ?: return Result.failure(IllegalStateException("current week period unavailable"))
        return settlePeriod(weekKey, weekKey)
    }

    fun settlePeriod(periodKey: NaturalPeriodKey, weekKey: NaturalPeriodKey): Result<CommunityBuildingPeriodSettlementResult> {
        val runtime = AccountSubsystem.runtimeOrNull() ?: return Result.failure(IllegalStateException("account subsystem unavailable"))
        return try {
            var settled = 0
            var skipped = 0
            var playerTransactions = 0
            var communityIncome = 0L
            for (community in CommunityDatabase.communities) {
                val regionId = community.regionNumberId ?: continue
                val entries = community.buildingState.activeEntries()
                if (entries.isEmpty()) continue
                val cursorUnit = "region:$regionId:${periodKey.timelineId}:${periodKey.kind.name.lowercase(Locale.ROOT)}"
                val existingCursor = runtime.sharedStore.findCursor(regionId, "building", "region", cursorUnit).join()
                val cursorValue = periodLedgerKey(periodKey)
                if (existingCursor?.cursor == cursorValue) {
                    skipped++
                    continue
                }
                val stats = queryBuildingStats(periodKey, regionId, entries)
                val rewardPlayers = stats.flatMap { it.playerContributions.keys }.toSet()
                val plan = CommunityBuildingSettlement.plan(
                    entries,
                    stats,
                    CommunityConfig.BUILDING_PLAYER_WEEKLY_CAP.value,
                    collectPlayerWeekUsage(periodLedgerKey(weekKey)),
                    CommunityConfig.BUILDING_COMMUNITY_WEEKLY_CAP.value,
                    currentCommunityWeekIncome(community, periodLedgerKey(weekKey)),
                    rewardPlayers.associateWith { CommunityTitleService.rewardPercent(community, it) },
                    rewardPlayers.associateWith { CommunityTitleService.extraWeeklyCap(community, it) },
                    collectPlayerExtraWeekUsage(regionId, periodLedgerKey(weekKey))
                )
                if (periodKey.kind == NaturalPeriodKind.HOUR) {
                    val futures = plan.playerRewards.map { reward ->
                        val external = "building:player:$regionId:${periodLedgerKey(periodKey)}:${reward.playerUuid}:${reward.blockId}"
                        val id = UUID.nameUUIDFromBytes(external.toByteArray())
                        runtime.service.submit(AccountTransaction(
                            id,
                            id.toString().replace("-", "").take(12).uppercase(Locale.ROOT),
                            System.currentTimeMillis(),
                            periodLedgerKey(periodKey),
                            reward.playerUuid,
                            null,
                            reward.amount,
                            AccountDirection.CREDIT,
                            "BUILDING",
                            external
                        ))
                    }
                    CompletableFuture.allOf(*futures.toTypedArray()).join()
                    appendPlayerRewardLedgers(runtime, regionId, periodLedgerKey(periodKey), plan.playerRewards)
                    applyPlayerWeekRewards(community, periodLedgerKey(weekKey), plan.playerRewards)
                    playerTransactions += plan.playerRewards.size
                } else if (plan.communityIncome > 0L) {
                    mutateTreasury(
                        community,
                        plan.communityIncome,
                        ResourceDirection.CREDIT,
                        "building",
                        "building:community:$regionId:${periodLedgerKey(periodKey)}",
                        "building-community-income",
                        periodLedgerKey(periodKey),
                        "community.treasury.desc.building_income",
                        listOf(periodLedgerKey(periodKey))
                    ).getOrThrow()
                    applyCommunityWeekIncome(community, periodLedgerKey(weekKey), plan.communityIncome)
                    communityIncome = Math.addExact(communityIncome, plan.communityIncome)
                }
                CommunityDatabase.save()
                runtime.sharedStore.append(PurposeCursorFact(UUID.randomUUID(), regionId, System.currentTimeMillis(), "building", "region", cursorUnit, cursorValue)).join()
                settled++
            }
            Result.success(CommunityBuildingPeriodSettlementResult(settled, skipped, playerTransactions, communityIncome))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun queryBuildingStats(
        periodKey: NaturalPeriodKey,
        regionId: Int,
        entries: List<CommunityBuildingEntry>
    ): List<CommunityBuildingBlockStats> {
        val blockIds = entries.flatMap { it.trackedBlockIds() }.distinct().toSet()
        if (blockIds.isEmpty()) return emptyList()
        val batch = RegionDataApi.queryBlockDeltaBatchAsync(periodKey.timelineId, periodKey.kind, periodKey.periodId, regionId, blockIds).join()
        require(batch.completeness.status == WorldGeoPeriodDataStatus.COMPLETE) { "WorldGeo period data is ${batch.completeness.status.name.lowercase(Locale.ROOT)}" }
        val checkpointByBlock = entries
            .flatMap { entry -> entry.trackedBlockIds().map { blockId -> blockId to checkpointFor(entry.selectionCheckpoint, periodKey) } }
            .filter { it.second != null }
            .associate { it.first to requireNotNull(it.second) }
        val baselineByCheckpoint = checkpointByBlock.values.distinct().associateWith(::readCheckpointBaseline)
        return batch.blocks.map { (blockId, delta) ->
            require(delta.placedCount >= 0L) { "negative WorldGeo placed count" }
            require(delta.brokenCount >= 0L) { "negative WorldGeo broken count" }
            val baseline = checkpointByBlock[blockId]?.let { baselineByCheckpoint[it]?.get(blockId) }
            val placed = Math.subtractExact(delta.placedCount, baseline?.placedCount ?: 0L).coerceAtLeast(0L)
            val broken = Math.subtractExact(delta.brokenCount, baseline?.brokenCount ?: 0L).coerceAtLeast(0L)
            val contributions = if (baseline == null) delta.playerContributions else {
                val players = delta.playerContributions.keys + baseline.playerContributions.keys
                players.associateWith { uuid ->
                    Math.subtractExact(delta.playerContributions[uuid] ?: 0L, baseline.playerContributions[uuid] ?: 0L)
                }
            }
            CommunityBuildingBlockStats(blockId, placed, broken, contributions)
        }
    }

    private fun checkpointFor(selectionCheckpoint: String, periodKey: NaturalPeriodKey): UUID? {
        val parts = selectionCheckpoint.split('|')
        if (parts.size != 5 || parts[0] != periodKey.timelineId) return null
        return when (periodKey.kind) {
            NaturalPeriodKind.HOUR -> if (parts[1] == periodKey.periodId) runCatching { UUID.fromString(parts[2]) }.getOrNull() else null
            NaturalPeriodKind.WEEK -> if (parts[3] == periodKey.periodId) runCatching { UUID.fromString(parts[4]) }.getOrNull() else null
            else -> null
        }
    }

    private fun readCheckpointBaseline(checkpointId: UUID): Map<String, CommunityBuildingBlockStats> {
        val aggregate = linkedMapOf<String, MutableBlockStats>()
        var pageIndex = 0
        while (true) {
            val page = RegionDataApi.readBehaviorStatsCheckpointPage(checkpointId, pageIndex).join()
                ?: throw IllegalStateException("building checkpoint $checkpointId unavailable")
            for (entry in page.entries) {
                val blockId = entry.objectId ?: continue
                val stats = aggregate.getOrPut(blockId) { MutableBlockStats() }
                when (entry.behaviorType) {
                    WorldGeoBehaviorType.BLOCK_PLACE -> {
                        stats.placedCount = Math.addExact(stats.placedCount, entry.count)
                        stats.playerContributions[entry.playerUuid] = Math.addExact(stats.playerContributions[entry.playerUuid] ?: 0L, entry.count)
                    }
                    WorldGeoBehaviorType.BLOCK_BREAK -> {
                        stats.brokenCount = Math.addExact(stats.brokenCount, entry.count)
                        stats.playerContributions[entry.playerUuid] = Math.subtractExact(stats.playerContributions[entry.playerUuid] ?: 0L, entry.count)
                    }
                    else -> Unit
                }
            }
            if (!page.hasMore) break
            pageIndex++
        }
        return aggregate.mapValues { (blockId, stats) ->
            CommunityBuildingBlockStats(blockId, stats.placedCount, stats.brokenCount, stats.playerContributions.toMap())
        }
    }

    private fun periodLedgerKey(key: NaturalPeriodKey): String = "${key.timelineId}:${key.periodId}"

    fun collectPlayerWeekUsage(weekId: String): Map<UUID, Long> {
        val result = LinkedHashMap<UUID, Long>()
        for (community in CommunityDatabase.communities) {
            for ((uuid, ledger) in community.buildingState.playerWeekLedgers) {
                if (ledger.weekPeriodId == weekId) result[uuid] = Math.addExact(result[uuid] ?: 0L, ledger.baseCapAmount)
            }
        }
        return result
    }

    private fun collectPlayerExtraWeekUsage(regionId: Int, weekId: String): Map<UUID, Long> {
        val community = CommunityDatabase.getCommunityById(regionId) ?: return emptyMap()
        return community.buildingState.playerWeekLedgers
            .filterValues { it.weekPeriodId == weekId && it.extraCapAmount > 0L }
            .mapValues { it.value.extraCapAmount }
    }

    private fun currentCommunityWeekIncome(community: Community, weekId: String): Long =
        community.buildingState.communityWeekLedgers.firstOrNull { it.weekPeriodId == weekId }?.settledAmount ?: 0L

    private fun applyCommunityWeekIncome(community: Community, weekId: String, amount: Long) {
        val ledger = community.buildingState.communityWeekLedgers.firstOrNull { it.weekPeriodId == weekId }
        if (ledger == null) {
            community.buildingState.communityWeekLedgers.add(com.imyvm.community.domain.model.community.CommunityBuildingCommunityWeekLedger(weekId, amount))
        } else {
            ledger.settledAmount = Math.addExact(ledger.settledAmount, amount)
        }
    }


    private fun appendPlayerRewardLedgers(
        runtime: AccountSubsystem.Runtime,
        regionId: Int,
        periodId: String,
        rewards: List<CommunityBuildingPlayerReward>
    ) {
        for (reward in rewards) {
            val external = "building:member:$regionId:$periodId:${reward.playerUuid}:${reward.blockId}"
            runtime.sharedStore.append(MemberLedgerFact(
                UUID.nameUUIDFromBytes(external.toByteArray()),
                regionId,
                System.currentTimeMillis(),
                reward.playerUuid,
                reward.amount,
                ResourceDirection.CREDIT,
                "building",
                external,
                "community.member.desc.building_reward",
                listOf(periodId, reward.blockId, reward.units.toString()),
                countsAsContribution = false
            )).join()
        }
    }

    private fun applyPlayerWeekRewards(community: Community, weekId: String, rewards: List<CommunityBuildingPlayerReward>) {
        for (reward in rewards) {
            val ledger = community.buildingState.playerWeekLedgers[reward.playerUuid]
            if (ledger == null || ledger.weekPeriodId != weekId) {
                community.buildingState.playerWeekLedgers[reward.playerUuid] = com.imyvm.community.domain.model.community.CommunityBuildingWeekLedger(weekId, reward.amount, reward.baseCapAmount, reward.extraCapAmount)
            } else {
                ledger.settledAmount = Math.addExact(ledger.settledAmount, reward.amount)
                ledger.baseCapAmount = Math.addExact(ledger.baseCapAmount, reward.baseCapAmount)
                ledger.extraCapAmount = Math.addExact(ledger.extraCapAmount, reward.extraCapAmount)
            }
        }
    }



    fun sendAdministrationSummary(player: ServerPlayer, community: Community) {
        val state = community.buildingState
        player.sendSystemMessage(Translator.tr("community.building.admin.header", community.generateCommunityMark()))
        player.sendSystemMessage(Translator.tr("community.building.admin.package", state.activeEntries().size.toString(), state.usedCapacityUnits().toString(), state.capacityUnits.toString(), getSelectablePool().size.toString()))
        player.sendSystemMessage(Translator.tr("community.building.admin.money", formatMoney(community.getTotalAssets()), getNextHourSettlementText(), state.pendingPayouts.size.toString()))
        val ranking = state.playerWeekLedgers.entries
            .sortedByDescending { it.value.settledAmount }
            .take(5)
        if (ranking.isEmpty()) {
            player.sendSystemMessage(Translator.tr("community.building.admin.ranking.empty"))
        } else {
            ranking.forEachIndexed { index, (uuid, ledger) ->
                player.sendSystemMessage(Translator.tr("community.building.admin.ranking.entry", (index + 1).toString(), uuid.toString(), formatMoney(ledger.settledAmount), formatMoney(ledger.extraCapAmount)))
            }
        }
    }

    fun buildPoolLore(entry: CommunityBuildingCatalogEntry): List<Component> = listOf(
        Translator.tr("community.building.lore.reward", formatMoney(entry.rewardPerBlock)),
        Translator.tr("community.building.lore.unit_cost", entry.unitCost.toString()),
        Translator.tr("community.building.lore.template_version", entry.templateVersion.toString()),
        Translator.tr("community.building.lore.linked", linkedSummary(entry.linkedBlockIds))
    )

    fun sendPoolEntryDetail(player: ServerPlayer, entry: CommunityBuildingCatalogEntry) {
        player.sendSystemMessage(Translator.tr("community.building.pool.detail.header", entry.baseBlockId))
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.unit_cost", entry.unitCost.toString()))
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.reward", formatMoney(entry.rewardPerBlock)))
        player.sendSystemMessage(Translator.tr("community.building.lore.template_version", entry.templateVersion.toString()))
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.linked", linkedSummary(entry.linkedBlockIds)))
    }

    fun buildCatalogLore(community: Community, entry: CommunityBuildingCatalogEntry): List<Component> = listOf(
        Translator.tr("community.building.lore.reward", formatMoney(entry.rewardPerBlock)),
        Translator.tr("community.building.lore.unit_cost", entry.unitCost.toString()),
        Translator.tr("community.building.lore.selected", if (community.buildingState.findEntry(entry.baseBlockId) != null) Translator.tr("community.building.value.yes").string else Translator.tr("community.building.value.no").string),
        Translator.tr("community.building.lore.linked", linkedSummary(entry.linkedBlockIds))
    )

    fun buildSelectedLore(entry: CommunityBuildingEntry): List<Component> = listOf(
        Translator.tr("community.building.lore.reward", formatMoney(entry.rewardPerBlock)),
        Translator.tr("community.building.lore.unit_cost", entry.unitCost.toString()),
        Translator.tr("community.building.lore.selected", Translator.tr("community.building.value.yes").string),
        Translator.tr("community.building.lore.linked", linkedSummary(entry.linkedBlockIds))
    )

    fun upsertEntry(community: Community, baseBlockId: String, unitCost: Int, rewardPerBlock: Long, linkedBlockIds: List<String>): Result<CommunityBuildingEntry> {
        val regionId = community.regionNumberId ?: return Result.failure(IllegalStateException("community region not bound"))
        val template = findSelectableEntry(baseBlockId) ?: return Result.failure(NoSuchElementException("template not found"))
        val state = community.buildingState
        val existing = state.findEntry(baseBlockId)
        val oldSnapshot = existing?.copy(linkedBlockIds = existing.linkedBlockIds.toMutableList())
        val oldUnitCost = existing?.unitCost ?: 0
        val projectedUsage = state.usedCapacityUnits() - oldUnitCost + template.unitCost
        if (projectedUsage > state.capacityUnits) return Result.failure(IllegalStateException("capacity exceeded"))
        val selectionCost = calculateSelectionCost(if (existing == null) template.unitCost else (template.unitCost - oldUnitCost).coerceAtLeast(0))
        if (selectionCost > 0L && community.getTotalAssets() < selectionCost) return Result.failure(IllegalStateException("insufficient treasury"))
        return try {
            val checkpoint = currentCheckpoint(regionId, template.trackedBlockIds())
            val frozen = CommunityBuildingEntry(
                template.baseBlockId,
                template.unitCost,
                template.rewardPerBlock,
                template.linkedBlockIds.toMutableList(),
                template.templateVersion,
                checkpoint,
                true
            )
            if (existing == null) state.stylePackage.add(frozen) else {
                existing.unitCost = frozen.unitCost
                existing.rewardPerBlock = frozen.rewardPerBlock
                existing.linkedBlockIds = frozen.linkedBlockIds
                existing.templateVersion = frozen.templateVersion
                existing.selectionCheckpoint = frozen.selectionCheckpoint
                existing.active = true
            }
            state.validateUniqueBlockMapping().getOrThrow()
            if (selectionCost > 0L) {
                mutateTreasury(community, selectionCost, ResourceDirection.DEBIT, "building", "community:building-style:$regionId:$baseBlockId:${template.templateVersion}:$checkpoint", "building-style-selection", baseBlockId, "community.treasury.desc.building_style_selection", listOf(baseBlockId, template.unitCost.toString())).getOrThrow()
            } else CommunityDatabase.save()
            Result.success(existing ?: frozen)
        } catch (error: Exception) {
            if (existing == null) state.stylePackage.removeIf { it.baseBlockId == baseBlockId } else if (oldSnapshot != null) {
                existing.unitCost = oldSnapshot.unitCost
                existing.rewardPerBlock = oldSnapshot.rewardPerBlock
                existing.linkedBlockIds = oldSnapshot.linkedBlockIds
                existing.templateVersion = oldSnapshot.templateVersion
                existing.selectionCheckpoint = oldSnapshot.selectionCheckpoint
                existing.active = oldSnapshot.active
            }
            Result.failure(error)
        }
    }

    fun removeEntry(community: Community, baseBlockId: String): Result<Unit> {
        val entry = community.buildingState.findEntry(baseBlockId) ?: return Result.failure(NoSuchElementException("entry not found"))
        return try {
            entry.active = false
            entry.selectionCheckpoint = currentCheckpoint(community.regionNumberId ?: 0, entry.trackedBlockIds())
            CommunityDatabase.save()
            Result.success(Unit)
        } catch (error: Exception) {
            entry.active = true
            Result.failure(error)
        }
    }

    fun setLinkedBlocks(community: Community, baseBlockId: String, linkedBlockIds: List<String>): Result<Unit> =
        Result.failure(UnsupportedOperationException("building links are frozen from OP templates"))

    fun buyCapacity(community: Community, buyUnits: Int): Result<Long> {
        if (buyUnits <= 0) return Result.failure(IllegalArgumentException("buy units must be positive"))
        val cost = calculateCapacityPurchaseCost(community.buildingState.capacityUnits, buyUnits)
        if (community.getTotalAssets() < cost) return Result.failure(IllegalStateException("insufficient treasury"))
        return try {
            community.buildingState.capacityUnits = Math.addExact(community.buildingState.capacityUnits, buyUnits)
            if (cost > 0L) {
                val newCapacity = community.buildingState.capacityUnits
                val oldCapacity = newCapacity - buyUnits
                mutateTreasury(community, cost, ResourceDirection.DEBIT, "building", "community:building-capacity:${community.regionNumberId}:$oldCapacity:$newCapacity", "building-capacity", buyUnits.toString(), "community.treasury.desc.building_capacity", listOf(buyUnits.toString())).getOrThrow()
            } else CommunityDatabase.save()
            Result.success(cost)
        } catch (error: Exception) {
            community.buildingState.capacityUnits -= buyUnits
            Result.failure(error)
        }
    }

    fun sendEntryDetail(player: ServerPlayer, community: Community, entry: CommunityBuildingEntry) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.header", community.generateCommunityMark(), entry.baseBlockId))
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.unit_cost", entry.unitCost.toString()))
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.reward", formatMoney(entry.rewardPerBlock)))
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.linked", linkedSummary(entry.linkedBlockIds)))
    }

    fun validateTemplatePool(): Result<Unit> {
        val ownerByBlock = LinkedHashMap<String, String>()
        for (entry in selectablePoolState) {
            for (blockId in entry.trackedBlockIds()) {
                val previous = ownerByBlock.putIfAbsent(blockId, entry.baseBlockId)
                if (previous != null && previous != entry.baseBlockId) return Result.failure(IllegalStateException("building template block $blockId conflicts between $previous and ${entry.baseBlockId}"))
            }
        }
        return Result.success(Unit)
    }

    fun formatMoney(amount: Long): String = String.format(Locale.ROOT, "%.2f", amount / 100.0)

    private fun linkedSummary(linkedBlockIds: List<String>): String = if (linkedBlockIds.isEmpty()) "-" else linkedBlockIds.joinToString(", ")
    private fun currentCheckpoint(regionId: Int, blockIds: List<String>): String {
        val server = WorldGeoCommunityAddon.server ?: return RegionDataApi.getCurrentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: ""
        val keys = RegionDataApi.getCurrentNaturalPeriodKeys()
        val hourKey = keys[NaturalPeriodKind.HOUR] ?: return ""
        val weekKey = keys[NaturalPeriodKind.WEEK] ?: return hourKey.periodId
        val blocks = blockIds.distinct().toSet()
        val hourCheckpoint = createCheckpoint(server, hourKey, regionId, blocks)
        val weekCheckpoint = createCheckpoint(server, weekKey, regionId, blocks)
        return listOf(hourKey.timelineId, hourKey.periodId, hourCheckpoint, weekKey.periodId, weekCheckpoint).joinToString("|")
    }

    private fun createCheckpoint(server: net.minecraft.server.MinecraftServer, key: NaturalPeriodKey, regionId: Int, blockIds: Set<String>): UUID {
        val checkpointId = UUID.randomUUID()
        val result = RegionDataApi.createBehaviorStatsCheckpoint(
            server,
            WorldGeoBehaviorStatsCheckpointRequest(
                checkpointId,
                WorldGeoBehaviorStatsPageQuery(key, regionId, objectIds = blockIds),
                512
            )
        ).join()
        require(result.status == WorldGeoBehaviorStatsCheckpointStatus.PUBLISHED || result.status == WorldGeoBehaviorStatsCheckpointStatus.ALREADY_PUBLISHED) {
            "building checkpoint failed: ${result.status.name.lowercase(Locale.ROOT)}"
        }
        require(result.completeness.status != WorldGeoPeriodDataStatus.UNAVAILABLE) { "building checkpoint period unavailable" }
        return result.checkpointId
    }
}

private class MutableBlockStats(
    var placedCount: Long = 0L,
    var brokenCount: Long = 0L,
    val playerContributions: MutableMap<UUID, Long> = linkedMapOf()
)

data class CommunityBuildingDraft(
    var baseBlockId: String,
    var unitCost: Int,
    var rewardPerBlock: Long,
    var linkedBlockIds: MutableList<String> = mutableListOf(),
    var editingExisting: Boolean = false
)

private val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")


data class CommunityBuildingPeriodSettlementResult(
    val settledCommunities: Int,
    val skippedCommunities: Int,
    val playerTransactions: Int,
    val communityIncome: Long
)


data class PlayerBuildingStatus(
    val community: Community,
    val weekId: String,
    val income: Long,
    val baseCap: Long,
    val baseUsed: Long,
    val baseRemaining: Long,
    val extraCap: Long,
    val extraUsed: Long,
    val extraRemaining: Long,
    val pendingPayouts: Int,
    val foreman: Boolean
)
