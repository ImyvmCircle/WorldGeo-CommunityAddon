package com.imyvm.community.entrypoints.screen.inner_community.affairs.assets

import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.entrypoints.screen.component.createPlayerHeadItemStack
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getFormattedMillsHour
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*

class DonorDetailsMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val community: Community,
    val donorUUID: UUID,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.assets.donor_details.title"),
    runBack = runBack
) {
    init {
        addDonorInfoDisplay()
        addTurnoverDisplay()
    }

    private fun addDonorInfoDisplay() {
        val donorName = UtilApi.getPlayerName(player, donorUUID)
        val memberAccount = community.member[donorUUID]
        val totalDonation = memberAccount?.getTotalDonation() ?: 0
        val donationFormatted = "%.2f".format(totalDonation / 100.0)

        addButton(
            slot = 4,
            itemStack = getLoreButton(
                createPlayerHeadItemStack(donorName, donorUUID),
                listOf(Text.literal("§7Total Donation: $donationFormatted"))
            ),
            name = donorName
        ) {}
    }

    private fun addTurnoverDisplay() {
        val memberAccount = community.member[donorUUID] ?: return
        val turnoverList = memberAccount.turnover.sortedByDescending { it.timestamp }

        val startSlot = 19
        val slots = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)

        turnoverList.take(slots.size).forEachIndexed { index, turnover ->
            val amountFormatted = "%.2f".format(turnover.amount / 100.0)
            val timeFormatted = getFormattedMillsHour(turnover.timestamp)

            addButton(
                slot = slots[index],
                itemStack = getLoreButton(
                    ItemStack(Items.PAPER),
                    listOf(
                        Text.literal("§7Amount: $amountFormatted"),
                        Text.literal("§7Time: $timeFormatted")
                    )
                ),
                name = Translator.tr("ui.community.assets.turnover")?.string ?: "Donation"
            ) {}
        }
    }
}
