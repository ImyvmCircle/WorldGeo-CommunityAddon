package com.imyvm.community.infra.communication

import com.imyvm.community.domain.model.communication.CommunicationCategory
import com.imyvm.community.domain.model.communication.CommunicationRecord
import com.imyvm.community.domain.model.communication.CommunicationRecordType
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

class CommunicationShardStoreTest {
    @AfterTest
    fun stopStore() = CommunicationShardStore.stop()

    @Test
    fun retentionDeletesExpiredCategories() {
        val root = createTempDirectory("community-comm-test")
        CommunicationShardStore.initialize(root)
        val zone = ZoneId.of("Asia/Taipei")
        val oldChatMillis = LocalDate.now(zone).minusDays(366).atStartOfDay(zone).toInstant().toEpochMilli()
        val recentSystemMillis = LocalDate.now(zone).minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val oldOpMillis = LocalDate.now(zone).minusDays(365).atStartOfDay(zone).toInstant().toEpochMilli()

        CommunicationShardStore.append(record(oldChatMillis), CommunicationCategory.CHAT)
        CommunicationShardStore.append(record(recentSystemMillis), CommunicationCategory.SYSTEM)
        CommunicationShardStore.append(record(oldOpMillis), CommunicationCategory.OP_EXCEPTION_UNCLOSED)
        CommunicationShardStore.flush()
        CommunicationShardStore.runRetentionCleanup(System.currentTimeMillis())

        val files = Files.list(root.resolve("community-comms")).use { it.map { file -> file.fileName.toString() }.toList() }
        assertTrue(files.any { it.startsWith("comm-CHAT-") })
        assertTrue(files.any { it.startsWith("comm-SYS-") })
        assertFalse(files.any { it.startsWith("comm-OPX-") })
    }

    @Test
    fun recentChatReturnsNewestRecords() {
        val root = createTempDirectory("community-chat-test")
        CommunicationShardStore.initialize(root)
        val now = System.currentTimeMillis()
        CommunicationShardStore.append(record(now - 2, CommunicationRecordType.CHAT, "old"), CommunicationCategory.CHAT)
        CommunicationShardStore.append(record(now - 1, CommunicationRecordType.CHAT, "new"), CommunicationCategory.CHAT)
        CommunicationShardStore.flush()

        assertEquals(listOf("new", "old"), CommunicationShardStore.recentChat(42, 2).map { it.legacyText })
    }

    @Test
    fun stopDrainsQueuedWrites() {
        val root = createTempDirectory("community-stop-test")
        CommunicationShardStore.initialize(root)
        val now = System.currentTimeMillis()
        assertTrue(CommunicationShardStore.append(record(now, CommunicationRecordType.CHAT, "queued"), CommunicationCategory.CHAT))

        CommunicationShardStore.stop()

        val files = Files.list(root.resolve("community-comms")).use { it.toList() }
        assertEquals(1, files.size)
        assertTrue(Files.size(files.single()) > 0L)
    }

    private fun record(time: Long, type: CommunicationRecordType = CommunicationRecordType.SYSTEM, text: String = "legacy") = CommunicationRecord(
        regionId = 42,
        recordedAtMillis = time,
        senderUuid = null,
        senderName = null,
        type = type,
        legacyText = text
    )
}
