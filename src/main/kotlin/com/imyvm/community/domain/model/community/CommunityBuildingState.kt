package com.imyvm.community.domain.model.community

import java.util.UUID

data class CommunityBuildingEntry(
    var baseBlockId: String,
    var unitCost: Int,
    var rewardPerBlock: Long,
    var linkedBlockIds: MutableList<String> = mutableListOf(),
    var templateVersion: Long = 1L,
    var selectionCheckpoint: String = "",
    var active: Boolean = true
) {
    fun trackedBlockIds(): List<String> = listOf(baseBlockId) + linkedBlockIds.distinct().filter { it != baseBlockId }
    fun frozenTemplate(): CommunityBuildingCatalogEntry = CommunityBuildingCatalogEntry(
        baseBlockId, unitCost, rewardPerBlock, linkedBlockIds.toMutableList(), templateVersion
    )
}

data class CommunityBuildingCatalogEntry(
    var baseBlockId: String,
    var unitCost: Int,
    var rewardPerBlock: Long,
    var linkedBlockIds: MutableList<String> = mutableListOf(),
    var templateVersion: Long = 1L
) {
    fun trackedBlockIds(): List<String> = listOf(baseBlockId) + linkedBlockIds.distinct().filter { it != baseBlockId }
}

data class CommunityBuildingWeekLedger(
    var weekPeriodId: String,
    var settledAmount: Long,
    var baseCapAmount: Long = settledAmount,
    var extraCapAmount: Long = 0L
)

data class CommunityBuildingCommunityWeekLedger(
    var weekPeriodId: String,
    var settledAmount: Long
)

data class CommunityBuildingPlayerNetLedger(
    var weekPeriodId: String,
    var blockId: String,
    var cumulativeNet: Long,
    var peakNet: Long
)

data class CommunityBuildingPendingPayout(
    val playerUuid: UUID,
    val amount: Long,
    val hourPeriodId: String,
    val weekPeriodId: String,
    val blockCount: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class CommunityBuildingState(
    var capacityUnits: Int = 12,
    var stylePackage: MutableList<CommunityBuildingEntry> = mutableListOf(),
    var processedHourPeriodIds: MutableList<String> = mutableListOf(),
    var processedWeekPeriodIds: MutableList<String> = mutableListOf(),
    var playerWeekLedgers: HashMap<UUID, CommunityBuildingWeekLedger> = hashMapOf(),
    var communityWeekLedgers: MutableList<CommunityBuildingCommunityWeekLedger> = mutableListOf(),
    var pendingPayouts: MutableList<CommunityBuildingPendingPayout> = mutableListOf(),
    var playerNetLedgers: HashMap<UUID, MutableList<CommunityBuildingPlayerNetLedger>> = hashMapOf()
) {
    fun activeEntries(): List<CommunityBuildingEntry> = stylePackage.filter { it.active }

    fun usedCapacityUnits(): Int = activeEntries().sumOf { it.unitCost }

    fun remainingCapacityUnits(): Int = (capacityUnits - usedCapacityUnits()).coerceAtLeast(0)

    fun findEntry(baseBlockId: String): CommunityBuildingEntry? =
        stylePackage.firstOrNull { it.active && it.baseBlockId.equals(baseBlockId, ignoreCase = true) }

    fun validateUniqueBlockMapping(): Result<Unit> {
        val ownerByBlock = LinkedHashMap<String, String>()
        for (entry in activeEntries()) {
            for (blockId in entry.trackedBlockIds()) {
                val previous = ownerByBlock.putIfAbsent(blockId, entry.baseBlockId)
                if (previous != null && previous != entry.baseBlockId) {
                    return Result.failure(IllegalStateException("building block $blockId conflicts between $previous and ${entry.baseBlockId}"))
                }
            }
        }
        return Result.success(Unit)
    }
}
