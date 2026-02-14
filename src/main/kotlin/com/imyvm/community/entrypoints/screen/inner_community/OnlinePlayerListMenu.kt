package com.imyvm.community.entrypoints.screen.inner_community

import com.imyvm.community.application.interaction.common.sendInvitation
import com.imyvm.community.application.interaction.common.validateInvitationSender
import com.imyvm.community.application.interaction.common.validateInvitationTarget
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.entrypoints.screen.component.createPlayerHeadItemStack
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class OnlinePlayerListMenu private constructor(
    syncId: Int,
    val community: Community,
    val playerExecutor: ServerPlayerEntity,
    page: Int = 0,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.community.invite.online_players"),
    page = page,
    runBack = runBack
) {
    private val playersPerPage = 45
    private val startSlot = 0

    companion object {
        fun create(
            syncId: Int,
            community: Community,
            playerExecutor: ServerPlayerEntity,
            page: Int = 0,
            runBack: ((ServerPlayerEntity) -> Unit)
        ): OnlinePlayerListMenu? {
            if (!validateInvitationSender(playerExecutor, community)) {
                return null
            }
            return OnlinePlayerListMenu(syncId, community, playerExecutor, page, runBack)
        }
    }

    init {
        val onlinePlayers = getInvitablePlayers()
        
        renderList(onlinePlayers, playersPerPage, startSlot) { player, slot, _ ->
            addButton(
                slot = slot,
                name = player.name.string,
                itemStack = createPlayerHeadItemStack(player.name.string, player.uuid)
            ) {
                if (validateInvitationTarget(playerExecutor, player, community)) {
                    sendInvitation(playerExecutor, player, community)
                }
                playerExecutor.closeHandledScreen()
            }
        }

        handlePageWithSize(onlinePlayers.size, playersPerPage)
    }

    private fun getInvitablePlayers(): List<ServerPlayerEntity> {
        return playerExecutor.server.playerManager.playerList
            .filter { it.uuid != playerExecutor.uuid }
            .filter { !community.member.containsKey(it.uuid) }
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            create(syncId, community, player, newPage, runBack)!!
        }
    }
}
