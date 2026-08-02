package com.imyvm.community.application.townbuilding

import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.community.domain.model.BuildingConfirmationData
import com.imyvm.community.domain.model.BuildingEntrySnapshot
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.CommunityBuildingEntry
import com.imyvm.community.domain.model.community.CommunityBuildingState
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommunityBuildingServiceTest {
    @Test
    fun `test hour settlement maps to test week instead of real week`() {
        val hourKey = NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "test:hour:170")
        val realWeekKey = NaturalPeriodKey("production", NaturalPeriodKind.WEEK, "2026-W31")

        val resolved = CommunityBuildingService.settlementWeekKey(hourKey, realWeekKey)

        assertEquals(NaturalPeriodKey("production", NaturalPeriodKind.WEEK, "test:week:1"), resolved)
    }

    @Test
    fun `test settlement projects pending building selection into view only`() {
        val community = Community(
            regionNumberId = 42,
            member = hashMapOf(UUID.fromString("00000000-0000-0000-0000-000000000001") to MemberAccount(0L, MemberRoleType.MEMBER)),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_MANOR,
            buildingState = CommunityBuildingState()
        )
        com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[com.imyvm.community.domain.model.pendingOperationKey(42, com.imyvm.community.domain.model.PendingOperationType.BUILDING_CONFIRMATION)] =
            com.imyvm.community.domain.model.PendingOperation(
                expireAt = Long.MAX_VALUE,
                type = com.imyvm.community.domain.model.PendingOperationType.BUILDING_CONFIRMATION,
                buildingData = BuildingConfirmationData(
                    regionNumberId = 42,
                    executorUUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    action = "select",
                    baseBlockId = "minecraft:oak_planks",
                    buyUnits = 0,
                    cost = 100L,
                    entrySnapshot = BuildingEntrySnapshot(1, 100L, mutableListOf("minecraft:oak_stairs"), 7L),
                    selectionCheckpoint = "production|2026-08-02T13|-|2026-W31|-"
                )
            )

        val projected = CommunityBuildingService.effectiveBuildingViewForSettlement(community, NaturalPeriodKey("test-2", NaturalPeriodKind.HOUR, "test:hour:12"))

        assertEquals(1, projected.activeEntries().size)
        assertEquals("minecraft:oak_planks", projected.activeEntries().single().baseBlockId)
        assertEquals("production|2026-08-02T13|-|2026-W31|-", projected.activeEntries().single().selectionCheckpoint)
        assertTrue(community.buildingState.activeEntries().isEmpty())
        com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.clear()
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
