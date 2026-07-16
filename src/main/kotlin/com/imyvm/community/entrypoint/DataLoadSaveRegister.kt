package com.imyvm.community.entrypoint

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents

fun registerDataLoadAndSave(){
    dataLoad()
    dataSave()
    captureServerInstance()
}

fun dataLoad() {
    try {
        CommunityConfig.validateValues()
        PricingConfig.validateValues()
        CommunityDatabase.load()
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
    ServerLifecycleEvents.SERVER_STARTED.register { server ->
        WorldGeoCommunityAddon.server = server
    }
    
    ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
        WorldGeoCommunityAddon.server = null
    }
}