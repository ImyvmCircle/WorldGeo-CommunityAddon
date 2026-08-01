package com.imyvm.community.domain.model

import com.imyvm.community.application.fiscal.CommunityFiscalService
import com.imyvm.community.domain.model.community.CommunityBuildingWeekLedger
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.fiscal.CommunityFiscalLineStatus
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import com.imyvm.community.domain.model.fiscal.CommunityFiscalSettlement
import com.imyvm.community.domain.model.fiscal.CommunityFiscalTaxSettlementLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.util.UUID

class CommunityFiscalPhaseSixTest {
    @Test
    fun marginalTaxUsesCentsAndFloorsOnce() {
        assertEquals(500L, CommunityFiscalService.taxAmount(50_000L))
        assertEquals(8_000L, CommunityFiscalService.taxAmount(200_000L))
        assertEquals(14_000L, CommunityFiscalService.taxAmount(260_000L))
        assertEquals(0L, CommunityFiscalService.taxAmount(99L))
    }


    @Test
    fun marginalTaxCoversThresholdEdges() {
        assertEquals(0L, CommunityFiscalService.taxAmount(0L))
        assertEquals(499L, CommunityFiscalService.taxAmount(49_999L))
        assertEquals(500L, CommunityFiscalService.taxAmount(50_000L))
        assertEquals(500L, CommunityFiscalService.taxAmount(50_001L))
        assertEquals(7_999L, CommunityFiscalService.taxAmount(199_999L))
        assertEquals(8_000L, CommunityFiscalService.taxAmount(200_000L))
        assertEquals(8_000L, CommunityFiscalService.taxAmount(200_001L))
        assertEquals(8_001L, CommunityFiscalService.taxAmount(200_010L))
        assertFailsWith<IllegalArgumentException> { CommunityFiscalService.taxAmount(-1L) }
    }

    @Test
    fun missingOrSingleObservationFreezesZeroTaxAndNoWelfare() {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000121")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000122")
        val community = community(first, second)
        community.fiscalState.activePolicy = CommunityFiscalPolicy.VISIBLE_HAND
        CommunityFiscalService.recordObservation(community, first, "2026-W31", 100_000L, 1L)
        community.buildingState.playerWeekLedgers[first] = CommunityBuildingWeekLedger("2026-W31", 60_000L)
        community.buildingState.playerWeekLedgers[second] = CommunityBuildingWeekLedger("2026-W31", 60_000L)
        val tax = CommunityFiscalService.planCommunityTax(community, "2026-W31")
        val welfare = CommunityFiscalService.planWelfare(community, "2026-W31", community.buildingState.playerWeekLedgers.mapValues { it.value.settledAmount })
        assertEquals(2, tax.size)
        assertEquals(listOf(false, false), tax.map { it.completeObservation })
        assertEquals(listOf(0L, 0L), tax.map { it.taxAmount })
        assertEquals(0, welfare.lines.size)
    }

    @Test
    fun welfareThresholdBoundariesUseCents() {
        val lowTaxable = UUID.fromString("00000000-0000-0000-0000-000000000131")
        val exactTaxable = UUID.fromString("00000000-0000-0000-0000-000000000132")
        val lowBuilding = UUID.fromString("00000000-0000-0000-0000-000000000133")
        val exactBuilding = UUID.fromString("00000000-0000-0000-0000-000000000134")
        val community = community(lowTaxable, exactTaxable, lowBuilding, exactBuilding)
        community.fiscalState.activePolicy = CommunityFiscalPolicy.VISIBLE_HAND
        community.treasuryBalance = 2_000_000L
        listOf(lowTaxable, exactTaxable, lowBuilding, exactBuilding).forEach { player ->
            CommunityFiscalService.recordObservation(community, player, "2026-W31", 0L, 1L)
        }
        CommunityFiscalService.recordObservation(community, lowTaxable, "2026-W31", 119_999L, 2L)
        CommunityFiscalService.recordObservation(community, exactTaxable, "2026-W31", 120_000L, 2L)
        CommunityFiscalService.recordObservation(community, lowBuilding, "2026-W31", 0L, 2L)
        CommunityFiscalService.recordObservation(community, exactBuilding, "2026-W31", 0L, 2L)
        val rewards = mapOf(lowTaxable to 60_000L, exactTaxable to 60_000L, lowBuilding to 59_999L, exactBuilding to 60_000L)
        val plan = CommunityFiscalService.planWelfare(community, "2026-W31", rewards)
        assertEquals(setOf(lowTaxable, exactBuilding), plan.lines.map { it.playerUuid }.toSet())
        assertEquals(12_000L, plan.theoreticalTotal)
    }

    @Test
    fun fiscalSettlementModelKeepsFrozenTransactionStatus() {
        val member = UUID.fromString("00000000-0000-0000-0000-000000000141")
        val settlement = CommunityFiscalSettlement(
            "2026-W31",
            1L,
            CommunityFiscalPolicy.NEOLIBERALISM,
            mutableListOf(CommunityFiscalTaxSettlementLine(member, 50_000L, 500L, 0L, 50_000L, true, status = CommunityFiscalLineStatus.SUBMITTED))
        )
        assertEquals("2026-W31", settlement.weekKey)
        assertEquals(CommunityFiscalLineStatus.SUBMITTED, settlement.taxLines.single().status)
    }

    @Test
    fun observationsUseFirstAndLastWithinSameWeek() {
        val member = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val community = community(member)
        CommunityFiscalService.recordObservation(community, member, "2026-W31", 100_000L, 20L)
        CommunityFiscalService.recordObservation(community, member, "2026-W31", 80_000L, 10L)
        CommunityFiscalService.recordObservation(community, member, "2026-W31", 280_000L, 30L)
        val line = CommunityFiscalService.planCommunityTax(community, "2026-W31").single()
        assertEquals(200_000L, line.taxableIncrease)
        assertEquals(8_000L, line.taxAmount)
    }

    @Test
    fun welfareRequiresPolicyThresholdsAndProportionalTreasury() {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000111")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000112")
        val community = community(first, second)
        community.fiscalState.activePolicy = CommunityFiscalPolicy.VISIBLE_HAND
        community.treasuryBalance = 1_006_000L
        CommunityFiscalService.recordObservation(community, first, "2026-W31", 100_000L, 1L)
        CommunityFiscalService.recordObservation(community, first, "2026-W31", 110_000L, 2L)
        CommunityFiscalService.recordObservation(community, second, "2026-W31", 100_000L, 1L)
        CommunityFiscalService.recordObservation(community, second, "2026-W31", 110_000L, 2L)
        community.buildingState.playerWeekLedgers[first] = CommunityBuildingWeekLedger("2026-W31", 60_000L)
        community.buildingState.playerWeekLedgers[second] = CommunityBuildingWeekLedger("2026-W31", 60_000L)
        val plan = CommunityFiscalService.planWelfare(community, "2026-W31", community.buildingState.playerWeekLedgers.mapValues { it.value.settledAmount })
        assertEquals(12_000L, plan.theoreticalTotal)
        assertEquals(6_000L, plan.spendableTreasury)
        assertEquals(listOf(3_000L, 3_000L), plan.lines.map { it.actualAmount }.sorted())
    }

    private fun community(vararg members: UUID) = Community(
        regionNumberId = 1,
        member = HashMap(members.associateWith { MemberAccount(0L, MemberRoleType.MEMBER) }),
        joinPolicy = CommunityJoinPolicy.OPEN,
        status = CommunityStatus.ACTIVE_MANOR
    )
}
