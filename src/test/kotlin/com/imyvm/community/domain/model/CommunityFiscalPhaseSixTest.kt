package com.imyvm.community.domain.model

import com.imyvm.community.application.fiscal.CommunityFiscalService
import com.imyvm.community.domain.model.community.CommunityBuildingWeekLedger
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
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
