package com.imyvm.community.infra.account

import com.imyvm.community.domain.model.account.AccountAuditRecord
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.zip.CRC32

class AccountAuditLog(
    private val file: Path,
    private val writer: CommunityDataWriter
) {
    init {
        Files.createDirectories(file.parent)
    }

    fun append(record: AccountAuditRecord): CompletableFuture<Unit> = writer.submit {
        val payload = encode(record)
        require(payload.size <= MAX_RECORD_BYTES)
        val checksum = CRC32().apply { update(payload) }.value.toInt()
        FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND).use { channel ->
            val entry = ByteBuffer.allocate(Int.SIZE_BYTES * 3 + payload.size)
                .putInt(MAGIC).putInt(payload.size).put(payload).putInt(checksum).flip() as ByteBuffer
            while (entry.hasRemaining()) channel.write(entry)
            channel.force(true)
        }
    }

    fun find(transactionId: UUID, limit: Int): CompletableFuture<List<AccountAuditRecord>> = writer.submit {
        require(limit in 1..1000)
        if (!Files.exists(file)) return@submit emptyList()
        val records = ArrayDeque<AccountAuditRecord>(limit)
        DataInputStream(Files.newInputStream(file)).use { input ->
            while (true) {
                val magic = try { input.readInt() } catch (_: EOFException) { break }
                require(magic == MAGIC) { "Invalid account audit record" }
                val length = input.readInt()
                require(length in 0..MAX_RECORD_BYTES)
                val payload = ByteArray(length).also(input::readFully)
                val checksum = input.readInt()
                require(checksum == CRC32().apply { update(payload) }.value.toInt())
                val record = decode(payload)
                if (record.transactionId == transactionId) {
                    if (records.size == limit) records.removeFirst()
                    records.addLast(record)
                }
            }
        }
        records.toList()
    }

    private fun encode(record: AccountAuditRecord): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(1)
            output.writeLong(record.transactionId.mostSignificantBits)
            output.writeLong(record.transactionId.leastSignificantBits)
            output.writeLong(record.recordedAtMillis)
            output.writeUTF(record.actorName)
            output.writeUTF(record.action)
            output.writeBoolean(record.observedBalance != null)
            record.observedBalance?.let(output::writeLong)
            output.writeUTF(record.result)
        }
    }.toByteArray()

    private fun decode(payload: ByteArray): AccountAuditRecord = DataInputStream(payload.inputStream()).use { input ->
        require(input.readInt() == 1)
        val id = UUID(input.readLong(), input.readLong())
        val at = input.readLong()
        val actor = input.readUTF()
        val action = input.readUTF()
        val balance = if (input.readBoolean()) input.readLong() else null
        val result = input.readUTF()
        require(input.available() == 0)
        AccountAuditRecord(id, at, actor, action, balance, result)
    }

    companion object {
        private const val MAGIC = 0x43414131
        private const val MAX_RECORD_BYTES = 64 * 1024
    }
}
