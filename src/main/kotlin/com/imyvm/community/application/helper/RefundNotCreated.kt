package com.imyvm.community.application.helper

import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.CommunityStatus
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.economy.EconomyMod
import net.minecraft.server.network.ServerPlayerEntity

fun refundNotCreated(player: ServerPlayerEntity, community: Community) {
    val playerAccount = EconomyMod.data.getOrCreate(player)
    playerAccount.addMoney(community.creationCost)
}