package com.imyvm.community.entrypoint.screen.inner_community.affairs.assets

import com.imyvm.community.application.interaction.screen.inner_community.affairs.onDonateConfirm
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class DonationMenu(
    syncId: Int,
    val player: ServerPlayer,
    val community: Community,
    val runBack: ((ServerPlayer) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.assets.donate.title"),
    runBack = runBack
) {
    init {
        addDonationOptions()
    }

    private fun addDonationOptions() {
        val amounts = listOf(100L, 500L, 1000L, 5000L, 10000L, 50000L, 100000L, 200000L, 500000L, 1000000L)
        val slots = listOf(10, 11, 12, 13, 14, 19, 20, 21, 22, 23)

        amounts.forEachIndexed { index, amount ->
            val amountFormatted = "%.2f".format(amount / 100.0)
            addButton(
                slot = slots[index],
                itemStack = getLoreButton(
                    ItemStack(Items.GOLD_INGOT),
                    listOf(Translator.tr("ui.community.assets.donate.amount.lore", amountFormatted) ?: Component.literal("§7$amountFormatted"))
                ),
                name = Translator.tr("ui.community.assets.donate.amount").string ?: "Donate"
            ) {
                onDonateConfirm(player, community, amount, runBack)
            }
        }
    }
}
