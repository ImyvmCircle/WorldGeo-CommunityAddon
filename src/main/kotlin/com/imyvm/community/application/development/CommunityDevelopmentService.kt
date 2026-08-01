package com.imyvm.community.application.development

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.townbuilding.CommunityBuildingBlockStats
import com.imyvm.community.application.townbuilding.CommunityBuildingSettlement
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.development.CommunityDevelopmentActivityWeek
import com.imyvm.community.domain.model.development.CommunityDevelopmentBreakdown
import com.imyvm.community.domain.model.development.CommunityDevelopmentInputs
import com.imyvm.community.domain.model.development.CommunityDevelopmentState
import com.imyvm.community.domain.model.development.CommunityLandPriceSnapshot
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTimelineType
import com.imyvm.iwg.domain.WorldGeoChunkCoverage
import com.imyvm.iwg.domain.WorldGeoDimensionChunk
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeBatchRequest
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeCompleteness
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeReading
import com.imyvm.iwg.domain.WorldGeoPeriodDataStatus
import com.imyvm.iwg.domain.WorldGeoSpaceGeometryFact
import com.imyvm.iwg.domain.WorldGeoSpaceGeometryStatus
import com.imyvm.iwg.inter.api.RegionDataApi
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import kotlin.math.ln
import kotlin.math.sqrt

object CommunityDevelopmentService {
    private const val HOUR_MILLIS = 3_600_000L
    private const val ACTIVE_PRICE_FACTOR = 3L
    private const val ACTIVE_PRICE_CAP_MILLIS = 25L * HOUR_MILLIS
    private const val MAX_DEVELOPMENT_WEEKS = 256
    private const val MAX_ACTIVE_WEEK_RECORDS = 16

    private val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")

    fun recordMemberLogin(playerUuid: UUID) {
        val weekKey = RegionDataApi.getCurrentNaturalPeriodKeys()[NaturalPeriodKind.WEEK]?.let(::periodLedgerKey) ?: return
        var changed = false
        for (community in CommunityDatabase.communities) {
            if (!community.hasFormalDevelopmentMember(playerUuid)) continue
            val record = community.developmentState.activeMemberWeeks.firstOrNull { it.weekKey == weekKey }
                ?: CommunityDevelopmentActivityWeek(weekKey).also { community.developmentState.activeMemberWeeks.add(it) }
            if (record.playerUuids.add(playerUuid)) changed = true
            while (community.developmentState.activeMemberWeeks.size > MAX_ACTIVE_WEEK_RECORDS) {
                community.developmentState.activeMemberWeeks.removeAt(0)
                changed = true
            }
        }
        if (changed) CommunityDatabase.save()
    }

    fun calculateDevelopmentFromCurrentState(community: Community): Result<CommunityDevelopmentState> = runCatching {
        val server = WorldGeoCommunityAddon.server ?: error("server unavailable")
        val region = community.getRegion() ?: error("community region unavailable")
        val weekKey = latestClosedProductionPeriod(NaturalPeriodKind.WEEK) ?: error("closed production week unavailable")
        val periodKey = periodLedgerKey(weekKey)
        val formalMembers = community.member.keys.count { uuid -> community.hasFormalDevelopmentMember(uuid) }
        val weekActiveMembers = community.developmentState.activeMemberWeeks
            .firstOrNull { it.weekKey == periodKey }
            ?.playerUuids
            ?.count { uuid -> community.hasFormalDevelopmentMember(uuid) }
            ?: 0
        val geometryFacts = RegionDataApi.getRegionGeometryFacts(region)
        val habitation = readWeightedRegionHabitation(server, geometryFacts, "community-development:${community.regionNumberId}:$periodKey")
        val weekBuildingIncome = theoreticalBuildingIncome(community, weekKey)
        val totalBuildingIncome = totalClosedProductionWeekBuildingIncome(community, weekKey)
        val inputs = CommunityDevelopmentInputs(
            formalMembers,
            weekActiveMembers,
            totalBuildingIncome,
            weekBuildingIncome,
            habitation.first,
            habitation.second
        )
        val (development, breakdown) = calculateDevelopment(inputs)
        CommunityDevelopmentState(periodKey, System.currentTimeMillis(), development, inputs, breakdown, community.developmentState.landPrice, community.developmentState.activeMemberWeeks)
    }

    fun refreshDevelopmentFromCurrentState(community: Community): Result<CommunityDevelopmentState> =
        calculateDevelopmentFromCurrentState(community).map { state ->
            community.developmentState = state
            CommunityDatabase.save()
            state
        }

    fun calculateRegionLandPrice(community: Community): Result<CommunityLandPriceSnapshot> = runCatching {
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
        val hourKey = latestClosedProductionPeriod(NaturalPeriodKind.HOUR)
        val inputVersion = "community-land-price:${community.regionNumberId}:${geometry.geometryVersion}:${hourKey?.periodId ?: "-"}"
        val request = WorldGeoNativeInhabitedTimeBatchRequest(chunks, inputVersion)
        val native = RegionDataApi.queryNativeInhabitedTimeBatchAsync(server, request).join()
        val totalMillis = completeNativeMillis(native.readings)
        val buildingIncome = hourKey?.let { theoreticalBuildingIncome(community, it) } ?: 0L
        calculateLandPrice(area, totalMillis, buildingIncome)
    }

    fun refreshRegionLandPrice(community: Community): Result<CommunityLandPriceSnapshot> =
        calculateRegionLandPrice(community).map { snapshot ->
            community.developmentState.landPrice = snapshot
            CommunityDatabase.save()
            snapshot
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
        require(listOf(building, population, habitation, modifier, development).all(Double::isFinite) && development >= 0.0) { "invalid development result" }
        return development to CommunityDevelopmentBreakdown(building, population, habitation, modifier)
    }

    fun calculateWeightedHabitationMillis(samples: List<Pair<Double, Long>>): Pair<Long, Long> {
        require(samples.isNotEmpty()) { "coverage must not be empty" }
        var weightedTotal = 0.0
        var equivalentChunks = 0.0
        for ((areaRatio, millis) in samples) {
            require(areaRatio.isFinite() && areaRatio > 0.0) { "area ratio must be positive and finite" }
            require(millis >= 0L) { "inhabited millis must be non-negative" }
            weightedTotal += areaRatio * millis.toDouble()
            equivalentChunks += areaRatio
        }
        require(weightedTotal.isFinite() && equivalentChunks.isFinite() && equivalentChunks > 0.0) { "invalid weighted habitation" }
        val total = weightedTotal.toLong()
        val average = (weightedTotal / equivalentChunks).toLong()
        require(total >= 0L && average >= 0L) { "weighted habitation overflow" }
        return total to average
    }

    fun updateDevelopment(
        community: Community,
        weekKey: String,
        inputs: CommunityDevelopmentInputs,
        updatedAtMillis: Long = System.currentTimeMillis()
    ): CommunityDevelopmentState {
        val (development, breakdown) = calculateDevelopment(inputs)
        val state = CommunityDevelopmentState(weekKey, updatedAtMillis, development, inputs, breakdown, community.developmentState.landPrice, community.developmentState.activeMemberWeeks)
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

    private fun readWeightedRegionHabitation(
        server: net.minecraft.server.MinecraftServer,
        geometryFacts: List<WorldGeoSpaceGeometryFact>,
        inputVersion: String
    ): Pair<Long, Long> {
        val available = geometryFacts.filter { it.status == WorldGeoSpaceGeometryStatus.AVAILABLE }
        require(available.isNotEmpty()) { "region geometry data unavailable" }
        val native = RegionDataApi.queryNativeInhabitedTimeForSpacesAsync(server, available, inputVersion).join()
        val readings = native.readings.associateBy { it.chunk }
        val samples = available.flatMap { fact -> fact.chunkCoverage.map { coverage -> coverage to readings[coverage.chunk] } }
            .map { (coverage, reading) -> coverage.areaRatio to completeNativeMillis(reading ?: error("native inhabited time data unavailable")) }
        return calculateWeightedHabitationMillis(samples)
    }

    private fun completeNativeMillis(readings: List<WorldGeoNativeInhabitedTimeReading>): Long =
        readings.fold(0L) { total, reading -> Math.addExact(total, completeNativeMillis(reading)) }

    private fun completeNativeMillis(reading: WorldGeoNativeInhabitedTimeReading): Long {
        val millis = reading.inhabitedMillis
        if (reading.completeness != WorldGeoNativeInhabitedTimeCompleteness.COMPLETE || millis == null) {
            error("native inhabited time data unavailable: ${reading.completeness.name}")
        }
        return millis
    }

    private fun totalClosedProductionWeekBuildingIncome(community: Community, latest: NaturalPeriodKey): Long {
        val range = RegionDataApi.getAvailableNaturalPeriodRange(latest.timelineId, NaturalPeriodKind.WEEK) ?: return theoreticalBuildingIncome(community, latest)
        var current: NaturalPeriodKey? = range.earliest
        var total = 0L
        var count = 0
        while (current != null && comparePeriodIds(NaturalPeriodKind.WEEK, current.periodId, latest.periodId) <= 0 && count < MAX_DEVELOPMENT_WEEKS) {
            total = Math.addExact(total, theoreticalBuildingIncome(community, current))
            current = nextPeriodKey(current.timelineId, NaturalPeriodKind.WEEK, current.periodId)
            count++
        }
        return total
    }

    private fun theoreticalBuildingIncome(community: Community, periodKey: NaturalPeriodKey): Long {
        val regionId = community.regionNumberId ?: return 0L
        val entries = community.buildingState.activeEntries()
        if (entries.isEmpty()) return 0L
        val blockIds = entries.flatMap { it.trackedBlockIds() }.distinct().toSet()
        if (blockIds.isEmpty()) return 0L
        val batch = RegionDataApi.queryProductionBlockDeltaBatchAsync(periodKey.kind, periodKey.periodId, regionId, blockIds).join()
        if (batch.completeness.status != WorldGeoPeriodDataStatus.COMPLETE) error("production building ${periodKey.kind.name.lowercase(Locale.ROOT)} data unavailable: ${batch.completeness.status.name}")
        val stats = batch.blocks.map { (blockId, delta) ->
            CommunityBuildingBlockStats(blockId, delta.placedCount, delta.brokenCount, delta.playerContributions)
        }
        return CommunityBuildingSettlement.plan(entries, stats, Long.MAX_VALUE, emptyMap(), Long.MAX_VALUE, 0L).theoreticalCommunityIncome
    }

    private fun latestClosedProductionPeriod(kind: NaturalPeriodKind): NaturalPeriodKey? {
        val timeline = RegionDataApi.getAvailableNaturalPeriodTimelines()
            .filter { it.type == NaturalPeriodTimelineType.PRODUCTION }
            .maxByOrNull { it.sequence } ?: return null
        val range = RegionDataApi.getAvailableNaturalPeriodRange(timeline.timelineId, kind) ?: return null
        return if (timeline.closed) range.latest else previousPeriodKey(range.latest)
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

    private fun parseWeekStart(periodId: String): LocalDate = LocalDate.parse("$periodId-1", DateTimeFormatter.ISO_WEEK_DATE)

    private fun formatWeek(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        return String.format(Locale.ROOT, "%04d-W%02d", date.get(weekFields.weekBasedYear()), date.get(weekFields.weekOfWeekBasedYear()))
    }

    private fun periodLedgerKey(key: NaturalPeriodKey): String = "${key.timelineId}:${key.periodId}"

    private fun Community.hasFormalDevelopmentMember(uuid: UUID): Boolean = when (member[uuid]?.basicRoleType) {
        MemberRoleType.OWNER, MemberRoleType.ADMIN, MemberRoleType.MEMBER -> true
        else -> false
    }
}
