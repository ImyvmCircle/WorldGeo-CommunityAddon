package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.createPlayerHeadItemStack
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
import com.mojang.authlib.GameProfile
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class AdministrationAuditListMenu(
    syncId: Int,
    private val community: Community,
    private val playerExecutor: ServerPlayerEntity,
    page: Int,
    private val runBackCommunityOperationMenu: ((ServerPlayerEntity) -> Unit)
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
                name = Translator.tr("ui.community.administration.audit_list.no_requests")?.string ?: "No Audit Requests",
                item = Items.DARK_OAK_SIGN
            ) {}
        } else {
            renderList(applicants, playersPerPage, startSlot) { applicant, slot, _ ->
                val uuid = applicant.key
                val name = UtilApi.getPlayerName(playerExecutor.server, uuid)
                val objectProfile = UtilApi.getPlayerProfile(playerExecutor.server, uuid)
                
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

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
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
        fun generateMenuTitle(community: Community): Text =
            Text.of(community.generateCommunityMark() + "ui.community.administration.audit_list.title.component")
    }
}