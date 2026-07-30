package com.imyvm.community.infra.account

import com.imyvm.community.domain.model.account.AccountAttempt
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionState
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.zip.CRC32

class AccountTransactionStore(
    private val root: Path,
    private val writer: CommunityDataWriter,
    private val maxSegmentBytes: Long = 4L * 1024 * 1024,
    private val maxCacheEntries: Int = 256,
    private val maxCacheBytes: Long = 4L * 1024 * 1024
) {
    private val factsDirectory = root.resolve("facts")
    private val statesDirectory = root.resolve("states")
    private val unresolvedDirectory = root.resolve("unresolved")
    private val accountOrderDirectory = root.resolve("account-order")
    private val externalDirectory = root.resolve("external")
    private val checkpointFile = root.resolve("account-index.checkpoint")
    private val activeSegment = factsDirectory.resolve("facts-active.log")
    private val cache = object : LinkedHashMap<UUID, CachedState>(16, 0.75f, true) {}
    private var cacheBytes = 0L
    private var appliedSequence = 0L
    private var nextSequence = 1L

    init {
        require(maxSegmentBytes >= 1024)
        require(maxCacheEntries > 0)
        require(maxCacheBytes > 0)
        Files.createDirectories(factsDirectory)
        Files.createDirectories(statesDirectory)
        Files.createDirectories(unresolvedDirectory)
        Files.createDirectories(accountOrderDirectory)
        Files.createDirectories(externalDirectory)
        recover()
    }

    fun determine(transaction: AccountTransaction): CompletableFuture<AccountTransactionState> = writer.submit {
        require(transaction.amount > 0) { "Account amount must be positive" }
        require(transaction.externalReference.isNotBlank()) { "External reference must not be blank" }
        val existing = findByExternalReference(transaction.externalReference)
        if (existing != null) {
            require(existing.transaction.subjectUuid == transaction.subjectUuid &&
                existing.transaction.direction == transaction.direction &&
                existing.transaction.amount == transaction.amount) { "Conflicting external reference" }
            return@submit existing
        }
        require(loadState(transaction.transactionId) == null) { "Duplicate transaction ID" }
        appendAndApply(AccountFact.Determined(transaction))
    }

    fun recordAttempt(transactionId: UUID, attempt: AccountAttempt): CompletableFuture<AccountTransactionState> =
        writer.submit { appendAndApply(AccountFact.Attempted(transactionId, attempt)) }

    fun recordCallStarted(
        transactionId: UUID,
        attemptId: UUID,
        startedAtMillis: Long
    ): CompletableFuture<AccountTransactionState> = writer.submit {
        appendAndApply(AccountFact.CallStarted(transactionId, attemptId, startedAtMillis))
    }

    fun changeState(fact: AccountFact.StateChanged): CompletableFuture<AccountTransactionState> =
        writer.submit { appendAndApply(fact) }

    @Synchronized
    fun find(transactionId: UUID): AccountTransactionState? = loadState(transactionId)

    fun findByShortId(shortId: String): AccountTransactionState? {
        Files.newDirectoryStream(statesDirectory, "*.state").use { stream ->
            for (path in stream) {
                val state = readState(path)
                if (state.transaction.shortId == shortId) return state
            }
        }
        return null
    }

    fun scanUnresolved(after: String?, limit: Int): List<AccountTransactionState> {
        require(limit in 1..10_000)
        return scanIndex(unresolvedDirectory, after, limit).mapNotNull { entry ->
            loadState(UUID.fromString(entry.fileName.toString().removeSuffix(".idx")))
        }
    }

    fun scanAccountOrder(subjectUuid: UUID, after: String?, limit: Int): List<AccountTransactionState> {
        require(limit in 1..10_000)
        val directory = accountOrderDirectory.resolve(subjectUuid.toString())
        if (!Files.exists(directory)) return emptyList()
        return scanIndex(directory, after, limit).mapNotNull { entry ->
            val id = Files.readString(entry).trim()
            loadState(UUID.fromString(id))
        }
    }

    fun currentAppliedSequence(): Long = appliedSequence

    fun cacheEntryCount(): Int = synchronized(cache) { cache.size }

    fun estimatedCacheBytes(): Long = synchronized(cache) { cacheBytes }

    private fun appendAndApply(fact: AccountFact): AccountTransactionState {
        val sequence = nextSequence++
        appendFact(SequencedAccountFact(sequence, fact))
        val state = applyFact(fact)
        writeDerivedState(state, sequence)
        appliedSequence = sequence
        writeCheckpoint(sequence)
        rotateIfNeeded(sequence)
        return state
    }

    private fun appendFact(entry: SequencedAccountFact) {
        val payload = AccountCodec.encodeFact(entry.fact)
        require(payload.size <= MAX_FACT_BYTES)
        val crc = CRC32().apply { update(payload) }.value.toInt()
        FileChannel.open(activeSegment, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND).use { channel ->
            val header = ByteBuffer.allocate(RECORD_HEADER_BYTES)
                .putInt(RECORD_MAGIC)
                .putLong(entry.sequence)
                .putInt(payload.size)
                .flip() as ByteBuffer
            while (header.hasRemaining()) channel.write(header)
            val body = ByteBuffer.wrap(payload)
            while (body.hasRemaining()) channel.write(body)
            val checksum = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(crc).flip() as ByteBuffer
            while (checksum.hasRemaining()) channel.write(checksum)
            channel.force(true)
        }
    }

    private fun applyFact(fact: AccountFact): AccountTransactionState {
        val current = loadState(fact.transactionId)
        if (fact is AccountFact.Determined) {
            if (current != null) return current
            return AccountTransactionState(fact.transaction)
        }
        val existing = current ?: error("Unknown account transaction: ${fact.transactionId}")
        return when (fact) {
            is AccountFact.Attempted -> {
                if (existing.attempts.any { it.attemptId == fact.attempt.attemptId }) existing
                else existing.copy(attempts = existing.attempts + fact.attempt)
            }
            is AccountFact.CallStarted -> {
                val attempts = existing.attempts.map { attempt ->
                    if (attempt.attemptId == fact.attemptId) attempt.copy(callStartedAtMillis = fact.startedAtMillis) else attempt
                }
                require(attempts.any { it.attemptId == fact.attemptId && it.callStartedAtMillis == fact.startedAtMillis })
                existing.copy(attempts = attempts)
            }
            is AccountFact.StateChanged -> existing.copy(
                status = fact.status,
                failureStage = fact.failureStage,
                failureReason = fact.failureReason,
                retryCount = fact.retryCount,
                nextRetryAtMillis = fact.nextRetryAtMillis,
                finalBalance = fact.finalBalance
            )
            is AccountFact.Determined -> error("unreachable")
        }
    }

    private fun writeDerivedState(state: AccountTransactionState, sequence: Long) {
        val payload = AccountCodec.encodeState(state)
        writeAtomic(statePath(state.transaction.transactionId), payload)
        val unresolved = unresolvedDirectory.resolve("${state.transaction.transactionId}.idx")
        if (state.status.isTerminal()) Files.deleteIfExists(unresolved) else writeAtomic(unresolved, byteArrayOf())
        if (state.status == com.imyvm.community.domain.model.account.AccountTransactionStatus.DETERMINED) {
            val accountDirectory = accountOrderDirectory.resolve(state.transaction.subjectUuid.toString())
            Files.createDirectories(accountDirectory)
            val orderName = "%020d-%s.idx".format(state.transaction.createdAtMillis, state.transaction.transactionId)
            writeAtomic(accountDirectory.resolve(orderName), state.transaction.transactionId.toString().toByteArray())
            writeAtomic(externalPath(state.transaction.externalReference), state.transaction.transactionId.toString().toByteArray())
        }
        putCache(state, payload.size.toLong())
    }

    private fun recover() {
        appliedSequence = readCheckpoint()
        var highestSequence = 0L
        val segments = Files.list(factsDirectory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".sealed") }
                .sorted()
                .toList()
        } + listOf(activeSegment).filter(Files::exists)
        for (segment in segments) {
            try {
                highestSequence = maxOf(highestSequence, readSegment(segment) { entry ->
                    if (entry.sequence > appliedSequence) {
                        val state = applyFact(entry.fact)
                        writeDerivedState(state, entry.sequence)
                        appliedSequence = entry.sequence
                        writeCheckpoint(appliedSequence)
                    }
                })
            } catch (error: CorruptSealedSegmentException) {
                Files.move(segment, segment.resolveSibling("${segment.fileName}.corrupt"), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        nextSequence = maxOf(highestSequence, appliedSequence) + 1
    }

    private fun readSegment(segment: Path, consumer: (SequencedAccountFact) -> Unit): Long {
        var highest = 0L
        var lastGoodOffset = 0L
        RandomAccessFile(segment.toFile(), "rw").use { file ->
            while (file.filePointer < file.length()) {
                val recordOffset = file.filePointer
                try {
                    require(file.readInt() == RECORD_MAGIC) { "Invalid account record magic" }
                    val sequence = file.readLong()
                    require(sequence > highest) { "Non-increasing account sequence" }
                    val length = file.readInt()
                    require(length in 0..MAX_FACT_BYTES) { "Invalid account fact length" }
                    val payload = ByteArray(length)
                    file.readFully(payload)
                    val expectedCrc = file.readInt()
                    val actualCrc = CRC32().apply { update(payload) }.value.toInt()
                    require(expectedCrc == actualCrc) { "Account fact checksum mismatch" }
                    consumer(SequencedAccountFact(sequence, AccountCodec.decodeFact(payload)))
                    highest = sequence
                    lastGoodOffset = file.filePointer
                } catch (error: EOFException) {
                    if (segment == activeSegment) {
                        file.setLength(lastGoodOffset)
                        break
                    }
                    throw CorruptSealedSegmentException(error)
                } catch (error: IllegalArgumentException) {
                    if (segment == activeSegment && recordOffset >= lastGoodOffset) {
                        file.setLength(lastGoodOffset)
                        break
                    }
                    throw CorruptSealedSegmentException(error)
                }
            }
        }
        return highest
    }

    private fun rotateIfNeeded(sequence: Long) {
        if (!Files.exists(activeSegment) || Files.size(activeSegment) < maxSegmentBytes) return
        val sealed = factsDirectory.resolve("facts-%020d.sealed".format(sequence))
        Files.move(activeSegment, sealed, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun readCheckpoint(): Long {
        if (!Files.exists(checkpointFile)) return 0L
        return DataInputStream(Files.newInputStream(checkpointFile)).use { input ->
            require(input.readInt() == CHECKPOINT_MAGIC)
            require(input.readInt() == 1)
            input.readLong().also { require(it >= 0) }
        }
    }

    private fun writeCheckpoint(sequence: Long) {
        val payload = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + Long.SIZE_BYTES)
            .putInt(CHECKPOINT_MAGIC)
            .putInt(1)
            .putLong(sequence)
            .array()
        writeAtomic(checkpointFile, payload)
    }

    private fun loadState(transactionId: UUID): AccountTransactionState? {
        synchronized(cache) { cache[transactionId]?.let { return it.state } }
        val path = statePath(transactionId)
        if (!Files.exists(path)) return null
        val payload = Files.readAllBytes(path)
        val state = AccountCodec.decodeState(payload)
        require(state.transaction.transactionId == transactionId)
        putCache(state, payload.size.toLong())
        return state
    }

    private fun readState(path: Path): AccountTransactionState {
        val payload = Files.readAllBytes(path)
        return AccountCodec.decodeState(payload)
    }

    private fun findByExternalReference(externalReference: String): AccountTransactionState? {
        val path = externalPath(externalReference)
        if (!Files.exists(path)) return null
        val id = UUID.fromString(Files.readString(path).trim())
        val state = loadState(id) ?: error("Missing account state for external reference")
        require(state.transaction.externalReference == externalReference) { "External reference hash collision" }
        return state
    }

    private fun scanIndex(directory: Path, after: String?, limit: Int): List<Path> {
        if (!Files.exists(directory)) return emptyList()
        val result = PriorityQueue<Path>(limit, compareByDescending { it.fileName.toString() })
        Files.newDirectoryStream(directory, "*.idx").use { entries ->
            for (entry in entries) {
                if (after != null && entry.fileName.toString() <= after) continue
                result.add(entry)
                if (result.size > limit) result.poll()
            }
        }
        return result.sortedBy { it.fileName.toString() }
    }

    private fun putCache(state: AccountTransactionState, bytes: Long) {
        synchronized(cache) {
            cache.remove(state.transaction.transactionId)?.let { cacheBytes -= it.bytes }
            if (bytes <= maxCacheBytes) {
                cache[state.transaction.transactionId] = CachedState(state, bytes)
                cacheBytes += bytes
            }
            while (cache.size > maxCacheEntries || cacheBytes > maxCacheBytes) {
                val eldest = cache.entries.iterator().next()
                cache.remove(eldest.key)
                cacheBytes -= eldest.value.bytes
            }
        }
    }

    private fun writeAtomic(target: Path, payload: ByteArray) {
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".derived-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(payload)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            moveAtomic(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun moveAtomic(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.io.IOException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun statePath(transactionId: UUID): Path = statesDirectory.resolve("$transactionId.state")

    private fun externalPath(externalReference: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(externalReference.toByteArray())
        return externalDirectory.resolve(digest.joinToString("") { "%02x".format(it) } + ".idx")
    }

    private data class CachedState(val state: AccountTransactionState, val bytes: Long)

    private class CorruptSealedSegmentException(cause: Throwable) : RuntimeException(cause)

    companion object {
        private const val RECORD_MAGIC = 0x43414631
        private const val CHECKPOINT_MAGIC = 0x43414931
        private const val RECORD_HEADER_BYTES = Int.SIZE_BYTES + Long.SIZE_BYTES + Int.SIZE_BYTES
        private const val MAX_FACT_BYTES = 1024 * 1024
    }
}
