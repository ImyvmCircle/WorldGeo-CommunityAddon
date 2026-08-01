package com.imyvm.community.infra.weekly

import com.imyvm.community.domain.model.weekly.CommunityWeeklyReport
import com.imyvm.community.domain.model.weekly.WeeklyReportAudience
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityWeeklyReportStoreTest {
    @Test
    fun `op global reports are read per op while player reports stay private`() {
        val root = Files.createTempDirectory("community-weekly-report-test")
        val player = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val firstOp = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val secondOp = UUID.fromString("00000000-0000-0000-0000-000000000003")

        CommunityWeeklyReportStore.initialize(root)
        CommunityWeeklyReportStore.upsert(CommunityWeeklyReport("player:week:1", player, WeeklyReportAudience.PLAYER, "week", "player", listOf("line")))
        CommunityWeeklyReportStore.upsert(CommunityWeeklyReport("op:week", null, WeeklyReportAudience.OP, "week", "op", listOf("line")))

        assertEquals(1, CommunityWeeklyReportStore.unreadCount(player, false))
        assertEquals(1, CommunityWeeklyReportStore.unreadCount(firstOp, true))
        assertEquals(1, CommunityWeeklyReportStore.unreadCount(secondOp, true))

        CommunityWeeklyReportStore.markRead(CommunityWeeklyReportStore.listFor(firstOp, true).single(), firstOp)

        assertEquals(0, CommunityWeeklyReportStore.unreadCount(firstOp, true))
        assertEquals(1, CommunityWeeklyReportStore.unreadCount(secondOp, true))
        assertEquals(1, CommunityWeeklyReportStore.unreadCount(player, false))
    }
}
