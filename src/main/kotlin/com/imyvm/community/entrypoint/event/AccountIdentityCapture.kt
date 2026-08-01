package com.imyvm.community.entrypoint.event

import com.imyvm.community.application.development.CommunityDevelopmentService
import com.imyvm.community.application.fiscal.CommunityFiscalService
import com.imyvm.community.infra.account.AccountSubsystem
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

fun registerAccountIdentityCapture() {
    ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        AccountSubsystem.captureIdentity(handler.player)
        CommunityDevelopmentService.recordMemberLogin(handler.player.uuid)
        CommunityFiscalService.recordLoginObservation(handler.player.uuid, handler.player.gameProfile.name)
    }
}
