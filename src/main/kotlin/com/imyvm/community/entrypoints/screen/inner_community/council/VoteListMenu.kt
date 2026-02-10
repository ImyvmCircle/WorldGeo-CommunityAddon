package com.imyvm.community.entrypoints.screen.inner_community.council

import com.imyvm.community.application.interaction.screen.inner_community.council.runOpenVoteDetailsMenu
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.council.CouncilVote
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class VoteListMenu(
    syncId: Int,
    val community: Community,
    val player: ServerPlayerEntity,
    val page: Int,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = Text.of("${community.generateCommunityMark()} - Votes"),
    runBack = runBack
) {
    private val itemsPerPage = 45
    
    init {
        displayVotes()
        addNavigationButtons()
    }

    private fun displayVotes() {
        val allVotes = community.council.voteSet.sortedByDescending { it.proposeTime }
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, allVotes.size)
        val pageVotes = allVotes.subList(startIndex, endIndex)

        pageVotes.forEachIndexed { index, vote ->
            addVoteButton(index, vote)
        }
    }

    private fun addVoteButton(slot: Int, vote: CouncilVote) {
        val status = when {
            vote.isEnacted == true && vote.isPassed() -> "✓ Executed"
            vote.isExpired() && vote.isPassed() -> "✓ Passed"
            vote.isExpired() && !vote.isPassed() -> "✗ Failed"
            else -> "⧗ Active"
        }

        val item = when {
            vote.isEnacted == true -> Items.LIME_WOOL
            vote.isExpired() && vote.isPassed() -> Items.GREEN_WOOL
            vote.isExpired() && !vote.isPassed() -> Items.RED_WOOL
            else -> Items.YELLOW_WOOL
        }

        val remainingTime = vote.getRemainingTime()
        val timeStr = if (remainingTime > 0) {
            val hours = remainingTime / (1000 * 60 * 60)
            val minutes = (remainingTime % (1000 * 60 * 60)) / (1000 * 60)
            "${hours}h ${minutes}m"
        } else {
            "Expired"
        }

        val voteStack = ItemStack(item)
        voteStack.set(DataComponentTypes.LORE, net.minecraft.component.type.LoreComponent(listOf(
            Text.of("Yea: ${vote.yeaVotes.size} | Nay: ${vote.nayVotes.size}"),
            Text.of("Remaining: $timeStr")
        )))
        
        addButton(
            slot = slot,
            itemStack = voteStack,
            name = "$status: ${vote.title}"
        ) {
            runOpenVoteDetailsMenu(player, community, vote.id, runBack)
        }
    }

    private fun addNavigationButtons() {
        val totalVotes = community.council.voteSet.size
        val totalPages = (totalVotes + itemsPerPage - 1) / itemsPerPage

        if (page > 0) {
            addButton(
                slot = 45,
                name = Translator.tr("ui.button.previous_page")?.string ?: "Previous Page",
                item = Items.ARROW
            ) {
                com.imyvm.community.application.interaction.screen.inner_community.council.runOpenVoteListMenu(
                    player, community, page - 1, runBack
                )
            }
        }

        if (page < totalPages - 1) {
            addButton(
                slot = 53,
                name = Translator.tr("ui.button.next_page")?.string ?: "Next Page",
                item = Items.ARROW
            ) {
                com.imyvm.community.application.interaction.screen.inner_community.council.runOpenVoteListMenu(
                    player, community, page + 1, runBack
                )
            }
        }
    }
}
