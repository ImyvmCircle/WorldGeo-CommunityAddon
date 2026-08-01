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
    private const val FILE_VERSION = 2

    private var stateDir: Path? = null
    private var currentDayKey: String = ""
    private val counts: MutableMap<String, Int> = mutableMapOf()
    private val likes: MutableSet<String> = mutableSetOf()

    @Volatile private var publishedDayKey: String? = null
    @Volatile private var damagedDayKey: String? = null

    fun initialize(worldRoot: Path) {
        stateDir = worldRoot.resolve("community-daily-teleport").also { Files.createDirectories(it) }
        val today = LocalDate.now(ZoneId.of(CommunityConfig.TIMEZONE.value)).toString()
        loadDay(today)
    }

    private fun loadDay(dayKey: String) {
        currentDayKey = dayKey
        counts.clear()
        likes.clear()
        damagedDayKey = null
        val file = stateDir?.resolve("teleport-$dayKey.dat") ?: return
        if (!Files.exists(file)) {
            publishedDayKey = null
            persist()
            return
        }
        try {
            DataInputStream(Files.newInputStream(file)).use { stream ->
                val version = stream.readInt()
                require(version in 1..FILE_VERSION) { "Unsupported daily state version: $version" }
                val storedDay = stream.readUTF()
                require(storedDay == dayKey) { "Day key mismatch: expected $dayKey, found $storedDay" }
                val countSize = stream.readInt()
                require(countSize >= 0) { "Invalid daily teleport count size" }
                repeat(countSize) { counts[stream.readUTF()] = stream.readInt() }
                if (version >= 2) {
                    val likeSize = stream.readInt()
                    require(likeSize >= 0) { "Invalid daily like size" }
                    repeat(likeSize) { likes.add(stream.readUTF()) }
                }
            }
            publishedDayKey = dayKey
        } catch (error: Exception) {
            WorldGeoCommunityAddon.logger.error("Failed to load daily interaction state for $dayKey", error)
            counts.clear()
            likes.clear()
            publishedDayKey = dayKey
            damagedDayKey = dayKey
        }
    }

    fun checkRollover() {
        val today = LocalDate.now(ZoneId.of(CommunityConfig.TIMEZONE.value)).toString()
        if (today == currentDayKey) return
        val oldDay = currentDayKey
        loadDay(today)
        stateDir?.resolve("teleport-$oldDay.dat")?.let { old ->
            runCatching { Files.deleteIfExists(old) }
                .onFailure { WorldGeoCommunityAddon.logger.warn("Failed to delete daily interaction state $oldDay", it) }
        }
    }

    fun currentDayKey(): String { checkRollover(); return currentDayKey }
    fun isStatePublishedForToday(): Boolean { checkRollover(); return publishedDayKey == currentDayKey }
    fun isStateWritableForToday(): Boolean { checkRollover(); return isStatePublishedForToday() && damagedDayKey != currentDayKey }
    fun getCount(playerUuid: UUID, regionId: Int): Int { checkRollover(); return counts[teleportKey(playerUuid, regionId)] ?: 0 }

    fun reserve(playerUuid: UUID, regionId: Int): Int {
        checkRollover()
        check(isStateWritableForToday()) { "Daily interaction state is not writable" }
        val key = teleportKey(playerUuid, regionId)
        val before = counts[key] ?: 0
        counts[key] = before + 1
        persist()
        return before
    }

    fun release(playerUuid: UUID, regionId: Int) {
        checkRollover()
        if (!isStateWritableForToday()) return
        val key = teleportKey(playerUuid, regionId)
        val current = counts[key] ?: 0
        if (current <= 0) return
        counts[key] = current - 1
        persist()
    }

    fun hasLikedToday(playerUuid: UUID, regionId: Int): Boolean {
        checkRollover()
        return likes.contains(likeKey(regionId, playerUuid))
    }

    fun recordLike(playerUuid: UUID, regionId: Int): Boolean {
        checkRollover()
        check(isStateWritableForToday()) { "Daily interaction state is not writable" }
        val key = likeKey(regionId, playerUuid)
        if (likes.contains(key)) return false
        likes.add(key)
        persist()
        return true
    }

    private fun teleportKey(playerUuid: UUID, regionId: Int) = "t:$playerUuid:$regionId"
    private fun likeKey(regionId: Int, playerUuid: UUID) = "l:$regionId:$playerUuid"

    private fun persist() {
        val dir = stateDir ?: return
        val dayKey = currentDayKey
        val file = dir.resolve("teleport-$dayKey.dat")
        val tmp = dir.resolve("teleport-$dayKey.dat.tmp")
        DataOutputStream(Files.newOutputStream(tmp)).use { stream ->
            stream.writeInt(FILE_VERSION)
            stream.writeUTF(dayKey)
            stream.writeInt(counts.size)
            counts.forEach { (key, value) -> stream.writeUTF(key); stream.writeInt(value) }
            stream.writeInt(likes.size)
            likes.forEach { stream.writeUTF(it) }
            stream.flush()
        }
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        publishedDayKey = dayKey
    }
}
