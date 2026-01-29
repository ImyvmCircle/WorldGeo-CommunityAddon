package com.imyvm.community.entrypoints

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.infra.CommunityDatabase
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents

fun registerDataLoadAndSave(){
    dataLoad()
    dataSave()
}

fun dataLoad() {
    try {
        CommunityDatabase.load()
    } catch (e: Exception) {
        WorldGeoCommunityAddon.logger.error("Failed to load community database: ${e.message}", e)
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