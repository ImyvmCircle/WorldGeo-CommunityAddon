package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.common.helper.checkPlayerMembershipPreCreation
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationSelectionMenu
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun runOpenCommunityCreation(
    player: ServerPlayerEntity,
    runBackTerritoryMenu: (ServerPlayerEntity) -> Unit
) {
    if (!checkPlayerMembershipPreCreation(player)) return
    val defaultTitle = generateNewCommunityTitle()
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationSelectionMenu(
            syncId,
            currentName = defaultTitle,
            playerExecutor = player,
            runBack = runBackTerritoryMenu
        )
    }
}

private fun generateNewCommunityTitle(): String {
    val index = CommunityDatabase.communities.size + 1
    val defaultTitle = Translator.tr("ui.create.title")?.string ?: "New-Community"
    return generateSequence(index) { it + 1 }
        .map { "$defaultTitle$it" }
        .first { title -> CommunityDatabase.communities.none { it.getRegion()?.name == title } }
}
