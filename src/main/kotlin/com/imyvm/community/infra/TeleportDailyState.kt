package com.imyvm.community.infra

import com.imyvm.community.WorldGeoCommunityAddon
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object TeleportDailyState {
    private val ZONE = ZoneId.of(CommunityConfig.TIMEZONE.value)
    private const val FILE_VERSION = 1

    private var stateDir: Path? = null
    private var currentDayKey: String = ""
    private val counts: MutableMap<String, Int> = mutableMapOf()

    @Volatile private var publishedDayKey: String? = null

    fun initialize(worldRoot: Path) {
        stateDir = worldRoot.resolve("community-daily-teleport").also { Files.createDirectories(it) }
        val today = LocalDate.now(ZoneId.of(CommunityConfig.TIMEZONE.value)).toString()
        loadDay(today)
    }

    private fun loadDay(dayKey: String) {
        currentDayKey = dayKey
        counts.clear()
        val file = stateDir?.resolve("teleport-$dayKey.dat") ?: return
        if (!Files.exists(file)) {
            publishedDayKey = null
            return
        }
        try {
            DataInputStream(Files.newInputStream(file)).use { stream ->
                val version = stream.readInt()
                check(version == FILE_VERSION) { "Unknown teleport daily state version $version" }
                val storedDay = stream.readUTF()
                check(storedDay == dayKey) { "Day key mismatch: expected $dayKey, found $storedDay" }
                val count = stream.readInt()
                repeat(count) {
                    val key = stream.readUTF()
                    val value = stream.readInt()
                    counts[key] = value
                }
            }
            publishedDayKey = dayKey
        } catch (error: Exception) {
            WorldGeoCommunityAddon.logger.error("Failed to load daily teleport state for $dayKey; counts reset", error)
            counts.clear()
            publishedDayKey = null
        }
    }

    fun checkRollover() {
        val today = LocalDate.now(ZoneId.of(CommunityConfig.TIMEZONE.value)).toString()
        if (today == currentDayKey) return
        val oldDay = currentDayKey
        loadDay(today)
        stateDir?.resolve("teleport-$oldDay.dat")?.let { old ->
            runCatching { Files.deleteIfExists(old) }
                .onFailure { WorldGeoCommunityAddon.logger.warn("Failed to delete daily teleport state $oldDay", it) }
        }
    }

    fun currentDayKey(): String {
        checkRollover()
        return currentDayKey
    }

    fun isStatePublishedForToday(): Boolean {
        checkRollover()
        return publishedDayKey == currentDayKey
    }

    fun getCount(playerUuid: UUID, regionId: Int): Int {
        checkRollover()
        return counts[slotKey(playerUuid, regionId)] ?: 0
    }

    fun reserve(playerUuid: UUID, regionId: Int): Int {
        checkRollover()
        val key = slotKey(playerUuid, regionId)
        val before = counts[key] ?: 0
        counts[key] = before + 1
        persist()
        return before
    }

    fun release(playerUuid: UUID, regionId: Int) {
        val key = slotKey(playerUuid, regionId)
        val current = counts[key] ?: 0
        if (current <= 0) return
        counts[key] = current - 1
        persist()
    }

    private fun slotKey(playerUuid: UUID, regionId: Int) = "$playerUuid:$regionId"

    private fun persist() {
        val dir = stateDir ?: return
        val dayKey = currentDayKey
        val snapshot = counts.toMap()
        Thread({
            try {
                val file = dir.resolve("teleport-$dayKey.dat")
                val tmp = dir.resolve("teleport-$dayKey.dat.tmp")
                DataOutputStream(Files.newOutputStream(tmp)).use { stream ->
                    stream.writeInt(FILE_VERSION)
                    stream.writeUTF(dayKey)
                    stream.writeInt(snapshot.size)
                    snapshot.forEach { (key, value) ->
                        stream.writeUTF(key)
                        stream.writeInt(value)
                    }
                    stream.flush()
                }
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                publishedDayKey = dayKey
            } catch (error: Exception) {
                WorldGeoCommunityAddon.logger.error("Failed to persist daily teleport state for $dayKey", error)
            }
        }, "community-teleport-daily-persist").apply { isDaemon = true }.start()
    }
}
