package com.imyvm.community.domain.model

import com.imyvm.community.application.development.CommunityDevelopmentService
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.development.CommunityDevelopmentInputs
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommunityDevelopmentPhaseSevenTest {
    @Test
    fun developmentUsesBuildingPopulationAndHabitationModifier() {
        val inputs = CommunityDevelopmentInputs(
            memberCount = 9,
            weekActiveMemberCount = 4,
            totalTheoreticalBuildingIncome = 10_000L,
            weekTheoreticalBuildingIncome = 5_000L,
            totalHabitationMillis = 3_600_000L,
            averageHabitationMillis = 1_800_000L
        )
        val (development, breakdown) = CommunityDevelopmentService.calculateDevelopment(inputs)
        assertEquals(sqrt(200.0), breakdown.building, 0.000001)
        assertEquals(2.0, breakdown.population, 0.000001)
        assertEquals(ln(2.0), breakdown.habitation, 0.000001)
        assertEquals(0.7, breakdown.habitationModifier, 0.000001)
        assertEquals((sqrt(200.0) + 2.0 + ln(2.0)) * 0.7, development, 0.000001)
    }

    @Test
    fun landPriceRoundsAreaAndCapsActivePrice() {
        val snapshot = CommunityDevelopmentService.calculateLandPrice(BigDecimal("10.5"), 90_000_000L, 1_000L)
        assertEquals(11L, snapshot.area)
        assertEquals(33L, snapshot.activePrice)
        assertEquals(100L, snapshot.buildingPrice)
        assertEquals(144L, snapshot.totalPrice)
    }


    @Test
    fun weightedHabitationUsesRegionAreaRatioAndEquivalentChunks() {
        val oneHourFromTicks = 72_000L * 50L
        val (total, average) = CommunityDevelopmentService.calculateWeightedHabitationMillis(
            listOf(
                0.25 to oneHourFromTicks,
                0.75 to oneHourFromTicks * 2L,
                0.50 to 0L
            )
        )
        assertEquals(6_300_000L, total)
        assertEquals(4_200_000L, average)
    }

    @Test
    fun developmentClampsAverageHabitationRatioOnlyForModifier() {
        val inputs = CommunityDevelopmentInputs(
            memberCount = 1,
            weekActiveMemberCount = 1,
            totalTheoreticalBuildingIncome = 0L,
            weekTheoreticalBuildingIncome = 0L,
            totalHabitationMillis = 7_200_000L,
            averageHabitationMillis = 7_200_000L
        )
        val (development, breakdown) = CommunityDevelopmentService.calculateDevelopment(inputs)
        assertEquals(1.0, breakdown.habitationModifier, 0.000001)
        assertEquals(0.8 + ln(3.0), development, 0.000001)
    }

    @Test
    fun landPriceAreaRoundingBoundaries() {
        assertFailsWith<IllegalArgumentException> {
            CommunityDevelopmentService.calculateLandPrice(BigDecimal("0.49"), 0L, 0L)
        }
        assertEquals(1L, CommunityDevelopmentService.calculateLandPrice(BigDecimal("0.50"), 0L, 0L).area)
        assertEquals(1L, CommunityDevelopmentService.calculateLandPrice(BigDecimal("1.49"), 0L, 0L).area)
        assertEquals(2L, CommunityDevelopmentService.calculateLandPrice(BigDecimal("1.50"), 0L, 0L).area)
    }

    @Test
    fun landPriceSumsTwentyFiveChunksBeforeCapping() {
        val oneHour = 3_600_000L
        val belowAverage = CommunityDevelopmentService.calculateLandPrice(BigDecimal("100"), oneHour * 24L, 0L)
        val exactlyAverage = CommunityDevelopmentService.calculateLandPrice(BigDecimal("100"), oneHour * 25L, 0L)
        val aboveAverage = CommunityDevelopmentService.calculateLandPrice(BigDecimal("100"), oneHour * 26L, 0L)
        val oneHugeChunkButAverageBelow = CommunityDevelopmentService.calculateLandPrice(BigDecimal("100"), oneHour * 20L, 0L)
        assertEquals(288L, belowAverage.activePrice)
        assertEquals(300L, exactlyAverage.activePrice)
        assertEquals(300L, aboveAverage.activePrice)
        assertEquals(240L, oneHugeChunkButAverageBelow.activePrice)
    }

    @Test
    fun invalidNumbersDoNotProduceSnapshots() {
        assertFailsWith<IllegalArgumentException> {
            CommunityDevelopmentService.calculateWeightedHabitationMillis(listOf(Double.NaN to 1L))
        }
        assertFailsWith<ArithmeticException> {
            CommunityDevelopmentService.calculateLandPrice(BigDecimal.valueOf(Long.MAX_VALUE), 90_000_000L, Long.MAX_VALUE)
        }
    }

    @Test
    fun updatePersistsDevelopmentAndLandSnapshotOnCommunity() {
        val community = Community(
            regionNumberId = 1,
            member = HashMap(mapOf(UUID.fromString("00000000-0000-0000-0000-000000000701") to MemberAccount(0L, MemberRoleType.MEMBER))),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_MANOR
        )
        val inputs = CommunityDevelopmentInputs(1, 1, 100L, 100L, 0L, 0L)
        CommunityDevelopmentService.updateDevelopment(community, "2026-W31", inputs, 1L)
        CommunityDevelopmentService.updateLandPrice(community, BigDecimal.ONE, 0L, 100L)
        assertEquals("2026-W31", community.developmentState.weekKey)
        assertEquals(10L, community.developmentState.landPrice?.buildingPrice)
    }

    @Test
    fun rejectsNegativeInputsAndTinyArea() {
        assertFailsWith<IllegalArgumentException> {
            CommunityDevelopmentService.calculateDevelopment(CommunityDevelopmentInputs(-1, 0, 0L, 0L, 0L, 0L))
        }
        assertFailsWith<IllegalArgumentException> {
            CommunityDevelopmentService.calculateLandPrice(BigDecimal.ZERO, 0L, 0L)
        }
    }
}
