package com.imyvm.community.entrypoint.event

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.interaction.common.submitApplicationRefund
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.account.AccountSubsystem
import com.mojang.authlib.GameProfile
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

fun registerPendingRefundCheck() {
    AccountSubsystem.onReady { runtime ->
        CommunityDatabase.communities.forEach { community ->
            val regionId = community.regionNumberId ?: return@forEach
            community.member.forEach { (uuid, account) ->
                if (account.pendingRefund > 0L) {
                    val name = runtime.identities.find(uuid)?.trustedName ?: uuid.toString()
                    submitApplicationRefund(GameProfile(uuid, name), regionId, account.pendingRefund, System.currentTimeMillis())
                }
            }
        }
    }
    ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        val player = handler.player
        val runtime = AccountSubsystem.runtimeOrNull() ?: return@register
        CommunityDatabase.communities.forEach { community ->
            val regionId = community.regionNumberId ?: return@forEach
            val account = community.member[player.uuid] ?: return@forEach
            if (account.pendingRefund > 0L) {
                submitApplicationRefund(player.gameProfile, regionId, account.pendingRefund, System.currentTimeMillis())
            }
        }
    }
}
