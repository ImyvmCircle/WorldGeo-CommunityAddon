package com.imyvm.community.entrypoint.event

import com.imyvm.community.application.event.checkPendingOperations
import com.imyvm.community.application.interaction.screen.inner_community.processPendingTeleportExecutions
import com.imyvm.community.infra.CommunityConfig
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

fun registerExpireCheck() {
    var tickCounter = 0

    ServerTickEvents.END_SERVER_TICK.register { server ->
        tickCounter++
        if (tickCounter >= CommunityConfig.PENDING_CHECK_INTERVAL_SECONDS.value * 20) {
            tickCounter = 0
            processPendingTeleportExecutions()
            checkPendingOperations(server)
        }
    }
}
