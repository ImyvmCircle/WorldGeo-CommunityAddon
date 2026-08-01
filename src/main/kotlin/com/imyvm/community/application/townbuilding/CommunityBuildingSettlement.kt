package com.imyvm.community.application.townbuilding

import com.imyvm.community.domain.model.community.CommunityBuildingEntry
import java.math.BigInteger
import java.util.UUID

data class CommunityBuildingBlockStats(
    val blockId: String,
    val placedCount: Long,
    val brokenCount: Long,
    val playerContributions: Map<UUID, Long>
)

data class CommunityBuildingPlayerReward(
    val playerUuid: UUID,
    val blockId: String,
    val units: Long,
    val amount: Long
)

data class CommunityBuildingSettlementPlan(
    val playerRewards: List<CommunityBuildingPlayerReward>,
    val communityIncome: Long,
    val theoreticalCommunityIncome: Long
)

object CommunityBuildingSettlement {
    fun plan(
        entries: List<CommunityBuildingEntry>,
        stats: List<CommunityBuildingBlockStats>,
        playerWeeklyCap: Long,
        playerWeekUsage: Map<UUID, Long>,
        communityWeeklyCap: Long,
        communityWeekUsage: Long
    ): CommunityBuildingSettlementPlan {
        require(playerWeeklyCap >= 0L) { "player weekly cap must not be negative" }
        require(communityWeeklyCap >= 0L) { "community weekly cap must not be negative" }
        require(communityWeekUsage >= 0L) { "community week usage must not be negative" }
        val entryByBlock = buildEntryByBlock(entries.filter { it.active })
        val rewards = mutableListOf<CommunityBuildingPlayerReward>()
        var communityNumerator = BigInteger.ZERO
        for (stat in stats) {
            validateStat(stat)
            val entry = entryByBlock[stat.blockId] ?: continue
            val netUnits = (stat.placedCount - stat.brokenCount).coerceAtLeast(0L)
            if (netUnits == 0L) continue
            communityNumerator = communityNumerator.add(BigInteger.valueOf(netUnits).multiply(BigInteger.valueOf(entry.rewardPerBlock)))
            rewards += allocatePlayerRewards(stat, entry, netUnits)
        }
        val cappedRewards = applyPlayerWeeklyCap(rewards, playerWeeklyCap, playerWeekUsage)
        val theoreticalCommunityIncome = toLongExact(communityNumerator.divide(BigInteger.valueOf(5L)), "community income")
        val remainingCommunityCap = (communityWeeklyCap - communityWeekUsage).coerceAtLeast(0L)
        val communityIncome = minOf(theoreticalCommunityIncome, remainingCommunityCap)
        return CommunityBuildingSettlementPlan(cappedRewards, communityIncome, theoreticalCommunityIncome)
    }

    private fun buildEntryByBlock(entries: List<CommunityBuildingEntry>): Map<String, CommunityBuildingEntry> {
        val result = LinkedHashMap<String, CommunityBuildingEntry>()
        for (entry in entries) {
            require(entry.unitCost > 0) { "unit cost must be positive" }
            require(entry.rewardPerBlock > 0L) { "reward must be positive" }
            for (blockId in entry.trackedBlockIds()) {
                val previous = result.putIfAbsent(blockId, entry)
                require(previous == null || previous.baseBlockId == entry.baseBlockId) {
                    "building block $blockId conflicts between ${previous?.baseBlockId} and ${entry.baseBlockId}"
                }
            }
        }
        return result
    }

    private fun validateStat(stat: CommunityBuildingBlockStats) {
        require(stat.blockId.isNotBlank()) { "block id must not be blank" }
        require(stat.placedCount >= 0L) { "placed count must not be negative" }
        require(stat.brokenCount >= 0L) { "broken count must not be negative" }
        stat.playerContributions.values.forEach { require(it != Long.MIN_VALUE) { "player contribution is invalid" } }
    }

    private fun allocatePlayerRewards(
        stat: CommunityBuildingBlockStats,
        entry: CommunityBuildingEntry,
        netUnits: Long
    ): List<CommunityBuildingPlayerReward> {
        val positives = stat.playerContributions
            .filterValues { it > 0L }
            .toSortedMap(compareBy { it.toString() })
        if (positives.isEmpty()) return emptyList()
        val totalPositive = positives.values.fold(0L) { acc, value -> Math.addExact(acc, value) }
        val rewardUnits = minOf(netUnits, totalPositive)
        val unitsByPlayer = LinkedHashMap<UUID, Long>()
        var assigned = 0L
        for ((uuid, positive) in positives) {
            val units = toLongExact(
                BigInteger.valueOf(rewardUnits).multiply(BigInteger.valueOf(positive)).divide(BigInteger.valueOf(totalPositive)),
                "player reward units"
            )
            unitsByPlayer[uuid] = units
            assigned = Math.addExact(assigned, units)
        }
        var remaining = rewardUnits - assigned
        val iterator = positives.keys.iterator()
        while (remaining > 0L && iterator.hasNext()) {
            val uuid = iterator.next()
            unitsByPlayer[uuid] = Math.addExact(unitsByPlayer.getValue(uuid), 1L)
            remaining--
        }
        return unitsByPlayer.entries
            .filter { it.value > 0L }
            .map { (uuid, units) ->
                CommunityBuildingPlayerReward(
                    uuid,
                    stat.blockId,
                    units,
                    toLongExact(BigInteger.valueOf(units).multiply(BigInteger.valueOf(entry.rewardPerBlock)), "player reward")
                )
            }
    }

    private fun applyPlayerWeeklyCap(
        rewards: List<CommunityBuildingPlayerReward>,
        playerWeeklyCap: Long,
        playerWeekUsage: Map<UUID, Long>
    ): List<CommunityBuildingPlayerReward> {
        val usage = playerWeekUsage.mapValues { (_, value) ->
            require(value >= 0L) { "player week usage must not be negative" }
            value
        }.toMutableMap()
        return rewards.mapNotNull { reward ->
            val used = usage[reward.playerUuid] ?: 0L
            val remaining = (playerWeeklyCap - used).coerceAtLeast(0L)
            val effective = minOf(reward.amount, remaining)
            usage[reward.playerUuid] = Math.addExact(used, effective)
            if (effective <= 0L) null else reward.copy(amount = effective)
        }
    }

    private fun toLongExact(value: BigInteger, label: String): Long = try {
        value.longValueExact()
    } catch (error: ArithmeticException) {
        throw ArithmeticException("$label overflows Long")
    }
}
