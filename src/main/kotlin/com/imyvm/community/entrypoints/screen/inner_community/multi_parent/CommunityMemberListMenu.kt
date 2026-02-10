package com.imyvm.community.entrypoints.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runCommunityOpenMember
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.entrypoints.screen.component.createPlayerHeadItemStack
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityMemberListMenu(
    syncId: Int,
    val community: Community,
    val playerExecutor: ServerPlayerEntity,
    page: Int = 0,
    val runBack : ((ServerPlayerEntity) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = generateCommunityMemberListMenuTitle(community),
    page = page,
    runBack = runBack
) {

    private val playersPerPage = 35
    private val startSlot = 10
    private val playersInPageZero = playersPerPage - 2 * 7
    private val startSlotInPageZero = startSlot + 2 * 9 + 2

    init {
        if (page == 0) {
            addOwnerButton()
            addAdminButtons()
            addMembersForPage0()
        } else {
            addMembersForOtherPages()
        }

        handlePageWithSize(community.getMemberUUIDs().size, playersPerPage)
    }

    private fun addOwnerButton() {
        addButton(
            slot = 10,
            name = (Translator.tr("ui.community.operation.member_list.owner")?.string ?: "Owner") + ":",
            item = Items.COMMAND_BLOCK
        ) {}

        val ownerUUID = community.getOwnerUUID()
        val ownerName = UtilApi.getPlayerName(playerExecutor, ownerUUID)
        addButton(
            slot = 12,
            name = ownerName,
            itemStack = createPlayerHeadItemStack(ownerName, ownerUUID!!)
        ) { runCommunityOpenMember(community, ownerUUID, playerExecutor, runBack) }
    }

    private fun addAdminButtons() {
        addButton(
            slot = 19,
            name = (Translator.tr("ui.community.operation.member_list.admin")?.string ?: "Admins") + ":",
            item = Items.COMMAND_BLOCK_MINECART
        ) {}

        val adminUUIDs = community.getAdminUUIDs()
        for ((index, uuid) in adminUUIDs.withIndex()) {
            val adminName = UtilApi.getPlayerName(playerExecutor, uuid)
            addButton(
                slot = 21 + index,
                name = adminName,
                itemStack = createPlayerHeadItemStack(adminName, uuid)
            ) { runCommunityOpenMember(community, uuid, playerExecutor, runBack) }
        }
    }

    private fun addMembersForPage0() {
        addButton(
            slot = 28,
            name = (Translator.tr("ui.community.operation.member_list.member")?.string ?: "Members") + ":",
            item = Items.VILLAGER_SPAWN_EGG
        ) {}

        val memberUUIDs = community.getMemberUUIDs().take(playersInPageZero)
        renderList(memberUUIDs, playersInPageZero, startSlotInPageZero) { uuid, slot, _ ->
            val memberName = UtilApi.getPlayerName(playerExecutor, uuid)
            addButton(
                slot = slot,
                name = memberName,
                itemStack = createPlayerHeadItemStack(memberName, uuid)
            ) { runCommunityOpenMember(community, uuid, playerExecutor, runBack) }
        }
    }

    private fun addMembersForOtherPages() {
        val memberUUIDs = community.getMemberUUIDs()
            .drop((page - 1) * playersPerPage + playersInPageZero)
            .take(playersPerPage)
        
        renderList(memberUUIDs, playersPerPage, startSlot) { uuid, slot, _ ->
            val memberName = UtilApi.getPlayerName(playerExecutor, uuid)
            addButton(
                slot = slot,
                name = memberName,
                itemStack = createPlayerHeadItemStack(memberName, uuid)
            ) { runCommunityOpenMember(community, uuid, playerExecutor, runBack) }
        }
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityMemberListMenu(syncId, community, player, newPage, runBack)
        }
    }

    override fun calculateTotalPages(listSize: Int): Int {
        return ((listSize + 2 * 7 + 2 + playersPerPage - 1) / playersPerPage)
    }

    companion object {
        fun generateCommunityMemberListMenuTitle(community: Community): Text =
            Text.of(community.generateCommunityMark()
                    + (Translator.tr("ui.community.operation.member_list.title.component")?.string ?: "- Member List")
            )
    }
}