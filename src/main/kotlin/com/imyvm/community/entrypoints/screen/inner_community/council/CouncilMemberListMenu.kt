package com.imyvm.community.entrypoints.screen.inner_community.council

import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CouncilMemberListMenu(
    syncId: Int,
    val community: Community,
    val player: ServerPlayerEntity,
    val page: Int,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = Text.of("${community.generateCommunityMark()} - Council Members"),
    runBack = runBack
) {
    private val itemsPerPage = 45
    
    init {
        displayMembers()
        addNavigationButtons()
    }

    private fun displayMembers() {
        val councilMembers = community.member.entries.filter { 
            it.value.isCouncilMember || it.value.governorship >= 0 
        }
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, councilMembers.size)
        val pageMembers = councilMembers.toList().subList(startIndex, endIndex)

        pageMembers.forEachIndexed { index, (uuid, member) ->
            val memberStack = ItemStack(Items.PLAYER_HEAD)
            memberStack.set(DataComponentTypes.LORE, net.minecraft.component.type.LoreComponent(listOf(
                Text.of("Role: ${member.basicRoleType}"),
                Text.of("Governorship: ${member.governorship}"),
                Text.of("Joined: ${member.joinedTime}")
            )))
            addButton(
                slot = index,
                itemStack = memberStack,
                name = uuid.toString()
            ) {}
        }
    }

    private fun addNavigationButtons() {
        val totalMembers = community.member.entries.count { 
            it.value.isCouncilMember || it.value.governorship >= 0 
        }
        val totalPages = (totalMembers + itemsPerPage - 1) / itemsPerPage

        if (page > 0) {
            addButton(
                slot = 45,
                name = Translator.tr("ui.button.previous_page")?.string ?: "Previous Page",
                item = Items.ARROW
            ) {
                com.imyvm.community.application.interaction.screen.CommunityMenuOpener.open(player) { syncId ->
                    CouncilMemberListMenu(syncId, community, player, page - 1, runBack)
                }
            }
        }

        if (page < totalPages - 1) {
            addButton(
                slot = 53,
                name = Translator.tr("ui.button.next_page")?.string ?: "Next Page",
                item = Items.ARROW
            ) {
                com.imyvm.community.application.interaction.screen.CommunityMenuOpener.open(player) { syncId ->
                    CouncilMemberListMenu(syncId, community, player, page + 1, runBack)
                }
            }
        }
    }
}
