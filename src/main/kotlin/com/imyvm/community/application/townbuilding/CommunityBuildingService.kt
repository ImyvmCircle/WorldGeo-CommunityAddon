package com.imyvm.community.application.townbuilding

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.CommunityBuildingCatalogEntry
import com.imyvm.community.domain.model.community.CommunityBuildingEntry
import com.imyvm.community.domain.model.community.CommunityBuildingPendingPayout
import com.imyvm.community.domain.model.community.CommunityBuildingState
import com.imyvm.community.domain.model.community.CommunityBuildingWeekLedger
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import com.imyvm.iwg.inter.api.RegionDataApi
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.ceil

object CommunityBuildingService {
    private const val STATE_HISTORY_LIMIT = 256
    private val transitionQueue = ConcurrentLinkedQueue<NaturalPeriodTransition>()
    private val entryDrafts = mutableMapOf<UUID, CommunityBuildingDraft>()
    private val nonSurvivalBlockIds = setOf(
        "minecraft:air",
        "minecraft:cave_air",
        "minecraft:void_air",
        "minecraft:bedrock",
        "minecraft:barrier",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:jigsaw",
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",
        "minecraft:light",
        "minecraft:debug_stick",
        "minecraft:test_block",
        "minecraft:test_instance_block"
    )
    private val woodFamilies = listOf(
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak"
    )
    private val stoneFamilies = listOf(
        "stone_bricks", "deepslate_bricks", "deepslate_tiles", "mud_bricks", "nether_bricks", "red_nether_bricks", "end_stone_bricks",
        "prismarine", "prismarine_bricks", "dark_prismarine", "sandstone", "red_sandstone", "blackstone", "polished_blackstone_bricks",
        "tuff_bricks", "resin_bricks"
    )
    val selectablePoolState: MutableList<CommunityBuildingCatalogEntry> = mutableListOf()

    fun register() {
        RegionDataApi.registerNaturalPeriodTransitionCallback { transitionQueue.add(it) }
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            processTransitions()
        }
    }

    fun getState(community: Community): CommunityBuildingState = community.buildingState

    fun getDraft(playerUuid: UUID): CommunityBuildingDraft? = entryDrafts[playerUuid]

    fun setDraft(playerUuid: UUID, draft: CommunityBuildingDraft) {
        entryDrafts[playerUuid] = draft
    }

    fun clearDraft(playerUuid: UUID) {
        entryDrafts.remove(playerUuid)
    }

    fun getSelectablePool(): List<CommunityBuildingCatalogEntry> = selectablePoolState.sortedBy { it.baseBlockId }

    fun findSelectableEntry(baseBlockId: String): CommunityBuildingCatalogEntry? =
        selectablePoolState.firstOrNull { it.baseBlockId.equals(baseBlockId, ignoreCase = true) }

    fun addOrUpdateSelectableEntry(baseBlockId: String, unitCost: Int, rewardPerBlock: Long, linkedBlockIds: List<String>): Result<CommunityBuildingCatalogEntry> {
        if (!isValidBlockId(baseBlockId)) return Result.failure(IllegalArgumentException("invalid block id"))
        if (unitCost <= 0) return Result.failure(IllegalArgumentException("unit cost must be positive"))
        if (rewardPerBlock <= 0L) return Result.failure(IllegalArgumentException("reward must be positive"))
        val normalizedLinked = linkedBlockIds.distinct().filter { it != baseBlockId && isValidBlockId(it) }.toMutableList()
        val existing = findSelectableEntry(baseBlockId)
        val snapshot = existing?.copy(linkedBlockIds = existing.linkedBlockIds.toMutableList())
        return try {
            val result = if (existing == null) {
                CommunityBuildingCatalogEntry(baseBlockId, unitCost, rewardPerBlock, normalizedLinked).also { selectablePoolState.add(it) }
            } else {
                existing.unitCost = unitCost
                existing.rewardPerBlock = rewardPerBlock
                existing.linkedBlockIds = normalizedLinked
                existing
            }
            CommunityDatabase.save()
            Result.success(result)
        } catch (e: Exception) {
            if (existing == null) selectablePoolState.removeIf { it.baseBlockId.equals(baseBlockId, ignoreCase = true) }
            else if (snapshot != null) {
                existing.unitCost = snapshot.unitCost
                existing.rewardPerBlock = snapshot.rewardPerBlock
                existing.linkedBlockIds = snapshot.linkedBlockIds
            }
            Result.failure(e)
        }
    }

    fun removeSelectableEntry(baseBlockId: String): Result<Unit> {
        val existing = findSelectableEntry(baseBlockId) ?: return Result.failure(NoSuchElementException("entry not found"))
        return try {
            selectablePoolState.remove(existing)
            CommunityDatabase.save()
            Result.success(Unit)
        } catch (e: Exception) {
            selectablePoolState.add(existing)
            Result.failure(e)
        }
    }

    fun createDraftFromSelectable(baseBlockId: String): CommunityBuildingDraft? {
        val selected = findSelectableEntry(baseBlockId) ?: return null
        return CommunityBuildingDraft(
            baseBlockId = selected.baseBlockId,
            unitCost = selected.unitCost,
            rewardPerBlock = selected.rewardPerBlock,
            linkedBlockIds = selected.linkedBlockIds.toMutableList(),
            editingExisting = false
        )
    }

    fun canView(community: Community, playerUuid: UUID): Boolean {
        val role = community.getMemberRole(playerUuid) ?: return false
        return role.name == "OWNER" || role.name == "ADMIN" || role.name == "MEMBER"
    }

    fun getPlayerWeekIncome(community: Community, playerUuid: UUID): Long {
        val currentWeekId = RegionDataApi.getCurrentNaturalPeriodIds()[NaturalPeriodKind.WEEK] ?: return 0L
        val ledger = community.buildingState.playerWeekLedgers[playerUuid] ?: return 0L
        return if (ledger.weekPeriodId == currentWeekId) ledger.settledAmount else 0L
    }

    fun getPlayerWeekRemainingCap(community: Community, playerUuid: UUID): Long =
        (CommunityConfig.BUILDING_PLAYER_WEEKLY_CAP.value - getPlayerWeekIncome(community, playerUuid)).coerceAtLeast(0L)

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

    fun isValidBlockId(blockId: String): Boolean =
        BuiltInRegistries.BLOCK.any { BuiltInRegistries.BLOCK.getKey(it).toString() == blockId }

    fun getBlockItem(blockId: String): Item =
        BuiltInRegistries.ITEM
            .filterIsInstance<BlockItem>()
            .firstOrNull { BuiltInRegistries.BLOCK.getKey(it.block).toString() == blockId }
            ?: net.minecraft.world.item.Items.BRICKS

    fun inferLinkedBlockIds(baseBlockId: String): List<String> {
        val path = baseBlockId.substringAfter(":", baseBlockId)
        val woodFamily = woodFamilies.firstOrNull { family ->
            path == family || path.startsWith("${family}_") || path.endsWith("_${family}")
        }
        if (woodFamily != null) {
            return listSurvivalBlockIds().filter { candidate ->
                val candidatePath = candidate.substringAfter(":", candidate)
                candidatePath == woodFamily ||
                    candidatePath.startsWith("${woodFamily}_") ||
                    candidatePath.endsWith("_${woodFamily}")
            }.filter { it != baseBlockId }
        }
        val stoneFamily = stoneFamilies.firstOrNull { family -> path.contains(family) }
        if (stoneFamily != null) {
            return listSurvivalBlockIds().filter { candidate ->
                val candidatePath = candidate.substringAfter(":", candidate)
                candidatePath.contains(stoneFamily)
            }.filter { it != baseBlockId }
        }
        val suffixes = listOf("stairs", "slab", "wall", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate")
        val normalized = suffixes.firstOrNull { path.endsWith("_$it") }?.let { path.removeSuffix("_$it") } ?: return emptyList()
        return listSurvivalBlockIds().filter { candidate ->
            val candidatePath = candidate.substringAfter(":", candidate)
            candidatePath == normalized || candidatePath.startsWith("${normalized}_")
        }.filter { it != baseBlockId }
    }

    fun calculateSelectionCost(unitDelta: Int): Long = unitDelta.toLong() * PricingConfig.BUILDING_STYLE_UNIT_SELECTION_COST.value

    fun calculateCapacityPurchaseCost(currentCapacity: Int, buyUnits: Int): Long {
        var total = 0L
        for (index in 1..buyUnits) {
            val newUnit = currentCapacity + index
            if (newUnit <= CommunityConfig.BUILDING_DEFAULT_CAPACITY_UNITS.value) continue
            val extraIndex = newUnit - CommunityConfig.BUILDING_DEFAULT_CAPACITY_UNITS.value
            val tier = ceil(extraIndex / 8.0).toInt()
            total += PricingConfig.BUILDING_CAPACITY_UNIT_BASE_COST.value * tier
        }
        return total
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

    private fun linkedSummary(linkedBlockIds: List<String>): String = if (linkedBlockIds.isEmpty()) "-" else linkedBlockIds.joinToString(", ")

    fun upsertEntry(
        community: Community,
        baseBlockId: String,
        unitCost: Int,
        rewardPerBlock: Long,
        linkedBlockIds: List<String>
    ): Result<CommunityBuildingEntry> {
        if (community.regionNumberId == null) return Result.failure(IllegalStateException("community region not bound"))
        if (!isValidBlockId(baseBlockId)) return Result.failure(IllegalArgumentException("invalid block id"))
        if (unitCost <= 0) return Result.failure(IllegalArgumentException("unit cost must be positive"))
        if (rewardPerBlock <= 0L) return Result.failure(IllegalArgumentException("reward must be positive"))
        val state = community.buildingState
        val existing = state.findEntry(baseBlockId)
        val oldUnitCost = existing?.unitCost ?: 0
        val projectedUsage = state.usedCapacityUnits() - oldUnitCost + unitCost
        if (projectedUsage > state.capacityUnits) {
            return Result.failure(IllegalStateException("capacity exceeded"))
        }
        val unitDelta = (unitCost - oldUnitCost).coerceAtLeast(0)
        val selectionCost = calculateSelectionCost(if (existing == null) unitCost else unitDelta)
        if (selectionCost > 0L && community.getTotalAssets() < selectionCost) {
            return Result.failure(IllegalStateException("insufficient treasury"))
        }
        val normalizedLinked = linkedBlockIds.distinct().filter { it != baseBlockId && isValidBlockId(it) }.toMutableList()
        val snapshot = existing?.copy(linkedBlockIds = existing.linkedBlockIds.toMutableList())
        val expenditure = if (selectionCost > 0L) {
            Turnover(
                selectionCost,
                System.currentTimeMillis(),
                TurnoverSource.SYSTEM,
                "community.treasury.desc.building_style_selection",
                listOf(baseBlockId, unitCost.toString())
            )
        } else null
        return try {
            if (existing == null) {
                val created = CommunityBuildingEntry(baseBlockId, unitCost, rewardPerBlock, normalizedLinked)
                state.stylePackage.add(created)
                expenditure?.let { community.expenditures.add(it) }
                CommunityDatabase.save()
                Result.success(created)
            } else {
                existing.unitCost = unitCost
                existing.rewardPerBlock = rewardPerBlock
                existing.linkedBlockIds = normalizedLinked
                expenditure?.let { community.expenditures.add(it) }
                CommunityDatabase.save()
                Result.success(existing)
            }
        } catch (e: Exception) {
            expenditure?.let { community.expenditures.remove(it) }
            if (existing == null) {
                state.stylePackage.removeIf { it.baseBlockId == baseBlockId }
            } else if (snapshot != null) {
                existing.unitCost = snapshot.unitCost
                existing.rewardPerBlock = snapshot.rewardPerBlock
                existing.linkedBlockIds = snapshot.linkedBlockIds
            }
            Result.failure(e)
        }
    }

    fun removeEntry(community: Community, baseBlockId: String): Result<Unit> {
        val state = community.buildingState
        val target = state.findEntry(baseBlockId) ?: return Result.failure(NoSuchElementException("entry not found"))
        return try {
            state.stylePackage.remove(target)
            CommunityDatabase.save()
            Result.success(Unit)
        } catch (e: Exception) {
            state.stylePackage.add(target)
            Result.failure(e)
        }
    }

    fun setLinkedBlocks(community: Community, baseBlockId: String, linkedBlockIds: List<String>): Result<Unit> {
        val entry = community.buildingState.findEntry(baseBlockId) ?: return Result.failure(NoSuchElementException("entry not found"))
        val oldLinked = entry.linkedBlockIds.toMutableList()
        return try {
            entry.linkedBlockIds = linkedBlockIds.distinct().filter { it != baseBlockId && isValidBlockId(it) }.toMutableList()
            CommunityDatabase.save()
            Result.success(Unit)
        } catch (e: Exception) {
            entry.linkedBlockIds = oldLinked
            Result.failure(e)
        }
    }

    fun buyCapacity(community: Community, buyUnits: Int): Result<Long> {
        if (buyUnits <= 0) return Result.failure(IllegalArgumentException("buy units must be positive"))
        val state = community.buildingState
        val cost = calculateCapacityPurchaseCost(state.capacityUnits, buyUnits)
        if (community.getTotalAssets() < cost) return Result.failure(IllegalStateException("insufficient treasury"))
        val expenditure = Turnover(
            cost,
            System.currentTimeMillis(),
            TurnoverSource.SYSTEM,
            "community.treasury.desc.building_capacity",
            listOf(buyUnits.toString())
        )
        return try {
            state.capacityUnits += buyUnits
            community.expenditures.add(expenditure)
            CommunityDatabase.save()
            Result.success(cost)
        } catch (e: Exception) {
            state.capacityUnits -= buyUnits
            community.expenditures.remove(expenditure)
            Result.failure(e)
        }
    }

    fun sendEntryDetail(player: ServerPlayer, community: Community, entry: CommunityBuildingEntry) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.header", community.generateCommunityMark(), entry.baseBlockId))
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.unit_cost", entry.unitCost.toString()))
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.reward", formatMoney(entry.rewardPerBlock)))
        val linked = if (entry.linkedBlockIds.isEmpty()) "-" else entry.linkedBlockIds.joinToString(", ")
        player.sendSystemMessage(Translator.tr("community.building.entry.detail.linked", linked))
    }

    private fun processTransitions() {
        while (true) {
            val transition = transitionQueue.poll() ?: break
            when (transition.kind) {
                NaturalPeriodKind.HOUR -> settleHour(transition.previousId)
                NaturalPeriodKind.WEEK -> settleWeek(transition.previousId)
                else -> Unit
            }
        }
    }

    private fun settleHour(hourId: String) {
        val weekId = deriveWeekIdFromHourId(hourId)
        var changed = false
        for (community in CommunityDatabase.communities) {
            val regionId = community.regionNumberId ?: continue
            val state = community.buildingState
            if (hourId in state.processedHourPeriodIds || state.stylePackage.isEmpty()) continue
            for (entry in state.stylePackage) {
                for (blockId in entry.trackedBlockIds()) {
                    val stats = RegionDataApi.queryBlockDelta(NaturalPeriodKind.HOUR, hourId, regionId, null, null, blockId)
                    for ((playerUuid, netPlaced) in stats.playerContributions) {
                        val effectivePlaced = netPlaced.coerceAtLeast(0L)
                        if (effectivePlaced <= 0L) continue
                        val ledger = state.playerWeekLedgers[playerUuid]
                        val settled = if (ledger?.weekPeriodId == weekId) ledger.settledAmount else 0L
                        val remaining = (CommunityConfig.BUILDING_PLAYER_WEEKLY_CAP.value - settled).coerceAtLeast(0L)
                        if (remaining <= 0L) continue
                        val amount = (effectivePlaced * entry.rewardPerBlock).coerceAtMost(remaining)
                        if (amount <= 0L) continue
                        if (ledger == null || ledger.weekPeriodId != weekId) {
                            state.playerWeekLedgers[playerUuid] = CommunityBuildingWeekLedger(weekId, amount)
                        } else {
                            ledger.settledAmount += amount
                        }
                        state.pendingPayouts.add(
                            CommunityBuildingPendingPayout(
                                playerUuid = playerUuid,
                                amount = amount,
                                hourPeriodId = hourId,
                                weekPeriodId = weekId,
                                blockCount = effectivePlaced
                            )
                        )
                        val member = community.member[playerUuid]
                        if (member != null) {
                            member.mail.add(net.minecraft.network.chat.Component.literal("[UNREAD]" + Translator.tr("community.building.mail.pending_reward", community.generateCommunityMark(), formatMoney(amount), hourId).string))
                        }
                        changed = true
                    }
                }
            }
            state.processedHourPeriodIds.add(hourId)
            trimHistory(state.processedHourPeriodIds)
            pruneWeekLedgers(state, weekId)
            changed = true
        }
        if (changed) {
            try {
                CommunityDatabase.save()
            } catch (e: Exception) {
                WorldGeoCommunityAddon.logger.error("Failed to save community building hour settlement $hourId", e)
            }
        }
    }

    private fun settleWeek(weekId: String) {
        var changed = false
        for (community in CommunityDatabase.communities) {
            val regionId = community.regionNumberId ?: continue
            val state = community.buildingState
            if (weekId in state.processedWeekPeriodIds || state.stylePackage.isEmpty()) continue
            var amount = 0L
            for (entry in state.stylePackage) {
                for (blockId in entry.trackedBlockIds()) {
                    val stats = RegionDataApi.queryBlockDelta(NaturalPeriodKind.WEEK, weekId, regionId, null, null, blockId)
                    val netPlaced = stats.netDelta.coerceAtLeast(0L)
                    if (netPlaced <= 0L) continue
                    amount += netPlaced * entry.rewardPerBlock / 5L
                }
            }
            val capped = amount.coerceAtMost(CommunityConfig.BUILDING_COMMUNITY_WEEKLY_CAP.value)
            if (capped > 0L) {
                community.communityIncome.add(
                    Turnover(
                        capped,
                        System.currentTimeMillis(),
                        TurnoverSource.SYSTEM,
                        "community.treasury.desc.building_weekly_income",
                        listOf(weekId)
                    )
                )
                changed = true
            }
            state.processedWeekPeriodIds.add(weekId)
            trimHistory(state.processedWeekPeriodIds)
            changed = true
        }
        if (changed) {
            try {
                CommunityDatabase.save()
            } catch (e: Exception) {
                WorldGeoCommunityAddon.logger.error("Failed to save community building week settlement $weekId", e)
            }
        }
    }

    private fun deriveWeekIdFromHourId(hourId: String): String {
        val localDateTime = LocalDateTime.parse(hourId, HOUR_FORMATTER)
        val weekFields = WeekFields.ISO
        val year = localDateTime.get(weekFields.weekBasedYear())
        val week = localDateTime.get(weekFields.weekOfWeekBasedYear())
        return String.format(Locale.ROOT, "%04d-W%02d", year, week)
    }

    private fun pruneWeekLedgers(state: CommunityBuildingState, currentWeekId: String) {
        state.playerWeekLedgers.entries.removeIf { it.value.weekPeriodId != currentWeekId }
    }

    private fun trimHistory(ids: MutableList<String>) {
        while (ids.size > STATE_HISTORY_LIMIT) {
            ids.removeAt(0)
        }
    }

    fun formatMoney(amount: Long): String = String.format(Locale.ROOT, "%.2f", amount / 100.0)
}

data class CommunityBuildingDraft(
    var baseBlockId: String,
    var unitCost: Int,
    var rewardPerBlock: Long,
    var linkedBlockIds: MutableList<String> = mutableListOf(),
    var editingExisting: Boolean = false
)

private val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")
