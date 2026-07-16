package com.imyvm.community.application.helper

import com.imyvm.community.domain.model.Community
import com.imyvm.economy.EconomyMod
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

fun refundNotCreated(player: ServerPlayer, community: Community) {
    refundNotCreated(player, community, player.uuid)
}

fun refundNotCreated(player: ServerPlayer?, community: Community, ownerUUID: UUID) {
    if (player != null) {
        EconomyMod.data.getOrCreate(player).addMoney(community.creationCost)
        return
    }
    community.member[ownerUUID]?.pendingRefund = (community.member[ownerUUID]?.pendingRefund ?: 0L) + community.creationCost
}
