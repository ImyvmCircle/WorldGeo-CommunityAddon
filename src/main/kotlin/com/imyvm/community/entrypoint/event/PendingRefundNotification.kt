package com.imyvm.community.entrypoint.event

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.economy.EconomyMod
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerPlayer

fun registerPendingRefundCheck() {
    ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        refundPendingRefunds(handler.player)
    }
}

private fun refundPendingRefunds(player: ServerPlayer) {
    val refunds = mutableListOf<Pair<MemberAccount, Long>>()
    for (community in CommunityDatabase.communities) {
        val memberAccount = community.member[player.uuid] ?: continue
        if (memberAccount.pendingRefund <= 0L) continue
        refunds.add(memberAccount to memberAccount.pendingRefund)
        memberAccount.pendingRefund = 0L
    }

    val refundTotal = refunds.sumOf { it.second }
    if (refundTotal <= 0L) return

    val playerData = EconomyMod.data.getOrCreate(player)
    playerData.addMoney(refundTotal)
    try {
        CommunityDatabase.save()
        player.sendSystemMessage(Translator.tr("community.join.refund", refundTotal / 100.0))
    } catch (e: Exception) {
        playerData.addMoney(-refundTotal)
        for ((memberAccount, amount) in refunds) {
            memberAccount.pendingRefund += amount
        }
        WorldGeoCommunityAddon.logger.error("Failed to save deferred refund for ${player.name.string}", e)
    }
}
