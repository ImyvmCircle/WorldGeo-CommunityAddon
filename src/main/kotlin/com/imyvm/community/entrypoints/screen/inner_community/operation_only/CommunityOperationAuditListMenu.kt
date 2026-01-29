package com.imyvm.community.entrypoints.screen.inner_community.operation_only

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.MemberAccount
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.entrypoints.screen.component.createPlayerHeadItemStack
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
import com.mojang.authlib.GameProfile
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*

class CommunityOperationAuditListMenu(
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
    private val endSlot = 44

    init {
        val applicants = community.member.entries.filter { it.value.basicRoleType.name == "APPLICANT" }
        if (applicants.isEmpty()) {
            addButton(
                slot = 10,
                name = Translator.tr("ui.community.operation.audit_list.no_requests")?.string ?: "No Audit Requests",
                item = Items.DARK_OAK_SIGN
            ) {}
        } else {
            addApplicantButtons(applicants)
        }

        handlePage(applicants.size)
    }

    override fun calculateTotalPages(listSize: Int): Int {
        return (listSize / playersPerPage) + 1
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityOperationAuditListMenu(
                syncId,
                community = community,
                playerExecutor = playerExecutor,
                page = newPage,
                runBackCommunityOperationMenu = runBackCommunityOperationMenu
            )
        }
    }

    private fun addApplicantButtons(applicants: List<Map.Entry<UUID, MemberAccount>>) {
        val applicantInPage = applicants.drop(page * playersPerPage).take(playersPerPage)

        var slot = startSlot
        val server = playerExecutor.server
        for (applicant in applicantInPage) {
            val uuid = applicant.key
            val name = UtilApi.getPlayerName(server, uuid)
            val objectProfile = UtilApi.getPlayerProfile(server, uuid) ?: continue
            addButton(
                slot = slot,
                name = name,
                itemStack = createPlayerHeadItemStack(name, uuid)
            ) { runOpenAuditMemberMenu(objectProfile) }
            slot = super.incrementSlotIndex(slot)
            if (slot > endSlot) break
        }
    }

    private fun runOpenAuditMemberMenu(objectProfile: GameProfile){
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            CommunityOperationAuditMenu(
                syncId,
                community = community,
                playerExecutor = playerExecutor,
                playerObject = objectProfile
            ) {
                CommunityMenuOpener.open(playerExecutor) { newSyncId ->
                    CommunityOperationAuditListMenu(
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
            Text.of(community.generateCommunityMark() + "ui.community.operation.audit_list.title.component")
    }
}