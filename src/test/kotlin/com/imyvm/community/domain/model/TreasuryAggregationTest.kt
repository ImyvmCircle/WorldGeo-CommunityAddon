package com.imyvm.community.domain.model

import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.transaction.ResourceDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.UUID

class TreasuryAggregationTest {
    @Test
    fun treasuryMutationIsIdempotentAndRejectsConflicts() {
        val community = Community(
            regionNumberId = 42,
            member = hashMapOf(UUID.fromString("00000000-0000-0000-0000-000000000001") to MemberAccount(0L, MemberRoleType.OWNER)),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_REALM,
            treasuryBalance = 1_000L
        )
        val record = TreasuryReferenceRecord("ref-1", 250L, ResourceDirection.DEBIT, "test", "withdraw", "42")

        assertEquals(TreasuryMutationResult.APPLIED, community.applyTreasuryMutation(record))
        assertEquals(750L, community.getTotalAssets())
        assertEquals(TreasuryMutationResult.ALREADY_APPLIED, community.applyTreasuryMutation(record))
        assertEquals(750L, community.getTotalAssets())
        assertEquals(
            TreasuryMutationResult.CONFLICT,
            community.applyTreasuryMutation(record.copy(amount = 251L))
        )
        assertEquals(750L, community.getTotalAssets())
    }

    @Test
    fun debitCannotMakeTreasuryNegative() {
        val community = Community(
            regionNumberId = 42,
            member = hashMapOf(),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_REALM,
            treasuryBalance = 100L
        )
        val record = TreasuryReferenceRecord("ref-2", 101L, ResourceDirection.DEBIT, "test", "withdraw", "42")

        assertEquals(TreasuryMutationResult.INSUFFICIENT_FUNDS, community.applyTreasuryMutation(record))
        assertEquals(100L, community.getTotalAssets())
    }
}
