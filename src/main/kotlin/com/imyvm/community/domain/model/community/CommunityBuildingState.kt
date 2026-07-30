package com.imyvm.community.domain.model.community

import java.util.UUID

data class CommunityBuildingEntry(
    var baseBlockId: String,
    var unitCost: Int,
    var rewardPerBlock: Long,
    var linkedBlockIds: MutableList<String> = mutableListOf()
) {
    fun trackedBlockIds(): List<String> = listOf(baseBlockId) + linkedBlockIds.distinct().filter { it != baseBlockId }
}

data class CommunityBuildingCatalogEntry(
    var baseBlockId: String,
    var unitCost: Int,
    var rewardPerBlock: Long,
    var linkedBlockIds: MutableList<String> = mutableListOf()
)

data class CommunityBuildingWeekLedger(
    var weekPeriodId: String,
    var settledAmount: Long
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
    var pendingPayouts: MutableList<CommunityBuildingPendingPayout> = mutableListOf()
) {
    fun usedCapacityUnits(): Int = stylePackage.sumOf { it.unitCost }

    fun remainingCapacityUnits(): Int = (capacityUnits - usedCapacityUnits()).coerceAtLeast(0)

    fun findEntry(baseBlockId: String): CommunityBuildingEntry? =
        stylePackage.firstOrNull { it.baseBlockId.equals(baseBlockId, ignoreCase = true) }
}
