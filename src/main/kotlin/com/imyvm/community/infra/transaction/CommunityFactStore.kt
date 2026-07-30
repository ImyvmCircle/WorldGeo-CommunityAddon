package com.imyvm.community.infra.transaction

import com.imyvm.community.domain.model.transaction.CombinationStepFact
import com.imyvm.community.domain.model.transaction.CommunityAuditFact
import com.imyvm.community.domain.model.transaction.CommunityFact
import com.imyvm.community.domain.model.transaction.CommunityFactPage
import com.imyvm.community.domain.model.transaction.CommunityFactRootSummary
import com.imyvm.community.domain.model.transaction.MemberLedgerFact
import com.imyvm.community.domain.model.transaction.PurposeCursorFact
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.domain.model.transaction.TreasuryLedgerFact
import com.imyvm.community.infra.account.CommunityDataWriter
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

class CommunityFactStore(
    private val root: Path,
    private val writer: CommunityDataWriter,
    private val maxSegmentBytes: Long = 4L * 1024 * 1024,
    private val maxCacheEntries: Int = 256,
    private val maxCacheBytes: Long = 4L * 1024 * 1024
) {
    private val factsDirectory = root.resolve("facts")
    private val recordsDirectory = root.resolve("records")
    private val treasuryDirectory = root.resolve("treasury")
    private val memberDirectory = root.resolve("member")
    private val auditDirectory = root.resolve("audit")
    private val operationDirectory = root.resolve("operation")
    private val operationLatestDirectory = root.resolve("operation-latest")
    private val cursorDirectory = root.resolve("cursor")
    private val externalDirectory = root.resolve("external")
    private val treasuryAggregateDirectory = root.resolve("aggregate/treasury")
    private val memberAggregateDirectory = root.resolve("aggregate/member")
    private val checkpointFile = root.resolve("community-fact.checkpoint")
    private val activeSegment = factsDirectory.resolve("facts-active.log")
    private val cache = object : LinkedHashMap<UUID, CachedFact>(16, 0.75f, true) {}
    private var cacheBytes = 0L
    private var appliedSequence = 0L
    private var nextSequence = 1L

    init {
        require(maxSegmentBytes >= 1024)
        require(maxCacheEntries > 0)
        require(maxCacheBytes > 0)
        listOf(
            factsDirectory, recordsDirectory, treasuryDirectory, memberDirectory, auditDirectory,
            operationDirectory, operationLatestDirectory, cursorDirectory, externalDirectory, treasuryAggregateDirectory, memberAggregateDirectory
        ).forEach(Files::createDirectories)
        recover()
    }

    fun append(fact: CommunityFact): CompletableFuture<CommunityFact> = writer.submit {
        validate(fact)
        loadFact(fact.factId)?.let { existing ->
            require(existing == fact) { "Conflicting community fact ID" }
            return@submit existing
        }
        findByExternalReference(fact)?.let { existing ->
            require(sameExternalOperation(existing, fact)) { "Conflicting community external reference" }
            return@submit existing
        }
        preflightAggregate(fact)
        val sequence = nextSequence++
        appendRecord(sequence, fact)
        applyFact(sequence, fact)
        appliedSequence = sequence
        writeCheckpoint(sequence)
        rotateIfNeeded(sequence)
        fact
    }

    fun find(factId: UUID): CompletableFuture<CommunityFact?> = writer.submit { loadFact(factId) }

    fun scanTreasury(regionId: Int, after: String?, limit: Int): CompletableFuture<CommunityFactPage> =
        scan(treasuryDirectory.resolve(regionId.toString()), after, limit)

    fun scanMember(regionId: Int, memberUuid: UUID, after: String?, limit: Int): CompletableFuture<CommunityFactPage> =
        scan(memberDirectory.resolve(regionId.toString()).resolve(memberUuid.toString()), after, limit)

    fun scanAudit(regionId: Int, after: String?, limit: Int): CompletableFuture<CommunityFactPage> =
        scan(auditDirectory.resolve(regionId.toString()), after, limit)

    fun scanOperation(operationId: UUID, after: String?, limit: Int): CompletableFuture<CommunityFactPage> =
        scan(operationDirectory.resolve(operationId.toString()), after, limit)

    fun findLatestOperationStep(operationId: UUID, stepKey: String): CompletableFuture<CombinationStepFact?> =
        writer.submit {
            require(stepKey.isNotBlank()) { "Combination step key must not be blank" }
            val path = operationLatestPath(operationId, stepKey)
            if (!Files.exists(path)) {
                null
            } else {
                val factId = UUID.fromString(Files.readString(path).trim())
                loadFact(factId) as? CombinationStepFact
                    ?: error("Missing combination latest-step target")
            }
        }

    fun findCursor(
        regionId: Int,
        purpose: String,
        consumerUnitType: String,
        consumerUnit: String
    ): CompletableFuture<PurposeCursorFact?> = writer.submit {
        val path = cursorPath(regionId, purpose, consumerUnitType, consumerUnit)
        if (!Files.exists(path)) return@submit null
        val factId = UUID.fromString(Files.readString(path).trim())
        loadFact(factId) as? PurposeCursorFact
    }

    fun rootSummary(): CompletableFuture<CommunityFactRootSummary> = writer.submit {
        val indexes = Files.list(factsDirectory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".sealed") }.sorted().toList()
        }
        return@submit CommunityFactRootSummary(
            appliedSequence,
            activeSegment.fileName.toString(),
            indexes.firstOrNull()?.fileName?.toString(),
            indexes.lastOrNull()?.fileName?.toString()
        )
    }

    fun cacheEntryCount(): Int = synchronized(cache) { cache.size }

    fun estimatedCacheBytes(): Long = synchronized(cache) { cacheBytes }

    fun treasuryBalance(regionId: Int): CompletableFuture<Long> = writer.submit {
        readAggregate(treasuryAggregateDirectory.resolve(regionId.toString() + ".state")).amount
    }

    fun memberContribution(regionId: Int, memberUuid: UUID): CompletableFuture<Long> = writer.submit {
        readAggregate(memberAggregatePath(regionId, memberUuid)).amount
    }

    private fun scan(directory: Path, after: String?, limit: Int): CompletableFuture<CommunityFactPage> = writer.submit {
        require(limit in 1..MAX_PAGE_SIZE)
        val entries = scanIndex(directory, after, limit)
        CommunityFactPage(
            entries.map { entry ->
                val factId = UUID.fromString(Files.readString(entry).trim())
                loadFact(factId) ?: error("Missing community fact index target")
            },
            entries.lastOrNull()?.fileName?.toString()
        )
    }

    private fun appendRecord(sequence: Long, fact: CommunityFact) {
        val payload = CommunityFactCodec.encode(fact)
        require(payload.size <= MAX_FACT_BYTES) { "Community fact is too large" }
        val checksum = CRC32().apply { update(payload) }.value.toInt()
        FileChannel.open(
            activeSegment, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
        ).use { channel ->
            writeFully(channel, ByteBuffer.allocate(RECORD_HEADER_BYTES)
                .putInt(RECORD_MAGIC).putLong(sequence).putInt(payload.size).flip() as ByteBuffer)
            writeFully(channel, ByteBuffer.wrap(payload))
            writeFully(channel, ByteBuffer.allocate(Int.SIZE_BYTES).putInt(checksum).flip() as ByteBuffer)
            channel.force(true)
        }
    }

    private fun applyFact(sequence: Long, fact: CommunityFact) {
        val payload = CommunityFactCodec.encode(fact)
        writeAtomic(recordPath(fact.factId), payload)
        val indexName = "%020d-%s.idx".format(sequence, fact.factId)
        when (fact) {
            is CombinationStepFact -> {
                writeIndex(operationDirectory.resolve(fact.operationId.toString()), indexName, fact.factId)
                writeAtomic(operationLatestPath(fact.operationId, fact.stepKey), fact.factId.toString().toByteArray())
            }
            is TreasuryLedgerFact -> {
                writeIndex(treasuryDirectory.resolve(fact.regionId.toString()), indexName, fact.factId)
                writeAtomic(externalPath("treasury", fact.externalReference), fact.factId.toString().toByteArray())
                applyAggregate(sequence, treasuryAggregateDirectory.resolve(fact.regionId.toString() + ".state"), fact.direction, fact.amount)
            }
            is MemberLedgerFact -> {
                writeIndex(memberDirectory.resolve(fact.regionId.toString()).resolve(fact.memberUuid.toString()), indexName, fact.factId)
                writeAtomic(externalPath("member", fact.externalReference), fact.factId.toString().toByteArray())
                if (fact.countsAsContribution) {
                    applyAggregate(sequence, memberAggregatePath(fact.regionId, fact.memberUuid), fact.direction, fact.amount)
                }
            }
            is CommunityAuditFact -> writeIndex(auditDirectory.resolve(fact.regionId.toString()), indexName, fact.factId)
            is PurposeCursorFact -> writeAtomic(
                cursorPath(fact.regionId, fact.purpose, fact.consumerUnitType, fact.consumerUnit),
                fact.factId.toString().toByteArray()
            )
        }
        putCache(fact, payload.size.toLong())
    }

    private fun writeIndex(directory: Path, name: String, factId: UUID) {
        Files.createDirectories(directory)
        writeAtomic(directory.resolve(name), factId.toString().toByteArray())
    }

    private fun recover() {
        appliedSequence = readCheckpoint()
        var highestSequence = 0L
        val segments = Files.list(factsDirectory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".sealed") }.sorted().toList()
        } + listOf(activeSegment).filter(Files::exists)
        for (segment in segments) {
            try {
                highestSequence = maxOf(highestSequence, readSegment(segment) { sequence, fact ->
                    if (sequence > appliedSequence) {
                        applyFact(sequence, fact)
                        appliedSequence = sequence
                        writeCheckpoint(sequence)
                    }
                })
            } catch (_: CorruptSealedSegmentException) {
                Files.move(segment, segment.resolveSibling("${segment.fileName}.corrupt"), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        nextSequence = maxOf(highestSequence, appliedSequence) + 1
    }

    private fun readSegment(segment: Path, consumer: (Long, CommunityFact) -> Unit): Long {
        var highest = 0L
        var lastGoodOffset = 0L
        RandomAccessFile(segment.toFile(), "rw").use { file ->
            while (file.filePointer < file.length()) {
                val recordOffset = file.filePointer
                try {
                    require(file.readInt() == RECORD_MAGIC) { "Invalid community fact magic" }
                    val sequence = file.readLong()
                    require(sequence > highest) { "Non-increasing community fact sequence" }
                    val length = file.readInt()
                    require(length in 0..MAX_FACT_BYTES) { "Invalid community fact length" }
                    val payload = ByteArray(length)
                    file.readFully(payload)
                    val expectedChecksum = file.readInt()
                    val actualChecksum = CRC32().apply { update(payload) }.value.toInt()
                    require(expectedChecksum == actualChecksum) { "Community fact checksum mismatch" }
                    consumer(sequence, CommunityFactCodec.decode(payload))
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
        moveAtomic(activeSegment, factsDirectory.resolve("facts-%020d.sealed".format(sequence)))
    }

    private fun readCheckpoint(): Long {
        if (!Files.exists(checkpointFile)) return 0L
        return DataInputStream(Files.newInputStream(checkpointFile)).use { input ->
            require(input.readInt() == CHECKPOINT_MAGIC) { "Invalid community fact checkpoint" }
            require(input.readInt() == CHECKPOINT_VERSION) { "Unsupported community fact checkpoint" }
            input.readLong().also { require(it >= 0) }
        }
    }

    private fun writeCheckpoint(sequence: Long) {
        val bytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + Long.SIZE_BYTES)
            .putInt(CHECKPOINT_MAGIC).putInt(CHECKPOINT_VERSION).putLong(sequence).array()
        writeAtomic(checkpointFile, bytes)
    }

    private fun loadFact(factId: UUID): CommunityFact? {
        synchronized(cache) { cache[factId]?.let { return it.fact } }
        val path = recordPath(factId)
        if (!Files.exists(path)) return null
        val payload = Files.readAllBytes(path)
        val fact = CommunityFactCodec.decode(payload)
        require(fact.factId == factId) { "Community fact ID mismatch" }
        putCache(fact, payload.size.toLong())
        return fact
    }

    private fun findByExternalReference(fact: CommunityFact): CommunityFact? {
        val key = when (fact) {
            is TreasuryLedgerFact -> "treasury" to fact.externalReference
            is MemberLedgerFact -> "member" to fact.externalReference
            else -> return null
        }
        val path = externalPath(key.first, key.second)
        if (!Files.exists(path)) return null
        return loadFact(UUID.fromString(Files.readString(path).trim()))
            ?: error("Missing community external reference target")
    }

    private fun sameExternalOperation(existing: CommunityFact, requested: CommunityFact): Boolean = when {
        existing is TreasuryLedgerFact && requested is TreasuryLedgerFact ->
            existing.regionId == requested.regionId && existing.amount == requested.amount &&
                existing.direction == requested.direction && existing.source == requested.source &&
                existing.operationType == requested.operationType &&
                existing.objectReference == requested.objectReference
        existing is MemberLedgerFact && requested is MemberLedgerFact ->
            existing.regionId == requested.regionId && existing.memberUuid == requested.memberUuid &&
                existing.amount == requested.amount && existing.direction == requested.direction &&
                existing.source == requested.source &&
                existing.countsAsContribution == requested.countsAsContribution
        else -> false
    }

    private fun validate(fact: CommunityFact) {
        require(fact.regionId > 0) { "Community fact region ID must be positive" }
        require(fact.recordedAtMillis >= 0) { "Community fact time must not be negative" }
        when (fact) {
            is CombinationStepFact -> {
                require(fact.stepKey.isNotBlank() && fact.resource.isNotBlank())
                require(fact.externalReference.isNotBlank())
            }
            is TreasuryLedgerFact -> {
                require(fact.amount > 0) { "Treasury amount must be positive" }
                require(fact.source.isNotBlank() && fact.externalReference.isNotBlank())
                require(fact.operationType.isNotBlank() && fact.objectReference.isNotBlank())
            }
            is MemberLedgerFact -> {
                require(fact.amount > 0) { "Member ledger amount must be positive" }
                require(fact.source.isNotBlank() && fact.externalReference.isNotBlank())
                require(!fact.countsAsContribution || fact.direction == ResourceDirection.CREDIT) {
                    "Member contribution must be a credit"
                }
            }
            is CommunityAuditFact -> {
                require(fact.actorName.isNotBlank() && fact.action.isNotBlank())
                require(fact.target.isNotBlank() && fact.result.isNotBlank())
            }
            is PurposeCursorFact -> {
                require(fact.purpose.isNotBlank() && fact.consumerUnitType.isNotBlank())
                require(fact.consumerUnit.isNotBlank() && fact.cursor.isNotBlank())
            }
        }
    }

    private fun preflightAggregate(fact: CommunityFact) {
        when (fact) {
            is TreasuryLedgerFact -> nextAmount(
                readAggregate(treasuryAggregateDirectory.resolve(fact.regionId.toString() + ".state")).amount,
                fact.direction,
                fact.amount
            )
            is MemberLedgerFact -> if (fact.countsAsContribution) {
                nextAmount(readAggregate(memberAggregatePath(fact.regionId, fact.memberUuid)).amount, fact.direction, fact.amount)
            }
            else -> Unit
        }
    }

    private fun applyAggregate(sequence: Long, path: Path, direction: ResourceDirection, amount: Long) {
        val current = readAggregate(path)
        if (current.appliedSequence >= sequence) return
        writeAggregate(path, AggregateState(sequence, nextAmount(current.amount, direction, amount)))
    }

    private fun nextAmount(current: Long, direction: ResourceDirection, amount: Long): Long =
        when (direction) {
            ResourceDirection.CREDIT -> Math.addExact(current, amount)
            ResourceDirection.DEBIT -> Math.subtractExact(current, amount)
        }

    private fun readAggregate(path: Path): AggregateState {
        if (!Files.exists(path)) return AggregateState()
        return DataInputStream(Files.newInputStream(path)).use { input ->
            require(input.readInt() == AGGREGATE_MAGIC) { "Invalid community aggregate" }
            require(input.readInt() == AGGREGATE_VERSION) { "Unsupported community aggregate" }
            val state = AggregateState(input.readLong(), input.readLong())
            require(state.appliedSequence >= 0) { "Invalid community aggregate sequence" }
            require(input.available() == 0) { "Unread community aggregate bytes" }
            state
        }
    }

    private fun writeAggregate(path: Path, state: AggregateState) {
        val bytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + Long.SIZE_BYTES * 2)
            .putInt(AGGREGATE_MAGIC)
            .putInt(AGGREGATE_VERSION)
            .putLong(state.appliedSequence)
            .putLong(state.amount)
            .array()
        writeAtomic(path, bytes)
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

    private fun putCache(fact: CommunityFact, bytes: Long) {
        synchronized(cache) {
            cache.remove(fact.factId)?.let { cacheBytes -= it.bytes }
            if (bytes <= maxCacheBytes) {
                cache[fact.factId] = CachedFact(fact, bytes)
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
        val temporary = Files.createTempFile(target.parent, ".community-fact-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                writeFully(channel, ByteBuffer.wrap(payload))
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

    private fun writeFully(channel: FileChannel, buffer: ByteBuffer) {
        while (buffer.hasRemaining()) channel.write(buffer)
    }

    private fun recordPath(factId: UUID): Path = recordsDirectory.resolve("$factId.fact")

    private fun operationLatestPath(operationId: UUID, stepKey: String): Path =
        operationLatestDirectory.resolve(operationId.toString()).resolve(digest(stepKey) + ".idx")

    private fun memberAggregatePath(regionId: Int, memberUuid: UUID): Path =
        memberAggregateDirectory.resolve(regionId.toString()).resolve(memberUuid.toString() + ".state")

    private fun externalPath(kind: String, reference: String): Path =
        externalDirectory.resolve("$kind-${digest(reference)}.idx")

    private fun cursorPath(regionId: Int, purpose: String, unitType: String, unit: String): Path =
        cursorDirectory.resolve("$regionId-${digest("$purpose\u0000$unitType\u0000$unit")}.idx")

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class CachedFact(val fact: CommunityFact, val bytes: Long)

    private data class AggregateState(val appliedSequence: Long = 0L, val amount: Long = 0L)

    private class CorruptSealedSegmentException(cause: Throwable) : RuntimeException(cause)

    companion object {
        private const val RECORD_MAGIC = 0x434d4631
        private const val CHECKPOINT_MAGIC = 0x434d4931
        private const val CHECKPOINT_VERSION = 1
        private const val AGGREGATE_MAGIC = 0x434d4131
        private const val AGGREGATE_VERSION = 1
        private const val RECORD_HEADER_BYTES = Int.SIZE_BYTES + Long.SIZE_BYTES + Int.SIZE_BYTES
        private const val MAX_FACT_BYTES = 1024 * 1024
        private const val MAX_PAGE_SIZE = 10_000
    }
}
