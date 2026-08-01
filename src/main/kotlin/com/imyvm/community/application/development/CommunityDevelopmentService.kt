package com.imyvm.community.application.development

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.townbuilding.CommunityBuildingBlockStats
import com.imyvm.community.application.townbuilding.CommunityBuildingSettlement
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoDimensionChunk
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeBatchRequest
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeCompleteness
import com.imyvm.iwg.domain.WorldGeoPeriodDataStatus
import com.imyvm.iwg.domain.WorldGeoSpaceGeometryStatus
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.community.domain.model.development.CommunityDevelopmentBreakdown
import com.imyvm.community.domain.model.development.CommunityDevelopmentInputs
import com.imyvm.community.domain.model.development.CommunityDevelopmentState
import com.imyvm.community.domain.model.development.CommunityLandPriceSnapshot
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.ln
import kotlin.math.sqrt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CommunityDevelopmentService {
    private const val HOUR_MILLIS = 3_600_000L
    private const val ACTIVE_PRICE_FACTOR = 3L
    private const val ACTIVE_PRICE_CAP_MILLIS = 25L * HOUR_MILLIS

    private val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")

    fun refreshDevelopmentFromCurrentState(community: Community): Result<CommunityDevelopmentState> = runCatching {
        val currentWeek = RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK]?.let { "${it.timelineId}:${it.periodId}" } ?: "-"
        val formalMembers = community.member.values.count { account ->
            account.basicRoleType == MemberRoleType.OWNER || account.basicRoleType == MemberRoleType.ADMIN || account.basicRoleType == MemberRoleType.MEMBER
        }
        val weekBuildingIncome = community.buildingState.playerWeekLedgers.values
            .filter { it.weekPeriodId == currentWeek }
            .fold(0L) { total, ledger -> Math.addExact(total, ledger.settledAmount.coerceAtLeast(0L)) }
        updateDevelopment(
            community,
            currentWeek,
            CommunityDevelopmentInputs(
                formalMembers,
                community.buildingState.playerWeekLedgers.count { it.value.weekPeriodId == currentWeek },
                weekBuildingIncome,
                weekBuildingIncome,
                0L,
                0L
            )
        )
    }

    fun refreshRegionLandPrice(community: Community): Result<CommunityLandPriceSnapshot> = runCatching {
        val server = WorldGeoCommunityAddon.server ?: error("server unavailable")
        val region = community.getRegion() ?: error("community region unavailable")
        val area = BigDecimal.valueOf(RegionDataApi.getRegionArea(region))
        val geometry = RegionDataApi.getRegionGeometryFacts(region)
            .firstOrNull { it.status == WorldGeoSpaceGeometryStatus.AVAILABLE }
            ?: error("region geometry data unavailable")
        val center = geometry.centroidChunk ?: error("region centroid chunk unavailable")
        val chunks = (-2..2).flatMap { dx ->
            (-2..2).map { dz -> WorldGeoDimensionChunk(center.dimensionId, center.chunkX + dx, center.chunkZ + dz) }
        }
        val request = WorldGeoNativeInhabitedTimeBatchRequest(chunks, "community-land-price:${community.regionNumberId}:${geometry.geometryVersion}")
        val native = RegionDataApi.queryNativeInhabitedTimeBatchAsync(server, request).join()
        val bad = native.readings.firstOrNull { it.completeness != WorldGeoNativeInhabitedTimeCompleteness.COMPLETE || it.inhabitedMillis == null }
        if (bad != null) error("native inhabited time data unavailable: ${bad.completeness.name}")
        val totalMillis = native.readings.fold(0L) { total, reading -> Math.addExact(total, reading.inhabitedMillis!!) }
        val buildingIncome = latestClosedProductionHourBuildingIncome(community)
        val snapshot = updateLandPrice(community, area, totalMillis, buildingIncome)
        com.imyvm.community.infra.CommunityDatabase.save()
        snapshot
    }

    private fun latestClosedProductionHourBuildingIncome(community: Community): Long {
        val regionId = community.regionNumberId ?: return 0L
        val entries = community.buildingState.activeEntries()
        if (entries.isEmpty()) return 0L
        val currentHour = RegionDataApi.getCurrentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0L
        val previousHour = runCatching { LocalDateTime.parse(currentHour, HOUR_FORMATTER).minusHours(1).format(HOUR_FORMATTER) }.getOrNull() ?: return 0L
        val blockIds = entries.flatMap { it.trackedBlockIds() }.distinct().toSet()
        val batch = RegionDataApi.queryProductionBlockDeltaBatchAsync(NaturalPeriodKind.HOUR, previousHour, regionId, blockIds).join()
        if (batch.completeness.status != WorldGeoPeriodDataStatus.COMPLETE) error("production building hour data unavailable: ${batch.completeness.status.name}")
        val stats = batch.blocks.map { (blockId, delta) ->
            CommunityBuildingBlockStats(blockId, delta.placedCount, delta.brokenCount, delta.playerContributions)
        }
        return CommunityBuildingSettlement.plan(entries, stats, Long.MAX_VALUE, emptyMap(), Long.MAX_VALUE, 0L).theoreticalCommunityIncome
    }

    fun calculateDevelopment(inputs: CommunityDevelopmentInputs): Pair<Double, CommunityDevelopmentBreakdown> {
        require(inputs.memberCount >= 0) { "memberCount must be non-negative" }
        require(inputs.weekActiveMemberCount >= 0) { "weekActiveMemberCount must be non-negative" }
        require(inputs.totalTheoreticalBuildingIncome >= 0L) { "totalTheoreticalBuildingIncome must be non-negative" }
        require(inputs.weekTheoreticalBuildingIncome >= 0L) { "weekTheoreticalBuildingIncome must be non-negative" }
        require(inputs.totalHabitationMillis >= 0L) { "totalHabitationMillis must be non-negative" }
        require(inputs.averageHabitationMillis >= 0L) { "averageHabitationMillis must be non-negative" }

        val totalBuildingYuan = inputs.totalTheoreticalBuildingIncome / 100.0
        val weekBuildingYuan = inputs.weekTheoreticalBuildingIncome / 100.0
        val building = sqrt(totalBuildingYuan + 2.0 * weekBuildingYuan)
        val population = 0.4 * sqrt(inputs.memberCount.toDouble()) + 0.4 * sqrt(inputs.weekActiveMemberCount.toDouble())
        val habitation = ln(1.0 + inputs.totalHabitationMillis.toDouble() / HOUR_MILLIS)
        val averageRatio = (inputs.averageHabitationMillis.toDouble() / HOUR_MILLIS).coerceIn(0.0, 1.0)
        val modifier = 0.4 + 0.6 * averageRatio
        val development = (building + population + habitation) * modifier
        require(development.isFinite() && development >= 0.0) { "invalid development result" }
        return development to CommunityDevelopmentBreakdown(building, population, habitation, modifier)
    }

    fun updateDevelopment(
        community: Community,
        weekKey: String,
        inputs: CommunityDevelopmentInputs,
        updatedAtMillis: Long = System.currentTimeMillis()
    ): CommunityDevelopmentState {
        val (development, breakdown) = calculateDevelopment(inputs)
        val state = CommunityDevelopmentState(weekKey, updatedAtMillis, development, inputs, breakdown, community.developmentState.landPrice)
        community.developmentState = state
        return state
    }

    fun calculateLandPrice(
        area: BigDecimal,
        total25HabitationMillis: Long,
        theoreticalBuildingIncome: Long
    ): CommunityLandPriceSnapshot {
        require(total25HabitationMillis >= 0L) { "total25HabitationMillis must be non-negative" }
        require(theoreticalBuildingIncome >= 0L) { "theoreticalBuildingIncome must be non-negative" }
        val areaUnits = area.setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(areaUnits >= 1L) { "area must be at least 1" }
        val cappedMillis = total25HabitationMillis.coerceAtMost(ACTIVE_PRICE_CAP_MILLIS)
        val activePrice = BigInteger.valueOf(areaUnits)
            .multiply(BigInteger.valueOf(ACTIVE_PRICE_FACTOR))
            .multiply(BigInteger.valueOf(cappedMillis))
            .divide(BigInteger.valueOf(ACTIVE_PRICE_CAP_MILLIS))
            .longValueExact()
        val buildingPrice = theoreticalBuildingIncome / 10L
        val totalPrice = Math.addExact(Math.addExact(areaUnits, activePrice), buildingPrice)
        return CommunityLandPriceSnapshot(areaUnits, total25HabitationMillis, theoreticalBuildingIncome, activePrice, buildingPrice, totalPrice)
    }

    fun updateLandPrice(
        community: Community,
        area: BigDecimal,
        total25HabitationMillis: Long,
        theoreticalBuildingIncome: Long
    ): CommunityLandPriceSnapshot {
        val snapshot = calculateLandPrice(area, total25HabitationMillis, theoreticalBuildingIncome)
        community.developmentState.landPrice = snapshot
        return snapshot
    }
}
