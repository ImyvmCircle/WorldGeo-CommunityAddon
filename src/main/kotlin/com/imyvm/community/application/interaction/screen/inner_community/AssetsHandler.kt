package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.Turnover
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.entrypoints.screen.inner_community.CommunityAssetsMenu
import com.imyvm.community.entrypoints.screen.inner_community.DonationMenu
import com.imyvm.community.entrypoints.screen.inner_community.DonorDetailsMenu
import com.imyvm.community.entrypoints.screen.inner_community.DonorListMenu
import com.imyvm.community.util.Translator
import com.imyvm.economy.EconomyMod
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun runOpenAssetsMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityAssetsMenu(syncId, player, community) { runBackToCommunityMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenDonationMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    val memberRole = community.getMemberRole(player.uuid)
    if (memberRole == null || memberRole == MemberRoleType.APPLICANT || memberRole == MemberRoleType.REFUSED) {
        player.sendMessage(Translator.tr("ui.community.assets.donate.error.not_member"))
        player.closeHandledScreen()
        return
    }

    CommunityMenuOpener.open(player) { syncId ->
        DonationMenu(syncId, player, community) { runOpenAssetsMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenDonorListMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        DonorListMenu(syncId, community, player, 0) { runOpenAssetsMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenDonorDetailsMenu(player: ServerPlayerEntity, community: Community, donorUUID: UUID, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        DonorDetailsMenu(syncId, player, community, donorUUID) { runOpenDonorListMenu(player, community, runBackGrandfather) }
    }
}

fun onDonateConfirm(player: ServerPlayerEntity, community: Community, amount: Int, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    val playerAccount = EconomyMod.data.getOrCreate(player)
    
    if (playerAccount.money < amount.toLong()) {
        player.sendMessage(Translator.tr("ui.community.assets.donate.error.insufficient_funds"))
        player.closeHandledScreen()
        return
    }

    val memberAccount = community.member[player.uuid]
    if (memberAccount == null) {
        player.sendMessage(Translator.tr("ui.community.assets.donate.error.not_member"))
        player.closeHandledScreen()
        return
    }

    playerAccount.addMoney(-amount.toLong())
    memberAccount.turnover.add(Turnover(amount, System.currentTimeMillis()))

    val amountFormatted = "%.2f".format(amount / 100.0)
    player.sendMessage(Translator.tr("ui.community.assets.donate.success", amountFormatted))
    
    runOpenAssetsMenu(player, community, runBackGrandfather)
}

private fun runBackToCommunityMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    runBackGrandfather(player)
}
