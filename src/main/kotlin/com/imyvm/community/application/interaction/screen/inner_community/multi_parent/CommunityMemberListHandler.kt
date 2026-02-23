package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityMemberListMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.element.CommunityMemberMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun runCommunityOpenMember(
    community: Community,
    playerObjectUuid: UUID,
    playerExecutor: ServerPlayerEntity,
    runBackGrandfather: (ServerPlayerEntity) -> Unit
){
    val playerObjectProfile = UtilApi.getPlayerProfile(playerExecutor, playerObjectUuid)
    if (playerObjectProfile != null) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            CommunityMemberMenu(syncId, community, playerObjectProfile, playerExecutor) {
                runBackToMemberListMenu(
                    playerExecutor,
                    community,
                    runBackGrandfather
                )
            }
        }
    } else {
        playerExecutor.closeHandledScreen()
        playerExecutor.sendMessage(Translator.tr("ui.community.member_list.error.offline_member"))
    }
}

private fun runBackToMemberListMenu(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        CommunityMemberListMenu(syncId, community, playerExecutor, runBack = runBackGrandfatherMenu)
    }
}