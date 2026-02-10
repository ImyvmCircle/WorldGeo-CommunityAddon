package com.imyvm.community.entrypoints.screen.inner_community.council

import com.imyvm.community.application.interaction.screen.inner_community.council.runCastVote
import com.imyvm.community.application.interaction.screen.inner_community.council.runRetractVote
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*

class VoteDetailsMenu(
    syncId: Int,
    val community: Community,
    val player: ServerPlayerEntity,
    val voteId: UUID,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = Text.of("Vote Details"),
    runBack = runBack
) {
    init {
        val vote = community.council.getVote(voteId)
        if (vote != null) {
            displayVoteInfo(vote)
            displayVotingButtons(vote)
        } else {
            player.sendMessage(Text.of("Vote not found"), false)
        }
    }

    private fun displayVoteInfo(vote: com.imyvm.community.domain.community.council.CouncilVote) {
        val titleStack = ItemStack(Items.PAPER)
        titleStack.set(DataComponentTypes.LORE, LoreComponent(listOf(
            Text.of(""),
            Text.of(vote.description),
            Text.of(""),
            Text.of("Permission: ${vote.permission?.name ?: "NONE"}"),
            Text.of("Proposer: ${vote.proposerUUID}")
        )))
        addButton(
            slot = 4,
            itemStack = titleStack,
            name = vote.title
        ) {}

        val status = if (vote.isExpired()) "Expired" else "Active"
        val remainingTime = vote.getRemainingTime()
        val timeStr = if (remainingTime > 0) {
            val hours = remainingTime / (1000 * 60 * 60)
            val minutes = (remainingTime % (1000 * 60 * 60)) / (1000 * 60)
            "${hours}h ${minutes}m"
        } else {
            "Expired"
        }
        
        val statusStack = ItemStack(if (vote.isExpired()) Items.RED_WOOL else Items.YELLOW_WOOL)
        statusStack.set(DataComponentTypes.LORE, LoreComponent(listOf(
            Text.of("Status: $status"),
            Text.of("Remaining: $timeStr")
        )))
        addButton(
            slot = 10,
            itemStack = statusStack,
            name = Translator.tr("council.vote.status")?.string ?: "Status"
        ) {}

        val resultsStack = ItemStack(Items.BOOK)
        resultsStack.set(DataComponentTypes.LORE, LoreComponent(listOf(
            Text.of("Yea: ${vote.yeaVotes.size}"),
            Text.of("Nay: ${vote.nayVotes.size}"),
            Text.of("Result: ${if (vote.isPassed()) "PASSED" else "FAILED"}")
        )))
        addButton(
            slot = 11,
            itemStack = resultsStack,
            name = Translator.tr("council.vote.results")?.string ?: "Results"
        ) {}

        // Execution Data
        val executionStack = ItemStack(Items.WRITABLE_BOOK)
        executionStack.set(DataComponentTypes.LORE, LoreComponent(getExecutionDataLore(vote.executionData)))
        addButton(
            slot = 12,
            itemStack = executionStack,
            name = Translator.tr("council.vote.execution_data")?.string ?: "Action Details"
        ) {}
    }

    private fun displayVotingButtons(vote: com.imyvm.community.domain.community.council.CouncilVote) {
        if (vote.isExpired()) {
            val status = if (vote.isEnacted == true) {
                if (vote.isPassed()) "✓ Executed" else "Failed"
            } else if (vote.isPassed()) {
                "Awaiting Execution"
            } else {
                "Failed"
            }
            
            val item = when {
                vote.isEnacted == true && vote.isPassed() -> Items.LIME_WOOL
                vote.isPassed() -> Items.GREEN_WOOL
                else -> Items.RED_WOOL
            }
            
            addButton(
                slot = 22,
                name = status,
                item = item
            ) {}
            return
        }

        val hasVoted = vote.hasVoted(player.uuid)
        val currentVote = if (hasVoted) {
            if (player.uuid in vote.yeaVotes) true else false
        } else null

        val yeaStack = ItemStack(if (currentVote == true) Items.LIME_CONCRETE else Items.LIME_WOOL)
        if (currentVote == true) {
            yeaStack.set(DataComponentTypes.LORE, LoreComponent(listOf(Text.of("Your current vote"))))
        }
        addButton(
            slot = 20,
            itemStack = yeaStack,
            name = Translator.tr("council.vote.button.yea")?.string ?: "Vote Yea"
        ) {
            runCastVote(player, community, voteId, true, runBack)
        }

        val nayStack = ItemStack(if (currentVote == false) Items.RED_CONCRETE else Items.RED_WOOL)
        if (currentVote == false) {
            nayStack.set(DataComponentTypes.LORE, LoreComponent(listOf(Text.of("Your current vote"))))
        }
        addButton(
            slot = 21,
            itemStack = nayStack,
            name = Translator.tr("council.vote.button.nay")?.string ?: "Vote Nay"
        ) {
            runCastVote(player, community, voteId, false, runBack)
        }

        if (hasVoted) {
            addButton(
                slot = 22,
                name = Translator.tr("council.vote.button.retract")?.string ?: "Retract Vote",
                item = Items.BARRIER
            ) {
                runRetractVote(player, community, voteId, runBack)
            }
        }
    }

    private fun getExecutionDataLore(data: com.imyvm.community.domain.community.council.VoteExecutionData?): List<Text> {
        return when (data) {
            is com.imyvm.community.domain.community.council.VoteExecutionData.ChangeJoinPolicy -> listOf(
                Text.of("Action: Change Join Policy"),
                Text.of("New Policy: ${data.newPolicy}")
            )
            is com.imyvm.community.domain.community.council.VoteExecutionData.ManageMember -> listOf(
                Text.of("Action: ${data.action.name}"),
                Text.of("Target: ${data.targetUUID}")
            )
            is com.imyvm.community.domain.community.council.VoteExecutionData.AuditApplication -> listOf(
                Text.of("Action: ${if (data.accept) "Approve" else "Reject"} Application"),
                Text.of("Applicant: ${data.applicantUUID}")
            )
            null -> listOf(Text.of("No execution data"))
        }
    }
}
