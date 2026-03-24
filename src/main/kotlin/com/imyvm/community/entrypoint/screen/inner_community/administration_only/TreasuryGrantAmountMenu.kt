package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.inner_community.administration_only.runGrantCoinsToTarget
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class TreasuryGrantAmountMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val sourceCommunity: Community,
    val targetCommunity: Community,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr(
        "ui.treasury_grant.amount.title",
        sourceCommunity.generateCommunityMark(),
        targetCommunity.generateCommunityMark()
    ) ?: Text.literal("Grant: ${sourceCommunity.generateCommunityMark()} → ${targetCommunity.generateCommunityMark()}"),
    runBack = runBack
) {
    init {
        addAmountOptions()
    }

    private fun addAmountOptions() {
        val amounts = listOf(100L, 500L, 1000L, 5000L, 10000L, 50000L, 100000L, 200000L, 500000L, 1000000L)
        val slots = listOf(10, 11, 12, 13, 14, 19, 20, 21, 22, 23)

        amounts.forEachIndexed { index, amount ->
            val amountFormatted = "%.2f".format(amount / 100.0)
            addButton(
                slot = slots[index],
                itemStack = getLoreButton(
                    ItemStack(Items.GOLD_INGOT),
                    listOf(Translator.tr("ui.treasury_grant.amount.lore", amountFormatted) ?: Text.of("§7$amountFormatted"))
                ),
                name = Translator.tr("ui.treasury_grant.amount")?.string ?: "Grant Amount"
            ) {
                runGrantCoinsToTarget(player, sourceCommunity, targetCommunity, amount, runBack)
            }
        }
    }
}
