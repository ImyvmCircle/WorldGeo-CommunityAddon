package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.community.TaxWelfareSettlementStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityV4ServiceTest {
    @Test
    fun buildingRewardClaimUpdatesLedgerWithoutChangingCommunityTreasury() {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val community = testCommunity(regionNumberId = null)
        community.developmentBlockPlaceTotal = 20L

        val amount = CommunityV4Service.claimBuildingReward(community, owner, "2026-W30", 100L)
        val ledger = community.buildingRewardLedgers.getValue(owner)

        assertEquals(1200L, amount)
        assertEquals(12L, ledger.claimedBlockPlaceCount)
        assertEquals(1200L, ledger.claimedAmount)
        assertEquals("2026-W30", ledger.lastClaimedPeriodId)
        assertTrue(community.communityIncome.isEmpty())
    }

    @Test
    fun policySwitchFreezesPendingPolicyAndCost() {
        val community = testCommunity()

        assertTrue(CommunityV4Service.schedulePolicy(community, "growth", "2026-W30", "2026-W32"))
        assertFalse(CommunityV4Service.schedulePolicy(community, "tax_cut", "2026-W30", "2026-W33"))
        assertEquals("growth", community.policy.pendingPolicyKey)
        assertEquals("2026-W32", community.policy.pendingEffectivePeriodId)
        assertEquals(1, community.expenditures.size)

        assertTrue(CommunityV4Service.applyDuePolicy(community, "2026-W32"))
        assertEquals("growth", community.policy.activePolicyKey)
        assertEquals(null, community.policy.pendingPolicyKey)
    }

    @Test
    fun taxWelfareSettlementIsIdempotentForSamePeriod() {
        val community = testCommunity()
        community.communityIncome.add(Turnover(100000L, 1L, TurnoverSource.SYSTEM))

        val first = CommunityV4Service.freezeTaxWelfare(community, "2026-W30")
        val second = CommunityV4Service.freezeTaxWelfare(community, "2026-W30")

        assertEquals(first, second)
        assertEquals(1, community.taxWelfareSettlements.size)
        assertTrue(CommunityV4Service.applyTaxWelfare(community, first))
        assertEquals(TaxWelfareSettlementStatus.APPLIED, first.status)
    }

    private fun testCommunity(regionNumberId: Int? = 77): Community {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
        return Community(
            regionNumberId = regionNumberId,
            member = hashMapOf(owner to MemberAccount(0L, MemberRoleType.OWNER)),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_REALM
        )
    }
}
