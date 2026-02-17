package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.permission.PermissionCheck
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.entrypoints.screen.inner_community.affairs.assets.CommunityAssetsMenu
import com.imyvm.community.entrypoints.screen.inner_community.affairs.assets.DonationMenu
import com.imyvm.community.entrypoints.screen.inner_community.affairs.assets.DonorDetailsMenu
import com.imyvm.community.entrypoints.screen.inner_community.affairs.assets.DonorListMenu
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
    PermissionCheck.executeWithPermission(
        player,
        { PermissionCheck.canDonate(player, community) }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            DonationMenu(syncId, player, community) { runOpenAssetsMenu(player, community, runBackGrandfather) }
        }
    } ?: player.closeHandledScreen()
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

fun onDonateConfirm(player: ServerPlayerEntity, community: Community, amount: Long, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    PermissionCheck.executeWithPermission(
        player,
        { PermissionCheck.canDonate(player, community) }
    ) {
        val playerAccount = EconomyMod.data.getOrCreate(player)
        
        if (playerAccount.money < amount) {
            player.sendMessage(Translator.tr("ui.community.assets.donate.error.insufficient_funds"))
            player.closeHandledScreen()
            return@executeWithPermission
        }

        val memberAccount = community.member[player.uuid]
        if (memberAccount == null) {
            player.sendMessage(Translator.tr("ui.community.assets.donate.error.not_member"))
            player.closeHandledScreen()
            return@executeWithPermission
        }

        playerAccount.addMoney(-amount)
        memberAccount.turnover.add(Turnover(amount, System.currentTimeMillis()))

        val amountFormatted = "%.2f".format(amount / 100.0)
        player.sendMessage(Translator.tr("ui.community.assets.donate.success", amountFormatted))
        
        runOpenAssetsMenu(player, community, runBackGrandfather)
    } ?: player.closeHandledScreen()
}

private fun runBackToCommunityMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    runBackGrandfather(player)
}
