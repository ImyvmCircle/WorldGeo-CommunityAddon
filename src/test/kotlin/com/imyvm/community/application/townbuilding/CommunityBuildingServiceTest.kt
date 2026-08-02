package com.imyvm.community.application.townbuilding

import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityBuildingServiceTest {
    @Test
    fun `test hour settlement maps to test week instead of real week`() {
        val hourKey = NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "test:hour:170")
        val realWeekKey = NaturalPeriodKey("production", NaturalPeriodKind.WEEK, "2026-W31")

        val resolved = CommunityBuildingService.settlementWeekKey(hourKey, realWeekKey)

        assertEquals(NaturalPeriodKey("production", NaturalPeriodKind.WEEK, "test:week:1"), resolved)
    }

    @Test
    fun `test settlement can read from real production stats without sharing namespace`() {
        val testHourKey = NaturalPeriodKey("test-2", NaturalPeriodKind.HOUR, "test:hour:12")
        val realHourKey = NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "2026-08-02T13")

        val resolved = CommunityBuildingService.settlementStatsPeriodKey(testHourKey, realHourKey)

        assertEquals(realHourKey, resolved)
    }

    @Test
    fun `test hour next settlement text stays readable without parsing real time`() {
        val hourKey = NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "test:hour:170")

        val text = CommunityBuildingService.formatNextHourSettlementText(hourKey, ZoneId.of("Asia/Shanghai"))

        assertEquals("测试小时 171（测试周 1）", text)
    }

    @Test
    fun `real hour next settlement text keeps east asia time formatting`() {
        val hourKey = NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "2026-08-02T13")

        val text = CommunityBuildingService.formatNextHourSettlementText(hourKey, ZoneId.of("Asia/Shanghai"))

        assertEquals("2026-08-02 14:00 GMT+08:00", text)
    }
}
