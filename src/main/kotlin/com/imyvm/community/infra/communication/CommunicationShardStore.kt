package com.imyvm.community.infra.communication

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.domain.model.communication.CommunicationCategory
import com.imyvm.community.domain.model.communication.CommunicationRecord
import com.imyvm.community.domain.model.communication.CommunicationVisibility
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32

object CommunicationShardStore {
    private const val RECORD_VERSION = 1
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private var shardRoot: Path? = null

    fun initialize(worldRoot: Path) {
        shardRoot = worldRoot.resolve("community-comms").also { Files.createDirectories(it) }
    }

    fun append(record: CommunicationRecord, category: CommunicationCategory) {
        val root = shardRoot ?: return
        val date = Instant.ofEpochMilli(record.recordedAtMillis)
            .atZone(ZoneId.of(CommunityConfig.TIMEZONE.value)).format(DATE_FMT)
        val file = root.resolve("comm-${category.filePrefix}-${record.regionId}-$date.log")
        try {
            val data = encode(record)
            val checksum = crc32(data)
            Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { raw ->
                DataOutputStream(raw).use { out ->
                    out.writeInt(data.size)
                    out.writeInt(checksum)
                    out.write(data)
                    out.flush()
                }
            }
        } catch (error: IOException) {
            WorldGeoCommunityAddon.logger.error(
                "Failed to append ${category.filePrefix} shard for region ${record.regionId}", error
            )
        }
    }

    fun closeOpException(regionId: Int, exceptionStableId: String) {
        val root = shardRoot ?: return
        val prefix = "comm-OPX-$regionId-"
        try {
            Files.list(root).use { stream ->
                stream.filter { it.fileName.toString().startsWith(prefix) }.forEach { file ->
                    try {
                        val targetName = file.fileName.toString().replace("comm-OPX-", "comm-SYS-")
                        Files.move(file, root.resolve(targetName), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                    } catch (error: IOException) {
                        WorldGeoCommunityAddon.logger.error(
                            "Failed to close OPX shard for region $regionId exception $exceptionStableId", error
                        )
                    }
                }
            }
        } catch (error: IOException) {
            WorldGeoCommunityAddon.logger.error(
                "Failed to scan OPX shards for region $regionId", error
            )
        }
    }

    fun runRetentionCleanup() {
        runRetentionCleanup(System.currentTimeMillis())
    }

    internal fun runRetentionCleanup(now: Long) {
        val root = shardRoot ?: return
        CommunicationCategory.values()
            .filter { it.retentionDays != null }
            .forEach { category ->
                val cutoff = now - category.retentionDays!! * 86_400_000L
                try {
                    Files.list(root).use { stream ->
                        stream.filter { file ->
                            val name = file.fileName.toString()
                            name.startsWith("comm-${category.filePrefix}-") && extractDateMillis(name) < cutoff
                        }.forEach { file ->
                            runCatching { Files.deleteIfExists(file) }.onFailure {
                                WorldGeoCommunityAddon.logger.warn("Failed to delete expired shard $file", it)
                            }
                        }
                    }
                } catch (error: IOException) {
                    WorldGeoCommunityAddon.logger.warn("Failed to scan ${category.filePrefix} shards for cleanup", error)
                }
            }
    }

    private fun extractDateMillis(fileName: String): Long {
        val noExt = fileName.removeSuffix(".log")
        val parts = noExt.split("-")
        if (parts.size < 3) return Long.MAX_VALUE
        val dateStr = parts.takeLast(3).joinToString("-")
        return runCatching {
            val local = java.time.LocalDate.parse(dateStr, DATE_FMT)
            local.atStartOfDay(ZoneId.of(CommunityConfig.TIMEZONE.value)).toInstant().toEpochMilli()
        }.getOrDefault(Long.MAX_VALUE)
    }

    private fun encode(record: CommunicationRecord): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        DataOutputStream(buf).use { stream ->
            stream.writeInt(RECORD_VERSION)
            stream.writeInt(record.regionId)
            stream.writeLong(record.recordedAtMillis)
            stream.writeUTF(record.senderUuid ?: "")
            stream.writeUTF(record.senderName ?: "")
            stream.writeUTF(record.type.name)
            stream.writeUTF(record.legacyText ?: "")
            stream.writeUTF(record.localizationKey ?: "")
            stream.writeInt(record.localizationArgs.size)
            record.localizationArgs.forEach { stream.writeUTF(it) }
            stream.writeUTF(record.visibility.name)
        }
        return buf.toByteArray()
    }

    private fun crc32(data: ByteArray): Int {
        val crc = CRC32()
        crc.update(data)
        return crc.value.toInt()
    }
}
