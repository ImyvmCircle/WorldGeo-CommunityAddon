package com.imyvm.community.infra.economy

import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.economy.api.DatabaseApi
import com.imyvm.economy.api.PlayerWallet
import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import java.util.UUID

class EconomyWalletAdapter {
    fun <T> withWallet(
        server: MinecraftServer,
        subjectUuid: UUID,
        trustedName: String,
        action: WalletSession.() -> T
    ): T {
        check(server.isSameThread) { "Economy wallet access must run on the server thread" }
        require(trustedName.isNotBlank() && trustedName != subjectUuid.toString()) { "Trusted player name required" }
        val onlinePlayer = server.playerList.getPlayer(subjectUuid)
        val player = onlinePlayer ?: OfflineEconomyPlayer(server.overworld(), GameProfile(subjectUuid, trustedName))
        return WalletSession(DatabaseApi.getInstance().getPlayer(player)).action()
    }

    class WalletSession internal constructor(private val wallet: PlayerWallet) {
        fun balance(): Long = wallet.money

        fun mutate(direction: AccountDirection, amount: Long): Boolean = when (direction) {
            AccountDirection.CREDIT -> {
                wallet.addMoney(amount)
                true
            }
            AccountDirection.DEBIT -> wallet.takeMoney(amount)
        }
    }

    companion object {
        fun balance(player: Player): Long = onlineWallet(player).money

        fun credit(player: Player, amount: Long) {
            require(amount >= 0)
            if (amount > 0) onlineWallet(player).addMoney(amount)
        }

        fun debit(player: Player, amount: Long): Boolean {
            require(amount >= 0)
            return amount == 0L || onlineWallet(player).takeMoney(amount)
        }

        private fun onlineWallet(player: Player): PlayerWallet {
            val server = player.level().server ?: error("Player is not attached to a server")
            check(server.isSameThread) { "Economy wallet access must run on the server thread" }
            return DatabaseApi.getInstance().getPlayer(player)
        }
    }

    private class OfflineEconomyPlayer(
        level: net.minecraft.world.level.Level,
        profile: GameProfile
    ) : Player(level, profile) {
        override fun gameMode(): GameType = GameType.SURVIVAL
    }
}
