package com.imyvm.community.entrypoint.screen.inner_community.affairs.assets

import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenDonationMenu
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenDonorListMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityAssetsMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val community: Community,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.assets.title"),
    runBack = runBack
) {
    init {
        addAssetDisplay()
        addDonateButton()
        addDonorListButton()
    }

    private fun addAssetDisplay() {
        val totalAssets = community.getTotalAssets()
        val assetsFormatted = "%.2f".format(totalAssets / 100.0)

        addButton(
            slot = 13,
            itemStack = getLoreButton(
                ItemStack(Items.GOLD_BLOCK),
                listOf(Translator.tr("ui.community.assets.total.lore", assetsFormatted) ?: Text.of("§7$assetsFormatted"))
            ),
            name = Translator.tr("ui.community.assets.total")?.string ?: "Total Assets"
        ) {}
    }

    private fun addDonateButton() {
        addButton(
            slot = 20,
            name = Translator.tr("ui.community.assets.donate")?.string ?: "Donate",
            item = Items.EMERALD
        ) { runOpenDonationMenu(player, community, runBack) }
    }

    private fun addDonorListButton() {
        addButton(
            slot = 24,
            name = Translator.tr("ui.community.assets.donor_list")?.string ?: "Donor List",
            item = Items.WRITABLE_BOOK
        ) { runOpenDonorListMenu(player, community, runBack) }
    }
}