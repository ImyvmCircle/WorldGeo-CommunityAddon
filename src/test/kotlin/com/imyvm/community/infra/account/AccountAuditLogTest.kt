package com.imyvm.community.infra.account

import com.imyvm.community.domain.model.account.AccountAuditRecord
import java.nio.file.Files
import java.util.Comparator
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountAuditLogTest {
    @Test
    fun keepsBoundedRecentRecordsForTheRequestedTransaction() {
        val root = Files.createTempDirectory("community-account-audit")
        try {
            CommunityDataWriter(16).use { writer ->
                val audit = AccountAuditLog(root.resolve("audit.log"), writer)
                val target = UUID.randomUUID()
                audit.append(AccountAuditRecord(UUID.randomUUID(), 1L, "Other", "CLOSE", null, "REQUESTED")).join()
                repeat(5) { index ->
                    audit.append(AccountAuditRecord(target, index.toLong(), "Operator", "CLOSE", 100L, "R$index")).join()
                }
                assertEquals(listOf("R2", "R3", "R4"), audit.find(target, 3).join().map { it.result })
            }
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
