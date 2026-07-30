package com.imyvm.community.infra.account

import com.imyvm.community.domain.model.account.AccountAttempt
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.account.PlayerIdentity
import java.nio.file.Files
import java.util.Comparator
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountTransactionStoreTest {
    @Test
    fun factsReplayPastCheckpointAndIndexesStayPaged() {
        val root = Files.createTempDirectory("community-account-store")
        try {
            CommunityDataWriter(32).use { writer ->
                val store = AccountTransactionStore(root, writer, maxCacheEntries = 2, maxCacheBytes = 1024)
                repeat(8) { index -> store.determine(transaction(index)).join() }
                assertEquals(8L, store.currentAppliedSequence())
                assertTrue(store.scanUnresolved(null, 3).join().items.size == 3)
                assertTrue(store.cacheEntryCount() <= 2)
                assertTrue(store.estimatedCacheBytes() <= 1024)
            }

            java.io.DataOutputStream(Files.newOutputStream(root.resolve("account-index.checkpoint"))).use { output ->
                output.writeInt(0x43414931)
                output.writeInt(1)
                output.writeLong(0L)
            }
            CommunityDataWriter(32).use { writer ->
                val recovered = AccountTransactionStore(root, writer, maxCacheEntries = 2, maxCacheBytes = 1024)
                assertEquals(8L, recovered.currentAppliedSequence())
                assertEquals(8, recovered.scanUnresolved(null, 20).join().items.size)
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun replayIsIdempotentWhenDerivedStateIsAheadOfCheckpoint() {
        val root = Files.createTempDirectory("community-account-replay")
        val transaction = transaction(20)
        val attempt = AccountAttempt(UUID.randomUUID(), 10L, 100L, 101L)
        try {
            CommunityDataWriter(8).use { writer ->
                val store = AccountTransactionStore(root, writer)
                store.determine(transaction).join()
                store.recordAttempt(transaction.transactionId, attempt).join()
            }
            java.io.DataOutputStream(Files.newOutputStream(root.resolve("account-index.checkpoint"))).use { output ->
                output.writeInt(0x43414931)
                output.writeInt(1)
                output.writeLong(0L)
            }

            CommunityDataWriter(8).use { writer ->
                val recovered = AccountTransactionStore(root, writer)
                assertEquals(1, recovered.find(transaction.transactionId).join()?.attempts?.size)
                assertEquals(attempt.attemptId, recovered.find(transaction.transactionId).join()?.attempts?.single()?.attemptId)
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun sealedSegmentDamageIsIsolatedWithoutBlockingLaterState() {
        val root = Files.createTempDirectory("community-account-sealed")
        try {
            CommunityDataWriter(32).use { writer ->
                val store = AccountTransactionStore(root, writer, maxSegmentBytes = 1024)
                repeat(20) { index -> store.determine(transaction(index + 100)).join() }
            }
            val sealed = Files.list(root.resolve("facts")).use { paths ->
                paths.filter { it.fileName.toString().endsWith(".sealed") }.findFirst().orElseThrow()
            }
            java.io.RandomAccessFile(sealed.toFile(), "rw").use { file -> file.writeInt(0) }

            CommunityDataWriter(32).use { writer ->
                val recovered = AccountTransactionStore(root, writer)
                assertEquals("T0119", recovered.find(transaction(119).transactionId).join()?.transaction?.shortId)
                assertTrue(Files.exists(sealed.resolveSibling("${sealed.fileName}.corrupt")))
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun unresolvedPagesAreDeterministicAndDoNotRepeatEntries() {
        val root = Files.createTempDirectory("community-account-pages")
        try {
            CommunityDataWriter(64).use { writer ->
                val store = AccountTransactionStore(root, writer)
                repeat(40) { index -> store.determine(transaction(index + 200)).join() }
                val first = store.scanUnresolved(null, 13).join()
                val second = store.scanUnresolved(first.nextToken, 13).join()
                assertEquals(13, first.items.size)
                assertEquals(13, second.items.size)
                assertTrue(first.items.map { it.transaction.transactionId }.toSet()
                    .intersect(second.items.map { it.transaction.transactionId }.toSet()).isEmpty())
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun accountOrderUsesDurableJournalSequence() {
        val root = Files.createTempDirectory("community-account-order")
        try {
            CommunityDataWriter(8).use { writer ->
                val store = AccountTransactionStore(root, writer)
                val subject = UUID.randomUUID()
                val first = transaction(500).copy(subjectUuid = subject, createdAtMillis = 200L)
                val second = transaction(501).copy(subjectUuid = subject, createdAtMillis = 100L)
                store.determine(first).join()
                store.determine(second).join()

                val ordered = store.scanAccountOrder(subject, null, 10).join().items
                assertEquals(listOf(first.transactionId, second.transactionId),
                    ordered.map { it.transaction.transactionId })
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun activeTailTearStopsAtLastCompleteFact() {
        val root = Files.createTempDirectory("community-account-tail")
        try {
            CommunityDataWriter(8).use { writer ->
                AccountTransactionStore(root, writer).determine(transaction(1)).join()
            }
            val active = root.resolve("facts/facts-active.log")
            Files.write(active, byteArrayOf(1, 2, 3), java.nio.file.StandardOpenOption.APPEND)
            val tornSize = Files.size(active)

            CommunityDataWriter(8).use { writer ->
                val recovered = AccountTransactionStore(root, writer)
                assertEquals(1L, recovered.currentAppliedSequence())
                assertTrue(Files.size(active) < tornSize)
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun terminalFactRemovesUnresolvedIndexAndDuplicateReferenceIsIdempotent() {
        val root = Files.createTempDirectory("community-account-terminal")
        try {
            CommunityDataWriter(8).use { writer ->
                val store = AccountTransactionStore(root, writer)
                val transaction = transaction(3)
                val first = store.determine(transaction).join()
                val duplicate = store.determine(transaction.copy(transactionId = UUID.randomUUID())).join()
                assertEquals(first.transaction.transactionId, duplicate.transaction.transactionId)

                store.changeState(AccountFact.StateChanged(
                    transaction.transactionId,
                    AccountTransactionStatus.SUCCEEDED,
                    finalBalance = 900L
                )).join()
                assertTrue(store.scanUnresolved(null, 10).join().items.isEmpty())
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun conflictingReferenceIsRejected() {
        val root = Files.createTempDirectory("community-account-conflict")
        try {
            CommunityDataWriter(8).use { writer ->
                val store = AccountTransactionStore(root, writer)
                store.determine(transaction(4)).join()
                assertFailsWith<java.util.concurrent.CompletionException> {
                    store.determine(transaction(4).copy(amount = 2L, transactionId = UUID.randomUUID())).join()
                }
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun identityDirectoryRejectsUuidPlaceholderAndPersistsTrustedName() {
        val root = Files.createTempDirectory("community-identities")
        try {
            val directory = PlayerIdentityDirectory(root)
            val uuid = UUID.randomUUID()
            assertFailsWith<IllegalArgumentException> {
                directory.save(PlayerIdentity(uuid, uuid.toString(), 1L))
            }
            directory.save(PlayerIdentity(uuid, "TrustedName", 2L))
            assertEquals(PlayerIdentity(uuid, "TrustedName", 2L), directory.find(uuid))
        } finally {
            deleteTree(root)
        }
    }

    private fun transaction(index: Int): AccountTransaction {
        val id = UUID.nameUUIDFromBytes("transaction-$index".toByteArray())
        return AccountTransaction(
            transactionId = id,
            shortId = "T%04d".format(index),
            createdAtMillis = index.toLong(),
            periodKey = "manual",
            subjectUuid = UUID.nameUUIDFromBytes("player-$index".toByteArray()),
            subjectName = "Player$index",
            amount = 1L,
            direction = AccountDirection.CREDIT,
            source = "test",
            externalReference = "test:$index"
        )
    }

    private fun deleteTree(root: java.nio.file.Path) {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
}
