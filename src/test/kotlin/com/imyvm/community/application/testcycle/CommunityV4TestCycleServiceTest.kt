package com.imyvm.community.application.testcycle

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.testcycle.CommunityV4TestCycleDatabase
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CommunityV4TestCycleServiceTest {
    @Test
    fun testCycleProcessesTwoCyclesWithoutTouchingMainSettlements() {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val community = Community(
            regionNumberId = 77,
            member = hashMapOf(owner to MemberAccount(0L, MemberRoleType.OWNER)),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_REALM,
            communityIncome = arrayListOf(Turnover(100000L, 1L, TurnoverSource.SYSTEM))
        )
        CommunityDatabase.communities = mutableListOf(community)
        CommunityV4TestCycleDatabase.runs = mutableListOf()

        val run = CommunityV4TestCycleService.start(community, nowMillis = 1000L, persist = false)
        val processed = CommunityV4TestCycleService.processDue(1000L + CommunityV4TestCycleService.TEST_PERIOD_MILLIS * 2, persist = false)

        assertEquals(2, processed)
        assertEquals(2, run.completedCycles)
        assertEquals(2, run.settlements.size)
        assertFalse(run.active)
        assertEquals(0, community.taxWelfareSettlements.size)
    }
}
