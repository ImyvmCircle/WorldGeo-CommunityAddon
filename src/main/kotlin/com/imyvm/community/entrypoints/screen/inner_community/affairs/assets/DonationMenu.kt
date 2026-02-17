package com.imyvm.community.entrypoints.screen.inner_community.affairs.assets

import com.imyvm.community.application.interaction.screen.inner_community.affairs.onDonateConfirm
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class DonationMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val community: Community,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.assets.donate.title"),
    runBack = runBack
) {
    init {
        addDonationOptions()
    }

    private fun addDonationOptions() {
        val amounts = listOf(100L, 500L, 1000L, 5000L, 10000L, 50000L)
        val slots = listOf(10, 11, 12, 19, 20, 21)

        amounts.forEachIndexed { index, amount ->
            val amountFormatted = "%.2f".format(amount / 100.0)
            addButton(
                slot = slots[index],
                itemStack = getLoreButton(
                    ItemStack(Items.GOLD_INGOT),
                    listOf(Translator.tr("ui.community.assets.donate.amount.lore", amountFormatted) ?: Text.of("§7$amountFormatted"))
                ),
                name = Translator.tr("ui.community.assets.donate.amount")?.string ?: "Donate"
            ) {
                onDonateConfirm(player, community, amount, runBack)
            }
        }
    }
}
