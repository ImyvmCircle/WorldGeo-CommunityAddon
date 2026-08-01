package com.imyvm.community.infra.communication

import com.imyvm.community.domain.model.communication.CommunicationCategory
import com.imyvm.community.domain.model.communication.CommunicationRecord
import com.imyvm.community.domain.model.communication.CommunicationRecordType
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

class CommunicationShardStoreTest {
    @Test
    fun retentionDeletesOnlyExpiredCleanableCategories() {
        val root = createTempDirectory("community-comm-test")
        CommunicationShardStore.initialize(root)
        val zone = ZoneId.of("Asia/Taipei")
        val oldChatMillis = LocalDate.now(zone).minusDays(31).atStartOfDay(zone).toInstant().toEpochMilli()
        val recentSystemMillis = LocalDate.now(zone).minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val oldOpMillis = LocalDate.now(zone).minusDays(365).atStartOfDay(zone).toInstant().toEpochMilli()

        CommunicationShardStore.append(record(oldChatMillis), CommunicationCategory.CHAT)
        CommunicationShardStore.append(record(recentSystemMillis), CommunicationCategory.SYSTEM)
        CommunicationShardStore.append(record(oldOpMillis), CommunicationCategory.OP_EXCEPTION_UNCLOSED)
        CommunicationShardStore.runRetentionCleanup(System.currentTimeMillis())

        val files = Files.list(root.resolve("community-comms")).use { it.map { file -> file.fileName.toString() }.toList() }
        assertFalse(files.any { it.startsWith("comm-CHAT-") })
        assertTrue(files.any { it.startsWith("comm-SYS-") })
        assertTrue(files.any { it.startsWith("comm-OPX-") })
    }

    private fun record(time: Long) = CommunicationRecord(
        regionId = 42,
        recordedAtMillis = time,
        senderUuid = null,
        senderName = null,
        type = CommunicationRecordType.SYSTEM,
        legacyText = "legacy"
    )
}
