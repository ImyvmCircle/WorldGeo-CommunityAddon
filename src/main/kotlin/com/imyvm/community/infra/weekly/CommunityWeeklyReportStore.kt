package com.imyvm.community.infra.weekly

import com.imyvm.community.domain.model.weekly.CommunityWeeklyReport
import com.imyvm.community.domain.model.weekly.WeeklyReportAudience
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

object CommunityWeeklyReportStore {
    private const val FILE_NAME = "community-weekly-reports.db"
    private const val VERSION = 1
    private const val MAX_REPORTS = 5000
    private var file: Path? = null
    private val reports = mutableListOf<CommunityWeeklyReport>()

    fun initialize(root: Path) {
        file = root.resolve(FILE_NAME)
        load()
    }

    fun upsert(report: CommunityWeeklyReport): Boolean {
        val index = reports.indexOfFirst { it.id == report.id }
        val created = index < 0
        if (index >= 0) {
            reports[index] = report.copy(readBy = reports[index].readBy)
        } else {
            reports.add(report)
        }
        trim()
        save()
        return created
    }

    fun listFor(playerUuid: UUID, isOp: Boolean): List<CommunityWeeklyReport> = reports
        .filter { it.recipientUuid == playerUuid || (isOp && it.audience == WeeklyReportAudience.OP && it.recipientUuid == null) }
        .sortedWith(compareByDescending<CommunityWeeklyReport> { it.createdAtMillis }.thenBy { it.audience.name }.thenBy { it.id })

    fun unreadCount(playerUuid: UUID, isOp: Boolean): Int = listFor(playerUuid, isOp).count { !it.isReadBy(playerUuid) }

    fun markRead(report: CommunityWeeklyReport, playerUuid: UUID) {
        if (report.readBy.add(playerUuid)) save()
    }

    internal fun clearForTest() {
        reports.clear()
        save()
    }

    private fun trim() {
        if (reports.size <= MAX_REPORTS) return
        val keep = reports.sortedByDescending { it.createdAtMillis }.take(MAX_REPORTS).map { it.id }.toSet()
        reports.removeIf { it.id !in keep }
    }

    private fun load() {
        reports.clear()
        val path = file ?: return
        if (!Files.exists(path)) return
        DataInputStream(Files.newInputStream(path)).use { input ->
            val version = input.readInt()
            require(version == VERSION) { "Unsupported weekly report store version: $version" }
            repeat(input.readInt()) {
                val id = input.readUTF()
                val hasRecipient = input.readBoolean()
                val recipient = if (hasRecipient) UUID.fromString(input.readUTF()) else null
                val audience = WeeklyReportAudience.valueOf(input.readUTF())
                val weekKey = input.readUTF()
                val title = input.readUTF()
                val lineCount = input.readInt()
                val lines = MutableList(lineCount) { input.readUTF() }
                val createdAt = input.readLong()
                val readCount = input.readInt()
                val readBy = MutableList(readCount) { UUID.fromString(input.readUTF()) }.toMutableSet()
                reports.add(CommunityWeeklyReport(id, recipient, audience, weekKey, title, lines, createdAt, readBy))
            }
        }
    }

    private fun save() {
        val path = file ?: return
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, "$FILE_NAME.", ".tmp")
        DataOutputStream(Files.newOutputStream(temp)).use { output ->
            output.writeInt(VERSION)
            output.writeInt(reports.size)
            for (report in reports) {
                output.writeUTF(report.id)
                output.writeBoolean(report.recipientUuid != null)
                if (report.recipientUuid != null) output.writeUTF(report.recipientUuid.toString())
                output.writeUTF(report.audience.name)
                output.writeUTF(report.weekKey)
                output.writeUTF(report.title)
                output.writeInt(report.lines.size)
                report.lines.forEach(output::writeUTF)
                output.writeLong(report.createdAtMillis)
                output.writeInt(report.readBy.size)
                report.readBy.forEach { output.writeUTF(it.toString()) }
            }
        }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
