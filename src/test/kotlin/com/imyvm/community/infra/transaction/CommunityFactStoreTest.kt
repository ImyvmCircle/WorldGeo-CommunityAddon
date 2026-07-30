package com.imyvm.community.infra.transaction

import com.imyvm.community.domain.model.transaction.CombinationStepFact
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import com.imyvm.community.domain.model.transaction.CommunityAuditFact
import com.imyvm.community.domain.model.transaction.MemberLedgerFact
import com.imyvm.community.domain.model.transaction.PurposeCursorFact
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.domain.model.transaction.TreasuryLedgerFact
import com.imyvm.community.infra.account.CommunityDataWriter
import java.nio.file.Files
import java.util.Comparator
import java.util.UUID
import java.util.concurrent.CompletionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommunityFactStoreTest {
    @Test
    fun allFactKindsRecoverAndIndexesStayPaged() {
        val root = Files.createTempDirectory("community-shared-facts")
        val memberUuid = UUID.randomUUID()
        val operationId = UUID.randomUUID()
        try {
            CommunityDataWriter(32).use { writer ->
                val store = CommunityFactStore(root, writer, maxCacheEntries = 2, maxCacheBytes = 512)
                store.append(step(operationId)).join()
                store.append(step(operationId).copy(factId = UUID.randomUUID(), status = CombinationStepStatus.SUCCEEDED)).join()
                repeat(5) { store.append(treasury(it)).join() }
                store.append(member(memberUuid)).join()
                store.append(audit()).join()
                store.append(cursor()).join()

                val first = store.scanTreasury(REGION_ID, null, 2).join()
                val second = store.scanTreasury(REGION_ID, first.nextToken, 2).join()
                assertEquals(2, first.items.size)
                assertEquals(2, second.items.size)
                assertTrue(first.items.map { it.factId }.toSet().intersect(second.items.map { it.factId }.toSet()).isEmpty())
                assertEquals(1, store.scanMember(REGION_ID, memberUuid, null, 10).join().items.size)
                assertEquals(2, store.scanOperation(operationId, null, 10).join().items.size)
                assertEquals(CombinationStepStatus.SUCCEEDED, store.findLatestOperationStep(operationId, "wallet").join()?.status)
                assertEquals("period-10", store.findCursor(REGION_ID, "building", "region", "42").join()?.cursor)
                assertEquals(510L, store.treasuryBalance(REGION_ID).join())
                assertEquals(50L, store.memberContribution(REGION_ID, memberUuid).join())
                assertTrue(store.cacheEntryCount() <= 2)
                assertTrue(store.estimatedCacheBytes() <= 512)
            }

            DataOutputCheckpoint.write(root.resolve("community-fact.checkpoint"), 0L)
            CommunityDataWriter(32).use { writer ->
                val recovered = CommunityFactStore(root, writer, maxCacheEntries = 2, maxCacheBytes = 512)
                assertEquals(10L, recovered.rootSummary().join().appliedSequence)
                assertEquals(5, recovered.scanTreasury(REGION_ID, null, 10).join().items.size)
                assertEquals(510L, recovered.treasuryBalance(REGION_ID).join())
                assertEquals(50L, recovered.memberContribution(REGION_ID, memberUuid).join())
                assertEquals(CombinationStepStatus.SUCCEEDED, recovered.findLatestOperationStep(operationId, "wallet").join()?.status)
                assertEquals("period-10", recovered.findCursor(REGION_ID, "building", "region", "42").join()?.cursor)
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun treasuryReferenceIsIdempotentButRejectsChangedCanonicalFields() {
        val root = Files.createTempDirectory("community-treasury-reference")
        try {
            CommunityDataWriter(8).use { writer ->
                val store = CommunityFactStore(root, writer)
                val first = treasury(20)
                val duplicate = first.copy(
                    factId = UUID.randomUUID(),
                    descriptionKey = "changed.display.key",
                    descriptionArgs = listOf("different")
                )
                assertEquals(first.factId, store.append(first).join().factId)
                assertEquals(first.factId, store.append(duplicate).join().factId)
                assertFailsWith<CompletionException> {
                    store.append(duplicate.copy(factId = UUID.randomUUID(), amount = first.amount + 1)).join()
                }
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun aggregateOverflowIsRejectedBeforeFactIsWritten() {
        val root = Files.createTempDirectory("community-aggregate-overflow")
        try {
            CommunityDataWriter(8).use { writer ->
                val store = CommunityFactStore(root, writer)
                store.append(treasury(1).copy(amount = Long.MAX_VALUE)).join()
                assertFailsWith<CompletionException> {
                    store.append(treasury(2).copy(amount = 1L)).join()
                }
                assertEquals(Long.MAX_VALUE, store.treasuryBalance(REGION_ID).join())
                assertEquals(1L, store.rootSummary().join().appliedSequence)
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun activeTailTearIsTruncatedToLastCompleteFact() {
        val root = Files.createTempDirectory("community-shared-tail")
        try {
            CommunityDataWriter(8).use { writer -> CommunityFactStore(root, writer).append(treasury(30)).join() }
            val active = root.resolve("facts/facts-active.log")
            Files.write(active, byteArrayOf(1, 2, 3), java.nio.file.StandardOpenOption.APPEND)
            val tornSize = Files.size(active)
            CommunityDataWriter(8).use { writer ->
                val recovered = CommunityFactStore(root, writer)
                assertEquals(1L, recovered.rootSummary().join().appliedSequence)
                assertTrue(Files.size(active) < tornSize)
            }
        } finally {
            deleteTree(root)
        }
    }

    private fun step(operationId: UUID) = CombinationStepFact(
        UUID.randomUUID(), REGION_ID, 1L, operationId, "wallet", "money", "step:1",
        CombinationStepStatus.DETERMINED
    )

    private fun treasury(index: Int) = TreasuryLedgerFact(
        UUID.nameUUIDFromBytes("treasury-$index".toByteArray()), REGION_ID, index.toLong(),
        100L + index, ResourceDirection.CREDIT, "test", "treasury:$index", "deposit", "object:$index",
        "ledger.test", listOf(index.toString())
    )

    private fun member(memberUuid: UUID) = MemberLedgerFact(
        UUID.randomUUID(), REGION_ID, 2L, memberUuid, 50L, ResourceDirection.CREDIT,
        "donation", "member:1", "ledger.member", countsAsContribution = true
    )

    private fun audit() = CommunityAuditFact(
        UUID.randomUUID(), REGION_ID, 3L, null, "Server", "test", "treasury", "accepted"
    )

    private fun cursor() = PurposeCursorFact(
        UUID.randomUUID(), REGION_ID, 4L, "building", "region", "42", "period-10"
    )

    private fun deleteTree(root: java.nio.file.Path) {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }

    private object DataOutputCheckpoint {
        fun write(path: java.nio.file.Path, sequence: Long) {
            java.io.DataOutputStream(Files.newOutputStream(path)).use { output ->
                output.writeInt(0x434d4931)
                output.writeInt(1)
                output.writeLong(sequence)
            }
        }
    }

    companion object {
        private const val REGION_ID = 42
    }
}
