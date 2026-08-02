package com.imyvm.community.application.townbuilding

import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BuildingRewardPreviewTracker {
    private val validPlacementMinute = ConcurrentHashMap<UUID, String>()

    fun recordPlacement(player: ServerPlayer, world: Level, pos: BlockPos, blockId: String) {
        val minuteKey = currentMinuteKey()
        if (validPlacementMinute[player.uuid] == minuteKey) return
        if (!isValidBuildingPlacement(world, pos, blockId)) return
        validPlacementMinute[player.uuid] = minuteKey
    }

    fun hasValidPlacementThisMinute(playerUuid: UUID): Boolean =
        validPlacementMinute[playerUuid] == currentMinuteKey()

    private fun isValidBuildingPlacement(world: Level, pos: BlockPos, blockId: String): Boolean {
        val region = RegionDataApi.getRegionScopePairByLocation(world, pos)?.first ?: return false
        val community = CommunityDatabase.communities.firstOrNull { it.regionNumberId == region.numberID } ?: return false
        return community.buildingState.activeEntries().any { it.trackedBlockIds().contains(blockId) }
    }

    private fun currentMinuteKey(): String =
        LocalDateTime.now(ZoneId.of(CommunityConfig.TIMEZONE.value)).format(MINUTE_FORMATTER)

    private val MINUTE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")
}
