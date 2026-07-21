package com.imyvm.community.infra.testcycle

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.community.TaxWelfareSettlement
import com.imyvm.community.domain.model.community.TaxWelfareSettlementStatus
import com.imyvm.community.domain.model.testcycle.CommunityV4TestCycleRun
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object CommunityV4TestCycleDatabase {
    private const val DATABASE_FILENAME = "iwg_community_v4_test.db"
    private const val DATABASE_VERSION = 1
    var runs: MutableList<CommunityV4TestCycleRun> = mutableListOf()

    fun load(server: MinecraftServer) {
        val file = getDatabasePath(server)
        if (!Files.exists(file)) {
            runs = mutableListOf()
            return
        }
        DataInputStream(Files.newInputStream(file)).use { stream ->
            val version = stream.readInt()
            require(version == DATABASE_VERSION) { "Unsupported community v4 test database version: $version" }
            val count = stream.readInt()
            runs = MutableList(count) { readRun(stream) }
        }
    }

    fun save() {
        val file = getDatabasePath(WorldGeoCommunityAddon.server)
        val parent = file.parent
        if (parent != null) Files.createDirectories(parent)
        val tempFile = Files.createTempFile(parent, "$DATABASE_FILENAME.", ".tmp")
        try {
            DataOutputStream(Files.newOutputStream(tempFile)).use { stream ->
                stream.writeInt(DATABASE_VERSION)
                stream.writeInt(runs.size)
                for (run in runs) writeRun(stream, run)
            }
            replace(tempFile, file)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun replace(tempFile: Path, targetFile: Path) {
        try {
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeRun(stream: DataOutputStream, run: CommunityV4TestCycleRun) {
        stream.writeUTF(run.runId)
        stream.writeInt(run.regionNumberId)
        stream.writeLong(run.startedAt)
        stream.writeLong(run.nextCycleAt)
        stream.writeInt(run.completedCycles)
        stream.writeInt(run.maxCycles)
        stream.writeLong(run.periodMillis)
        stream.writeBoolean(run.active)
        stream.writeInt(run.settlements.size)
        for (settlement in run.settlements) writeSettlement(stream, settlement)
    }

    private fun readRun(stream: DataInputStream): CommunityV4TestCycleRun {
        val run = CommunityV4TestCycleRun(
            runId = stream.readUTF(),
            regionNumberId = stream.readInt(),
            startedAt = stream.readLong(),
            nextCycleAt = stream.readLong(),
            completedCycles = stream.readInt(),
            maxCycles = stream.readInt(),
            periodMillis = stream.readLong(),
            active = stream.readBoolean()
        )
        repeat(stream.readInt()) { run.settlements.add(readSettlement(stream)) }
        return run
    }

    private fun writeSettlement(stream: DataOutputStream, settlement: TaxWelfareSettlement) {
        stream.writeUTF(settlement.settlementId)
        stream.writeUTF(settlement.periodId)
        stream.writeLong(settlement.createdAt)
        stream.writeLong(settlement.totalAssetsAtFreeze)
        stream.writeLong(settlement.taxAmount)
        stream.writeLong(settlement.welfareAmount)
        stream.writeInt(settlement.status.value)
        stream.writeBoolean(settlement.failureReason != null)
        if (settlement.failureReason != null) stream.writeUTF(settlement.failureReason!!)
        stream.writeInt(settlement.retryCount)
        stream.writeLong(settlement.nextRetryAt)
    }

    private fun readSettlement(stream: DataInputStream): TaxWelfareSettlement = TaxWelfareSettlement(
        settlementId = stream.readUTF(),
        periodId = stream.readUTF(),
        createdAt = stream.readLong(),
        totalAssetsAtFreeze = stream.readLong(),
        taxAmount = stream.readLong(),
        welfareAmount = stream.readLong(),
        status = TaxWelfareSettlementStatus.fromValue(stream.readInt()),
        failureReason = if (stream.readBoolean()) stream.readUTF() else null,
        retryCount = stream.readInt(),
        nextRetryAt = stream.readLong()
    )

    private fun getDatabasePath(server: MinecraftServer?): Path {
        if (server != null) return server.getWorldPath(LevelResource.ROOT).resolve(DATABASE_FILENAME)
        return FabricLoader.getInstance().gameDir.resolve("world").resolve(DATABASE_FILENAME)
    }
}
