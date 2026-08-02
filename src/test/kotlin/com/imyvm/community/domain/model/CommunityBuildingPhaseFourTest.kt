package com.imyvm.community.domain.model

import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.application.townbuilding.CommunityBuildingBlockStats
import com.imyvm.community.application.townbuilding.CommunityBuildingPlayerReward
import com.imyvm.community.application.townbuilding.CommunityBuildingSettlement
import com.imyvm.community.domain.model.community.CommunityBuildingCatalogEntry
import com.imyvm.community.domain.model.community.CommunityBuildingEntry
import com.imyvm.community.domain.model.community.CommunityBuildingPlayerNetLedger
import com.imyvm.community.domain.model.community.CommunityBuildingState
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.UUID

class CommunityBuildingPhaseFourTest {
    @AfterTest
    fun tearDown() {
        CommunityBuildingService.selectablePoolState.clear()
    }

    @Test
    fun `active building entries consume capacity and inactive entries are ignored`() {
        val state = CommunityBuildingState(
            capacityUnits = 4,
            stylePackage = mutableListOf(
                CommunityBuildingEntry("minecraft:oak_planks", 3, 100L, active = true),
                CommunityBuildingEntry("minecraft:stone", 3, 100L, active = false)
            )
        )

        assertEquals(3, state.usedCapacityUnits())
        assertEquals(1, state.remainingCapacityUnits())
        assertEquals(1, state.activeEntries().size)
        assertEquals(null, state.findEntry("minecraft:stone"))
    }

    @Test
    fun `active building entries cannot map the same block twice`() {
        val state = CommunityBuildingState(
            stylePackage = mutableListOf(
                CommunityBuildingEntry("minecraft:oak_planks", 1, 100L, mutableListOf("minecraft:oak_stairs")),
                CommunityBuildingEntry("minecraft:oak_log", 1, 100L, mutableListOf("minecraft:oak_stairs"))
            )
        )

        assertTrue(state.validateUniqueBlockMapping().isFailure)
    }

    @Test
    fun `inactive building entries do not block new mappings`() {
        val state = CommunityBuildingState(
            stylePackage = mutableListOf(
                CommunityBuildingEntry("minecraft:oak_planks", 1, 100L, mutableListOf("minecraft:oak_stairs"), active = false),
                CommunityBuildingEntry("minecraft:oak_log", 1, 100L, mutableListOf("minecraft:oak_stairs"), active = true)
            )
        )

        assertTrue(state.validateUniqueBlockMapping().isSuccess)
    }

    @Test
    fun `community entries freeze template values and version`() {
        val template = CommunityBuildingCatalogEntry(
            "minecraft:oak_planks",
            2,
            150L,
            mutableListOf("minecraft:oak_stairs"),
            7L
        )
        val frozen = CommunityBuildingEntry(
            template.baseBlockId,
            template.unitCost,
            template.rewardPerBlock,
            template.linkedBlockIds.toMutableList(),
            template.templateVersion,
            "2026-08-01T00",
            true
        )

        template.unitCost = 4
        template.rewardPerBlock = 900L
        template.linkedBlockIds.add("minecraft:oak_slab")
        template.templateVersion++

        assertEquals(2, frozen.unitCost)
        assertEquals(150L, frozen.rewardPerBlock)
        assertEquals(listOf("minecraft:oak_stairs"), frozen.linkedBlockIds)
        assertEquals(7L, frozen.templateVersion)
    }

    @Test
    fun `template pool rejects duplicate tracked blocks`() {
        CommunityBuildingService.selectablePoolState.addAll(
            listOf(
                CommunityBuildingCatalogEntry("minecraft:oak_planks", 1, 100L, mutableListOf("minecraft:oak_stairs")),
                CommunityBuildingCatalogEntry("minecraft:oak_log", 1, 100L, mutableListOf("minecraft:oak_stairs"))
            )
        )

        assertFalse(CommunityBuildingService.validateTemplatePool().isSuccess)
    }

    @Test
    fun `incomplete selection checkpoint skips only matching active period`() {
        val marker = "production|2026-08-02T01|-|2026-W31|-"

        assertFalse(CommunityBuildingService.entryCountsForPeriod(marker, NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "2026-08-02T01")))
        assertFalse(CommunityBuildingService.entryCountsForPeriod(marker, NaturalPeriodKey("production", NaturalPeriodKind.WEEK, "2026-W31")))
        assertTrue(CommunityBuildingService.entryCountsForPeriod(marker, NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "2026-08-02T02")))
        assertTrue(CommunityBuildingService.entryCountsForPeriod(marker, NaturalPeriodKey("production", NaturalPeriodKind.WEEK, "2026-W32")))
    }
}

class CommunityBuildingSettlementTest {
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val third = UUID.fromString("00000000-0000-0000-0000-000000000003")

    @Test
    fun `settlement clamps negative block net without reducing other blocks`() {
        val entries = listOf(
            CommunityBuildingEntry("minecraft:oak_planks", 1, 100L),
            CommunityBuildingEntry("minecraft:stone", 1, 200L)
        )
        val plan = CommunityBuildingSettlement.plan(
            entries,
            listOf(
                CommunityBuildingBlockStats("minecraft:oak_planks", 2, 7, mapOf(first to -5L)),
                CommunityBuildingBlockStats("minecraft:stone", 4, 1, mapOf(first to 3L))
            ),
            120000L,
            emptyMap(),
            200000L,
            0L
        )

        assertEquals(listOf(CommunityBuildingPlayerReward(first, "minecraft:stone", 3L, 600L)), plan.playerRewards)
        assertEquals(120L, plan.communityIncome)
    }

    @Test
    fun `settlement distributes player units by contribution with uuid remainder order`() {
        val entries = listOf(CommunityBuildingEntry("minecraft:oak_planks", 1, 100L))
        val plan = CommunityBuildingSettlement.plan(
            entries,
            listOf(CommunityBuildingBlockStats("minecraft:oak_planks", 5, 0, mapOf(second to 2L, first to 2L, third to 2L))),
            120000L,
            emptyMap(),
            200000L,
            0L
        )

        assertEquals(
            listOf(
                CommunityBuildingPlayerReward(first, "minecraft:oak_planks", 2L, 200L),
                CommunityBuildingPlayerReward(second, "minecraft:oak_planks", 2L, 200L),
                CommunityBuildingPlayerReward(third, "minecraft:oak_planks", 1L, 100L)
            ),
            plan.playerRewards
        )
    }

    @Test
    fun `settlement applies global player weekly cap in publish order`() {
        val entries = listOf(
            CommunityBuildingEntry("minecraft:oak_planks", 1, 100L),
            CommunityBuildingEntry("minecraft:stone", 1, 100L)
        )
        val plan = CommunityBuildingSettlement.plan(
            entries,
            listOf(
                CommunityBuildingBlockStats("minecraft:oak_planks", 10, 0, mapOf(first to 10L)),
                CommunityBuildingBlockStats("minecraft:stone", 10, 0, mapOf(first to 10L))
            ),
            1200L,
            mapOf(first to 500L),
            200000L,
            0L
        )

        assertEquals(
            listOf(
                CommunityBuildingPlayerReward(first, "minecraft:oak_planks", 10L, 700L)
            ),
            plan.playerRewards
        )
    }

    @Test
    fun `settlement consumes title extra cap before global player weekly cap`() {
        val entries = listOf(
            CommunityBuildingEntry("minecraft:oak_planks", 1, 100L),
            CommunityBuildingEntry("minecraft:stone", 1, 100L)
        )
        val plan = CommunityBuildingSettlement.plan(
            entries,
            listOf(
                CommunityBuildingBlockStats("minecraft:oak_planks", 10, 0, mapOf(first to 10L)),
                CommunityBuildingBlockStats("minecraft:stone", 10, 0, mapOf(first to 10L))
            ),
            1200L,
            mapOf(first to 500L),
            200000L,
            0L,
            playerExtraWeeklyCaps = mapOf(first to 600L),
            playerExtraWeekUsage = mapOf(first to 200L)
        )

        assertEquals(
            listOf(
                CommunityBuildingPlayerReward(first, "minecraft:oak_planks", 10L, 1000L, 600L, 400L),
                CommunityBuildingPlayerReward(first, "minecraft:stone", 10L, 100L, 100L, 0L)
            ),
            plan.playerRewards
        )
    }

    @Test
    fun `community income floors all block numerator once and applies weekly cap`() {
        val entries = listOf(
            CommunityBuildingEntry("minecraft:oak_planks", 1, 101L),
            CommunityBuildingEntry("minecraft:stone", 1, 102L)
        )
        val plan = CommunityBuildingSettlement.plan(
            entries,
            listOf(
                CommunityBuildingBlockStats("minecraft:oak_planks", 3, 0, mapOf(first to 3L)),
                CommunityBuildingBlockStats("minecraft:stone", 4, 0, mapOf(first to 4L))
            ),
            120000L,
            emptyMap(),
            180L,
            50L
        )

        assertEquals(142L, plan.theoreticalCommunityIncome)
        assertEquals(130L, plan.communityIncome)
    }

    @Test
    fun `community weekly cap uses actual settled community income`() {
        val entries = listOf(CommunityBuildingEntry("minecraft:oak_planks", 1, 100L))
        val plan = CommunityBuildingSettlement.plan(
            entries,
            listOf(CommunityBuildingBlockStats("minecraft:oak_planks", 10, 0, mapOf(first to 10L))),
            120000L,
            emptyMap(),
            250L,
            100L
        )

        assertEquals(200L, plan.theoreticalCommunityIncome)
        assertEquals(150L, plan.communityIncome)
    }

    @Test
    fun `player reward settles only cumulative net growth beyond weekly peak`() {
        val entries = listOf(CommunityBuildingEntry("minecraft:oak_planks", 1, 100L))
        val netLedgers = HashMap<UUID, MutableList<CommunityBuildingPlayerNetLedger>>()
        val weekId = "production:2026-W31"

        fun settle(placed: Long, broken: Long): Long {
            val plan = CommunityBuildingSettlement.plan(
                entries,
                listOf(CommunityBuildingBlockStats("minecraft:oak_planks", placed, broken, mapOf(first to (placed - broken)))),
                120000L,
                emptyMap(),
                200000L,
                0L,
                weekPeriodId = weekId,
                playerNetLedgers = netLedgers
            )
            return plan.playerRewards.sumOf { it.units }
        }

        assertEquals(10L, settle(10, 0))
        assertEquals(0L, settle(0, 5))
        assertEquals(0L, settle(5, 0))
        assertEquals(3L, settle(3, 0))
    }
}
