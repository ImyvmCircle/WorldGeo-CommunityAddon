package com.imyvm.community.entrypoint.screen.inner_community.council

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.council.runOpenCouncilVoteCreationMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.util.Translator
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CouncilMenu(
    syncId: Int,
    val community: Community,
    val player: ServerPlayerEntity,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = Text.of("${community.generateCommunityMark()} - Council"),
    runBack = runBack
) {
    init {
        addCouncilButtons()
    }

    private fun addCouncilButtons() {
        val remainingVotes = CommunityConfig.COUNCIL_MAX_VOTES_PER_DAY.value - 
            community.council.getVotesCreatedToday()
        val activeVotesCount = community.council.getActiveVotes().size
        val councilMemberCount = community.council.getCouncilMemberCount(community)

        val createVoteStack = ItemStack(Items.WRITABLE_BOOK)
        createVoteStack.set(DataComponentTypes.LORE, net.minecraft.component.type.LoreComponent(listOf(
            Text.of("Cost: ${CommunityConfig.COUNCIL_VOTE_CREATION_COST.value} assets"),
            Text.of("Remaining Today: $remainingVotes")
        )))
        addButton(
            slot = 10,
            itemStack = createVoteStack,
            name = Translator.tr("ui.council.button.create_vote")?.string ?: "Create Vote"
        ) {
            runOpenCouncilVoteCreationMenu(player, community, runBack)
        }

        val viewVotesStack = ItemStack(Items.PAPER)
        viewVotesStack.set(DataComponentTypes.LORE, net.minecraft.component.type.LoreComponent(listOf(
            Text.of("Active: $activeVotesCount"),
            Text.of("Total: ${community.council.voteSet.size}")
        )))
        addButton(
            slot = 11,
            itemStack = viewVotesStack,
            name = Translator.tr("ui.council.button.view_votes")?.string ?: "View Votes"
        ) {
            runOpenVoteListMenu(player, community, runBack)
        }

        val membersStack = ItemStack(Items.PLAYER_HEAD)
        membersStack.set(DataComponentTypes.LORE, net.minecraft.component.type.LoreComponent(listOf(
            Text.of("Members: $councilMemberCount")
        )))
        addButton(
            slot = 12,
            itemStack = membersStack,
            name = Translator.tr("ui.council.button.members")?.string ?: "Council Members"
        ) {
            runOpenCouncilMemberListMenu(player, community, runBack)
        }

        val infoStack = ItemStack(Items.BOOK)
        infoStack.set(DataComponentTypes.LORE, net.minecraft.component.type.LoreComponent(listOf(
            Text.of("Active Votes: $activeVotesCount"),
            Text.of("Remaining Votes Today: $remainingVotes"),
            Text.of("Council Members: $councilMemberCount"),
            Text.of("Vote Duration: ${CommunityConfig.COUNCIL_VOTE_DURATION_HOURS.value}h"),
            Text.of("Vote Cost: ${CommunityConfig.COUNCIL_VOTE_CREATION_COST.value} assets")
        )))
        addButton(
            slot = 13,
            itemStack = infoStack,
            name = Translator.tr("ui.council.button.info")?.string ?: "Council Info"
        ) {}
    }
}

fun runOpenVoteListMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        VoteListMenu(syncId, community, player, 0, runBack)
    }
}

fun runOpenCouncilMemberListMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CouncilMemberListMenu(syncId, community, player, 0, runBack)
    }
}
