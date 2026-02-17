package com.imyvm.community.entrypoints.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.common.onCommunityRegionInteraction
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractRenameMenuAnvil
import com.imyvm.community.entrypoints.screen.inner_community.CommunityAdministrationMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class AdministrationRenameMenuAnvil(
    player: ServerPlayerEntity,
    private val community: Community,
    private val runBackGrandfather: ((ServerPlayerEntity) -> Unit)
): AbstractRenameMenuAnvil(
    player = player,
    initialName = community.regionNumberId?.let { RegionDataApi.getRegion(it)?.name } ?: "Unknown Name"
) {
    override fun processRenaming(finalName: String) {
        val oldName = community.regionNumberId?.let { com.imyvm.iwg.inter.api.RegionDataApi.getRegion(it)?.name } ?: "Unknown"
        
        if (renameCommunity(player, community, finalName) == 0) {
            player.closeHandledScreen()
            return
        }
        
        val notification = com.imyvm.community.util.Translator.tr(
            "community.notification.renamed",
            oldName,
            finalName,
            player.name.string
        ) ?: net.minecraft.text.Text.literal("Community renamed from '$oldName' to '$finalName' by ${player.name.string}")
        com.imyvm.community.application.interaction.common.notifyOfficials(community, player.server, notification, player)
        
        com.imyvm.community.infra.CommunityDatabase.save()
        reopenAdministrationMenuWithNewName(player, community)
    }

    override fun getMenuTitle(): Text{
        return Translator.tr("ui.community.administration.rename.title") ?: Text.of("Rename Community")
    }

    private fun renameCommunity(player: ServerPlayerEntity, community: Community, newName: String): Int {
        return onCommunityRegionInteraction(player, community) { p, _, region ->
            PlayerInteractionApi.renameRegion(p, region, newName)
        }
    }

    private fun reopenAdministrationMenuWithNewName(player: ServerPlayerEntity, community: Community) {
        CommunityMenuOpener.open(player) { newSyncId ->
            CommunityAdministrationMenu(newSyncId, community, player, runBack = runBackGrandfather)
        }
    }

}