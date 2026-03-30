package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.createPlayerHeadItemStack
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
import com.mojang.authlib.GameProfile
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class AdministrationAuditListMenu(
    syncId: Int,
    private val community: Community,
    private val playerExecutor: ServerPlayer,
    page: Int,
    private val runBackCommunityOperationMenu: ((ServerPlayer) -> Unit)
): AbstractListMenu(
    syncId,
    menuTitle = generateMenuTitle(community),
    page = page,
    runBackCommunityOperationMenu
) {

    private val playersPerPage = 35
    private val startSlot = 10

    init {
        val applicants = community.member.entries.filter { it.value.basicRoleType.name == "APPLICANT" }
        if (applicants.isEmpty()) {
            addButton(
                slot = 10,
                name = Translator.tr("ui.admin.audit_list.no_requests").string ?: "No Audit Requests",
                item = Items.DARK_OAK_SIGN
            ) {}
        } else {
            renderList(applicants, playersPerPage, startSlot) { applicant, slot, _ ->
                val uuid = applicant.key
                val name = UtilApi.getPlayerName(playerExecutor.level().server, uuid)
                val objectProfile = UtilApi.getPlayerProfile(playerExecutor.level().server, uuid)
                
                if (objectProfile != null) {
                    addButton(
                        slot = slot,
                        name = name,
                        itemStack = createPlayerHeadItemStack(name, uuid)
                    ) { runOpenAuditMemberMenu(objectProfile) }
                }
            }
        }

        handlePageWithSize(applicants.size, playersPerPage)
    }

    override fun openNewPage(player: ServerPlayer, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            AdministrationAuditListMenu(
                syncId,
                community = community,
                playerExecutor = playerExecutor,
                page = newPage,
                runBackCommunityOperationMenu = runBackCommunityOperationMenu
            )
        }
    }

    private fun runOpenAuditMemberMenu(objectProfile: GameProfile){
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            AdministrationAuditMenu(
                syncId,
                community = community,
                playerExecutor = playerExecutor,
                playerObject = objectProfile
            ) {
                CommunityMenuOpener.open(playerExecutor) { newSyncId ->
                    AdministrationAuditListMenu(
                        newSyncId,
                        community = community,
                        playerExecutor = playerExecutor,
                        page = page,
                        runBackCommunityOperationMenu = runBackCommunityOperationMenu
                    )
                }
            }
        }
    }

    companion object {
        fun generateMenuTitle(community: Community): Component =
            Component.literal(community.generateCommunityMark() + "ui.admin.audit_list.title")
    }
}