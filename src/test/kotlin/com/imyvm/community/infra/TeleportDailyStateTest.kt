package com.imyvm.community.infra

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.nio.file.Files
import java.util.UUID

class TeleportDailyStateTest {
    @Test
    fun firstInitializationPublishesEmptyWritableDay() {
        val root = createTempDirectory("community-daily-test")
        TeleportDailyState.initialize(root)
        assertTrue(TeleportDailyState.isStatePublishedForToday())
        assertTrue(TeleportDailyState.isStateWritableForToday())
    }

    @Test
    fun reserveAndLikeSurviveReload() {
        val root = createTempDirectory("community-daily-test")
        val player = UUID.fromString("00000000-0000-0000-0000-000000000011")
        TeleportDailyState.initialize(root)
        assertEquals(0, TeleportDailyState.reserve(player, 42))
        assertTrue(TeleportDailyState.recordLike(player, 42))

        TeleportDailyState.initialize(root)
        assertEquals(1, TeleportDailyState.getCount(player, 42))
        assertTrue(TeleportDailyState.hasLikedToday(player, 42))
        assertFalse(TeleportDailyState.recordLike(player, 42))
    }

    @Test
    fun damagedPublishedDayBlocksWrites() {
        val root = createTempDirectory("community-daily-test")
        TeleportDailyState.initialize(root)
        val day = TeleportDailyState.currentDayKey()
        Files.write(root.resolve("community-daily-teleport").resolve("teleport-$day.dat"), byteArrayOf(1, 2, 3))

        TeleportDailyState.initialize(root)
        assertTrue(TeleportDailyState.isStatePublishedForToday())
        assertFalse(TeleportDailyState.isStateWritableForToday())
        assertFailsWith<IllegalStateException> {
            TeleportDailyState.reserve(UUID.fromString("00000000-0000-0000-0000-000000000012"), 42)
        }
    }
}
