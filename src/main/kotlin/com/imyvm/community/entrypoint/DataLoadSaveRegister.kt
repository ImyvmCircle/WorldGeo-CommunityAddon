package com.imyvm.community.entrypoint

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.helper.CommunityBackgroundTasks
import com.imyvm.community.application.interaction.common.ChatChannelManager
import com.imyvm.community.application.townbuilding.BuildingRewardPreviewTracker
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.infra.TeleportDailyState
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.infra.communication.CommunicationShardStore
import com.imyvm.community.infra.weekly.CommunityWeeklyReportStore
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource

fun registerDataLoadAndSave() {
    dataLoad()
    dataSave()
    captureServerInstance()
    accountLifecycle()
    registerCommCleanup()
    registerPlayerStateCleanup()
}

fun dataLoad() {
    ServerLifecycleEvents.SERVER_STARTING.register { server ->
        WorldGeoCommunityAddon.server = server
        loadData(server)
    }
}

private fun loadData(server: MinecraftServer) {
    try {
        CommunityConfig.validateValues()
        PricingConfig.validateValues()
        TeleportDailyState.initialize(server.getWorldPath(LevelResource.ROOT))
        CommunicationShardStore.initialize(server.getWorldPath(LevelResource.ROOT))
        CommunityWeeklyReportStore.initialize(server.getWorldPath(LevelResource.ROOT))
        CommunityDatabase.load(server)
    } catch (e: Exception) {
        try {
            val backupPath = CommunityDatabase.backupDatabaseAfterLoadFailure()
            if (backupPath != null) {
                WorldGeoCommunityAddon.logger.error("Failed to load community database. Corrupt copy saved to $backupPath", e)
            } else {
                WorldGeoCommunityAddon.logger.error("Failed to load community database: ${e.message}", e)
            }
        } catch (backupError: Exception) {
            WorldGeoCommunityAddon.logger.error("Failed to load community database and failed to save corrupt copy: ${backupError.message}", e)
        }
        throw IllegalStateException("Failed to load community database", e)
    }
}

fun dataSave() {
    ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
        try {
            CommunityDatabase.save()
        } catch (e: Exception) {
            WorldGeoCommunityAddon.logger.error("Failed to save community database: ${e.message}", e)
        }
    }
}

fun captureServerInstance() {
    ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
        CommunityBackgroundTasks.stop()
        WorldGeoCommunityAddon.server = null
    }
}


private fun registerPlayerStateCleanup() {
    ServerPlayConnectionEvents.DISCONNECT.register { player, _ ->
        ChatChannelManager.clearChannel(player.player.uuid)
        BuildingRewardPreviewTracker.clear(player.player.uuid)
    }
}

private fun accountLifecycle() {
    ServerLifecycleEvents.SERVER_STARTED.register(AccountSubsystem::start)
    ServerLifecycleEvents.SERVER_STOPPING.register { AccountSubsystem.stop() }
}

private fun registerCommCleanup() {
    var tickCount = 0
    ServerTickEvents.END_SERVER_TICK.register { _ ->
        tickCount++
        if (tickCount >= 72000) {
            tickCount = 0
            Thread({ CommunicationShardStore.runRetentionCleanup() }, "community-comm-cleanup")
                .apply { isDaemon = true }.start()
        }
    }
}
