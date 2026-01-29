package com.imyvm.community.entrypoints.screen.inner_community.operation_only

import com.imyvm.community.application.interaction.common.onCommunityRegionInteraction
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractRenameMenuAnvil
import com.imyvm.community.entrypoints.screen.inner_community.CommunityOperationMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityOperationRenameMenuAnvil(
    player: ServerPlayerEntity,
    private val community: Community,
    private val runBackGrandfather: ((ServerPlayerEntity) -> Unit)
): AbstractRenameMenuAnvil(
    player = player,
    initialName = community.regionNumberId?.let { RegionDataApi.getRegion(it)?.name } ?: "Unknown Name"
) {
    override fun processRenaming(finalName: String) {
        if (renameCommunity(player, community, finalName) == 0) {
            player.closeHandledScreen()
            return
        }
        reopenOperationMenuWithNewName(player, community)
    }

    override fun getMenuTitle(): Text{
        return Translator.tr("ui.community.operation.rename.title") ?: Text.of("Rename Community")
    }

    private fun renameCommunity(player: ServerPlayerEntity, community: Community, newName: String): Int {
        return onCommunityRegionInteraction(player, community) { p, _, region ->
            PlayerInteractionApi.renameRegion(p, region, newName)
        }
    }

    private fun reopenOperationMenuWithNewName(player: ServerPlayerEntity, community: Community) {
        CommunityMenuOpener.open(player) { newSyncId ->
            CommunityOperationMenu(newSyncId, community, player, runBack = runBackGrandfather)
        }
    }

}