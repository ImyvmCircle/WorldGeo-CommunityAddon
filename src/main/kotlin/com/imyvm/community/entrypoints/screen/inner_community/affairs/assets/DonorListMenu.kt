package com.imyvm.community.entrypoints.screen.inner_community.affairs.assets

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenDonorDetailsMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.entrypoints.screen.component.createPlayerHeadItemStack
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class DonorListMenu(
    syncId: Int,
    val community: Community,
    val playerExecutor: ServerPlayerEntity,
    page: Int = 0,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.community.assets.donor_list.title"),
    page = page,
    runBack = runBack
) {

    private val donorsPerPage = 45
    private val startSlot = 0

    init {
        val donorList = community.getDonorList()
        renderList(donorList, donorsPerPage, startSlot) { donorUUID, slot, _ ->
            val donorName = UtilApi.getPlayerName(playerExecutor, donorUUID)
            val memberAccount = community.member[donorUUID]
            val totalDonation = memberAccount?.getTotalDonation() ?: 0
            val donationFormatted = "%.2f".format(totalDonation / 100.0)

            addButton(
                slot = slot,
                itemStack = getLoreButton(
                    createPlayerHeadItemStack(donorName, donorUUID),
                    listOf(Translator.tr("ui.community.assets.donor_list.lore.total", donationFormatted) ?: Text.of("§7Total: $donationFormatted"))
                ),
                name = donorName
            ) {
                runOpenDonorDetailsMenu(playerExecutor, community, donorUUID, runBack)
            }
        }
        handlePageWithSize(donorList.size, donorsPerPage)
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            DonorListMenu(syncId, community, playerExecutor, newPage, runBack)
        }
    }
}
