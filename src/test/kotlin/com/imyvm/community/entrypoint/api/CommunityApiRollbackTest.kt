package com.imyvm.community.entrypoint.api

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.community.BuildingRewardLedger
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityPlot
import com.imyvm.community.domain.model.community.CommunityPolicyState
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.CommunityTitle
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.community.TaxWelfareSettlement
import com.imyvm.community.domain.model.community.TaxWelfareSettlementStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityApiRollbackTest {
    @Test
    fun saveMutationRestoresDeeplyMutatedV4StateOnFailure() {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val community = Community(
            regionNumberId = 77,
            member = hashMapOf(owner to MemberAccount(0L, MemberRoleType.OWNER)),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_REALM,
            buildingRewardLedgers = hashMapOf(owner to BuildingRewardLedger(1L, 100L, "2026-W29")),
            plots = mutableListOf(CommunityPlot(1L, "Old Plot", ownerUUID = owner, salePrice = 500L, cachedPrice = 10L, lastPriceRefreshMillis = 1L)),
            titles = mutableListOf(CommunityTitle(1, "title.old", owner, 1L, effectKey = "effect.old", effectAmplifier = 1)),
            policy = CommunityPolicyState("old", "pending", "2026-W31", "2026-W30"),
            taxWelfareSettlements = mutableListOf(
                TaxWelfareSettlement("settlement-1", "2026-W30", 1L, 1000L, 100L, 50L)
            )
        )

        val method = CommunityApi::class.java.declaredMethods.first { it.name.startsWith("saveMutation") }
        method.isAccessible = true
        val action = {
            community.buildingRewardLedgers.getValue(owner).claimedAmount = 999L
            community.buildingRewardLedgers.getValue(owner).lastClaimedPeriodId = "2026-W30"
            community.plots.first().name = "New Plot"
            community.plots.first().salePrice = 9999L
            community.titles.first().titleKey = "title.new"
            community.titles.first().effectAmplifier = 5
            community.policy.activePolicyKey = "new"
            community.policy.pendingPolicyKey = null
            community.taxWelfareSettlements.first().status = TaxWelfareSettlementStatus.FAILED
            community.taxWelfareSettlements.first().failureReason = "boom"
            throw IllegalStateException("boom")
        }
        method.invoke(CommunityApi, community, action)
        assertEquals(100L, community.buildingRewardLedgers.getValue(owner).claimedAmount)
        assertEquals("2026-W29", community.buildingRewardLedgers.getValue(owner).lastClaimedPeriodId)
        assertEquals("Old Plot", community.plots.first().name)
        assertEquals(500L, community.plots.first().salePrice)
        assertEquals("title.old", community.titles.first().titleKey)
        assertEquals(1, community.titles.first().effectAmplifier)
        assertEquals("old", community.policy.activePolicyKey)
        assertEquals("pending", community.policy.pendingPolicyKey)
        assertEquals(TaxWelfareSettlementStatus.PENDING, community.taxWelfareSettlements.first().status)
        assertEquals(null, community.taxWelfareSettlements.first().failureReason)
    }
}
