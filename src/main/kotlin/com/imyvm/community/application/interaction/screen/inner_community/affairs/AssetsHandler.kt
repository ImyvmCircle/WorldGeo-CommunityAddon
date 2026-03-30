package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.entrypoint.screen.inner_community.affairs.assets.CommunityAssetsMenu
import com.imyvm.community.entrypoint.screen.inner_community.affairs.assets.DonationMenu
import com.imyvm.community.entrypoint.screen.inner_community.affairs.assets.DonorDetailsMenu
import com.imyvm.community.entrypoint.screen.inner_community.affairs.assets.DonorListMenu
import com.imyvm.community.entrypoint.screen.inner_community.affairs.assets.TreasuryLedgerMenu
import com.imyvm.community.entrypoint.screen.inner_community.CommunityMenu
import com.imyvm.community.util.Translator
import com.imyvm.economy.EconomyMod
import net.minecraft.server.level.ServerPlayer
import java.util.*

fun runOpenAssetsMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityAssetsMenu(syncId, player, community) { runBackToCommunityMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenDonationMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canDonate(player, community) }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            DonationMenu(syncId, player, community) { runOpenAssetsMenu(player, community, runBackGrandfather) }
        }
    } ?: player.closeContainer()
}

fun runOpenDonorListMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        DonorListMenu(syncId, community, player, 0) { runOpenAssetsMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenDonorDetailsMenu(player: ServerPlayer, community: Community, donorUUID: UUID, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        DonorDetailsMenu(syncId, player, community, donorUUID) { runOpenDonorListMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenTreasuryLedgerMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        TreasuryLedgerMenu(syncId, community, player, 0) { runOpenAssetsMenu(player, community, runBackGrandfather) }
    }
}

fun onDonateConfirm(player: ServerPlayer, community: Community, amount: Long, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canDonate(player, community) }
    ) {
        val playerAccount = EconomyMod.data.getOrCreate(player)
        
        if (playerAccount.money < amount) {
            player.sendSystemMessage(Translator.tr("ui.community.assets.donate.error.insufficient_funds"))
            player.closeContainer()
            return@executeWithPermission
        }

        val memberAccount = community.member[player.uuid]
        if (memberAccount == null) {
            player.sendSystemMessage(Translator.tr("ui.community.assets.donate.error.not_member"))
            player.closeContainer()
            return@executeWithPermission
        }

        playerAccount.addMoney(-amount)
        memberAccount.turnover.add(Turnover(amount, System.currentTimeMillis(), TurnoverSource.PLAYER, "community.treasury.desc.donation", listOf(player.name.string)))

        val amountFormatted = "%.2f".format(amount / 100.0)
        player.sendSystemMessage(Translator.tr("ui.community.assets.donate.success", amountFormatted))
        
        runOpenAssetsMenu(player, community, runBackGrandfather)
    } ?: player.closeContainer()
}

private fun runBackToCommunityMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMenu(syncId, player, community, runBackGrandfather)
    }
}
