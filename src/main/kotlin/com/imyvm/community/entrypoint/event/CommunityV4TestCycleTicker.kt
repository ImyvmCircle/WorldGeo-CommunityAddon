package com.imyvm.community.entrypoint.event

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.testcycle.CommunityV4TestCycleService
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

fun registerCommunityV4TestCycleTicker() {
    ServerTickEvents.END_SERVER_TICK.register {
        try {
            CommunityV4TestCycleService.processDue()
        } catch (e: Exception) {
            WorldGeoCommunityAddon.logger.error("Failed to process community v4 test cycle: ${e.message}", e)
        }
    }
}
