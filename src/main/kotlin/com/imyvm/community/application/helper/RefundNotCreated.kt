package com.imyvm.community.application.helper

import com.imyvm.community.application.interaction.common.submitApplicationRefund
import com.imyvm.community.domain.model.Community
import com.imyvm.community.infra.account.AccountSubsystem
import com.mojang.authlib.GameProfile
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

fun refundNotCreated(player: ServerPlayer, community: Community) {
    refundNotCreated(player, community, player.uuid)
}

fun refundNotCreated(player: ServerPlayer?, community: Community, ownerUUID: UUID) {
    val amount = community.creationCost
    if (amount <= 0L) return
    val regionId = community.regionNumberId ?: return
    val runtime = AccountSubsystem.runtimeOrNull()
    if (runtime != null) {
        val name = player?.gameProfile?.name
            ?: runtime.identities.find(ownerUUID)?.trustedName
            ?: ownerUUID.toString()
        submitApplicationRefund(GameProfile(ownerUUID, name), regionId, amount, System.currentTimeMillis())
    } else {
        community.member[ownerUUID]?.pendingRefund =
            (community.member[ownerUUID]?.pendingRefund ?: 0L) + amount
    }
}
