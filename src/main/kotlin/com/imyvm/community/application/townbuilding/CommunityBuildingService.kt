package com.imyvm.community.application.townbuilding

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.account.mutateTreasury
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.community.CommunityBuildingCatalogEntry
import com.imyvm.community.domain.model.community.CommunityBuildingEntry
import com.imyvm.community.domain.model.community.CommunityBuildingState
import com.imyvm.community.domain.model.transaction.PurposeCursorFact
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.math.ceil

object CommunityBuildingService {
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
        RegionDataApi.registerNaturalPeriodTransitionCallback(Consumer { transition ->
            val server = WorldGeoCommunityAddon.server ?: return@Consumer
            server.execute { settleTransition(transition) }
        })
    }

    private fun settleTransition(transition: NaturalPeriodTransition) {
        when (transition.kind) {
            NaturalPeriodKind.HOUR -> {
                val weekId = RegionDataApi.getCurrentNaturalPeriodIds()[NaturalPeriodKind.WEEK] ?: return
                settlePeriod(NaturalPeriodKind.HOUR, transition.previousId, weekId)
                    .onFailure { WorldGeoCommunityAddon.logger.error("Failed to settle building hour ${transition.previousId}", it) }
            }
            NaturalPeriodKind.WEEK -> settlePeriod(NaturalPeriodKind.WEEK, transition.previousId, transition.previousId)
                .onFailure { WorldGeoCommunityAddon.logger.error("Failed to settle building week ${transition.previousId}", it) }
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
    fun getPlayerWeekIncome(community: Community, playerUuid: UUID): Long = 0L
    fun getPlayerWeekRemainingCap(community: Community, playerUuid: UUID): Long = CommunityConfig.BUILDING_PLAYER_WEEKLY_CAP.value

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
        val ids = RegionDataApi.getCurrentNaturalPeriodIds()
        val hourId = ids[NaturalPeriodKind.HOUR] ?: return Result.failure(IllegalStateException("current hour period unavailable"))
        val weekId = ids[NaturalPeriodKind.WEEK] ?: return Result.failure(IllegalStateException("current week period unavailable"))
        return settlePeriod(NaturalPeriodKind.HOUR, hourId, weekId)
    }

    fun settleCurrentWeek(): Result<CommunityBuildingPeriodSettlementResult> {
        val ids = RegionDataApi.getCurrentNaturalPeriodIds()
        val weekId = ids[NaturalPeriodKind.WEEK] ?: return Result.failure(IllegalStateException("current week period unavailable"))
        return settlePeriod(NaturalPeriodKind.WEEK, weekId, weekId)
    }

    fun settlePeriod(periodKind: NaturalPeriodKind, periodId: String, weekId: String): Result<CommunityBuildingPeriodSettlementResult> {
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
                val cursorUnit = "region:$regionId:${periodKind.name.lowercase(Locale.ROOT)}"
                val existingCursor = runtime.sharedStore.findCursor(regionId, "building", "region", cursorUnit).join()
                if (existingCursor?.cursor == periodId) {
                    skipped++
                    continue
                }
                val stats = queryBuildingStats(periodKind, periodId, regionId, entries)
                val plan = CommunityBuildingSettlement.plan(
                    entries,
                    stats,
                    CommunityConfig.BUILDING_PLAYER_WEEKLY_CAP.value,
                    collectPlayerWeekUsage(weekId),
                    CommunityConfig.BUILDING_COMMUNITY_WEEKLY_CAP.value,
                    currentCommunityWeekIncome(community, weekId)
                )
                if (periodKind == NaturalPeriodKind.HOUR) {
                    val futures = plan.playerRewards.map { reward ->
                        val external = "building:player:$regionId:$periodId:${reward.playerUuid}:${reward.blockId}"
                        val id = UUID.nameUUIDFromBytes(external.toByteArray())
                        runtime.service.submit(AccountTransaction(
                            id,
                            id.toString().replace("-", "").take(12).uppercase(Locale.ROOT),
                            System.currentTimeMillis(),
                            periodId,
                            reward.playerUuid,
                            null,
                            reward.amount,
                            AccountDirection.CREDIT,
                            "BUILDING",
                            external
                        ))
                    }
                    CompletableFuture.allOf(*futures.toTypedArray()).join()
                    applyPlayerWeekRewards(community, weekId, plan.playerRewards)
                    playerTransactions += plan.playerRewards.size
                } else if (plan.communityIncome > 0L) {
                    mutateTreasury(
                        community,
                        plan.communityIncome,
                        ResourceDirection.CREDIT,
                        "building",
                        "building:community:$regionId:$periodId",
                        "building-community-income",
                        periodId,
                        "community.treasury.desc.building_income",
                        listOf(periodId)
                    ).getOrThrow()
                    communityIncome = Math.addExact(communityIncome, plan.communityIncome)
                }
                CommunityDatabase.save()
                runtime.sharedStore.append(PurposeCursorFact(UUID.randomUUID(), regionId, System.currentTimeMillis(), "building", "region", cursorUnit, periodId)).join()
                settled++
            }
            Result.success(CommunityBuildingPeriodSettlementResult(settled, skipped, playerTransactions, communityIncome))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun queryBuildingStats(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int,
        entries: List<CommunityBuildingEntry>
    ): List<CommunityBuildingBlockStats> {
        val blockIds = entries.flatMap { it.trackedBlockIds() }.distinct().toSet()
        if (blockIds.isEmpty()) return emptyList()
        val batch = RegionDataApi.queryProductionBlockDeltaBatchAsync(periodKind, periodId, regionId, blockIds).join()
        return batch.blocks.map { (blockId, delta) ->
            require(delta.placedCount >= 0L) { "negative WorldGeo placed count" }
            require(delta.brokenCount >= 0L) { "negative WorldGeo broken count" }
            CommunityBuildingBlockStats(blockId, delta.placedCount, delta.brokenCount, delta.playerContributions)
        }
    }

    private fun collectPlayerWeekUsage(weekId: String): Map<UUID, Long> {
        val result = LinkedHashMap<UUID, Long>()
        for (community in CommunityDatabase.communities) {
            for ((uuid, ledger) in community.buildingState.playerWeekLedgers) {
                if (ledger.weekPeriodId == weekId) result[uuid] = Math.addExact(result[uuid] ?: 0L, ledger.settledAmount)
            }
        }
        return result
    }

    private fun currentCommunityWeekIncome(community: Community, weekId: String): Long = community.buildingState.processedWeekPeriodIds
        .firstOrNull { it == weekId }
        ?.let { CommunityConfig.BUILDING_COMMUNITY_WEEKLY_CAP.value }
        ?: 0L

    private fun applyPlayerWeekRewards(community: Community, weekId: String, rewards: List<CommunityBuildingPlayerReward>) {
        for (reward in rewards) {
            val ledger = community.buildingState.playerWeekLedgers[reward.playerUuid]
            if (ledger == null || ledger.weekPeriodId != weekId) {
                community.buildingState.playerWeekLedgers[reward.playerUuid] = com.imyvm.community.domain.model.community.CommunityBuildingWeekLedger(weekId, reward.amount)
            } else {
                ledger.settledAmount = Math.addExact(ledger.settledAmount, reward.amount)
            }
        }
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
            val frozen = CommunityBuildingEntry(
                template.baseBlockId,
                template.unitCost,
                template.rewardPerBlock,
                template.linkedBlockIds.toMutableList(),
                template.templateVersion,
                currentCheckpoint(),
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
                mutateTreasury(community, selectionCost, ResourceDirection.DEBIT, "building", "community:building-style:$regionId:$baseBlockId:${System.currentTimeMillis()}", "building-style-selection", baseBlockId, "community.treasury.desc.building_style_selection", listOf(baseBlockId, template.unitCost.toString())).getOrThrow()
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
            entry.selectionCheckpoint = currentCheckpoint()
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
                mutateTreasury(community, cost, ResourceDirection.DEBIT, "building", "community:building-capacity:${community.regionNumberId}:$buyUnits:${System.currentTimeMillis()}", "building-capacity", buyUnits.toString(), "community.treasury.desc.building_capacity", listOf(buyUnits.toString())).getOrThrow()
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
    private fun currentCheckpoint(): String = RegionDataApi.getCurrentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: System.currentTimeMillis().toString()
}

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
