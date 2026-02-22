package com.imyvm.community.application.interaction.screen.inner_community.council

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdministrationPermission
import com.imyvm.community.domain.model.community.council.CouncilVote
import com.imyvm.community.domain.model.community.council.VoteExecutionData
import com.imyvm.community.entrypoint.screen.inner_community.council.CouncilMenu
import com.imyvm.community.entrypoint.screen.inner_community.council.VoteDetailsMenu
import com.imyvm.community.entrypoint.screen.inner_community.council.VoteListMenu
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*

fun runOpenCouncilMenu(player: ServerPlayerEntity, community: Community, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canAccessCouncil(player, community) }
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            CouncilMenu(syncId, community, player, runBack)
        }
    }
}

fun runOpenVoteListMenu(player: ServerPlayerEntity, community: Community, page: Int = 0, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        VoteListMenu(syncId, community, player, page, runBack)
    }
}

fun runOpenVoteDetailsMenu(player: ServerPlayerEntity, community: Community, voteId: UUID, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        VoteDetailsMenu(syncId, community, player, voteId, runBack)
    }
}

fun runCastVote(player: ServerPlayerEntity, community: Community, voteId: UUID, support: Boolean, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canAccessCouncil(player, community) }
    ) {
        val vote = community.council.getVote(voteId)
        if (vote == null) {
            player.sendMessage(Translator.tr("council.vote.not_found") ?: Text.of("Vote not found"), false)
            return@executeWithPermission
        }

        if (vote.isExpired()) {
            player.sendMessage(Translator.tr("council.vote.expired") ?: Text.of("Vote has expired"), false)
            return@executeWithPermission
        }

        vote.castVote(player.uuid, support, community.council, community)
        player.sendMessage(
            Translator.tr("council.vote.cast_success") ?: Text.of("Vote cast successfully"),
            false
        )
        runOpenVoteDetailsMenu(player, community, voteId, runBack)
    }
}

fun runRetractVote(player: ServerPlayerEntity, community: Community, voteId: UUID, runBack: (ServerPlayerEntity) -> Unit) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canAccessCouncil(player, community) }
    ) {
        val vote = community.council.getVote(voteId)
        if (vote == null) {
            player.sendMessage(Translator.tr("council.vote.not_found") ?: Text.of("Vote not found"), false)
            return@executeWithPermission
        }

        if (vote.isExpired()) {
            player.sendMessage(Translator.tr("council.vote.expired") ?: Text.of("Vote has expired"), false)
            return@executeWithPermission
        }

        vote.yeaVotes.remove(player.uuid)
        vote.nayVotes.remove(player.uuid)
        
        player.sendMessage(
            Translator.tr("council.vote.retracted") ?: Text.of("Vote retracted"),
            false
        )
        runOpenVoteDetailsMenu(player, community, voteId, runBack)
    }
}

fun runCreateVoteProposal(
    player: ServerPlayerEntity,
    community: Community,
    title: String,
    description: String,
    executionData: VoteExecutionData,
    permission: AdministrationPermission?,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        player,
        { CommunityPermissionPolicy.canAccessCouncil(player, community) }
    ) {
        if (!community.council.canCreateVoteToday()) {
            player.sendMessage(
                Translator.tr("council.vote.daily_limit_reached") ?: Text.of("Daily vote limit reached"),
                false
            )
            return@executeWithPermission
        }

        val cost = PricingConfig.COUNCIL_VOTE_CREATION_COST.value
        val totalAssets = community.getTotalAssets()
        if (totalAssets < cost) {
            player.sendMessage(
                Translator.tr("council.vote.insufficient_assets", cost) 
                    ?: Text.of("Insufficient community assets. Need: $cost, Have: $totalAssets"),
                false
            )
            return@executeWithPermission
        }

        val proposerAccount = community.member[player.uuid]
        if (proposerAccount != null) {
            val proposerDonations = proposerAccount.getTotalDonation()
            if (proposerDonations >= cost) {
                proposerAccount.turnover.add(
                    com.imyvm.community.domain.model.Turnover(
                        amount = -cost,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                player.sendMessage(
                    Text.of("You need at least $cost in personal donations to create a vote. You have: $proposerDonations"),
                    false
                )
                return@executeWithPermission
            }
        }

        val vote = CouncilVote(
            proposerUUID = player.uuid,
            executionData = executionData,
            permission = permission,
            title = title,
            description = description
        )
        community.council.addVote(vote)

        player.sendMessage(
            Translator.tr("council.vote.created") ?: Text.of("Vote created successfully. Cost: $cost assets"),
            false
        )
        runOpenVoteDetailsMenu(player, community, vote.id, runBack)
    }
}

fun runFinalizeExpiredVotes(community: Community): List<CouncilVote> {
    community.council.finalizeExpiredVotes()

    val finalizedVotes = community.council.getFinalizedVotes()
    
    finalizedVotes.forEach { vote ->
        if (vote.isPassed() && vote.isEnacted == true) {
            executeVote(community, vote)
        }
    }
    
    return finalizedVotes
}

private fun executeVote(community: Community, vote: CouncilVote) {
    when (val data = vote.executionData) {
        is VoteExecutionData.ChangeJoinPolicy -> {
            community.joinPolicy = data.newPolicy
        }
        is VoteExecutionData.ManageMember -> {
            val member = community.member[data.targetUUID]
            if (member != null) {
                when (data.action) {
                    VoteExecutionData.ManageMember.MemberAction.PROMOTE -> {
                        member.basicRoleType = com.imyvm.community.domain.model.community.MemberRoleType.ADMIN
                    }
                    VoteExecutionData.ManageMember.MemberAction.DEMOTE -> {
                        member.basicRoleType = com.imyvm.community.domain.model.community.MemberRoleType.MEMBER
                    }
                    VoteExecutionData.ManageMember.MemberAction.REMOVE -> {
                        com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.revokeGrantedPermissions(data.targetUUID, community)
                        community.member.remove(data.targetUUID)
                    }
                }
            }
        }
        is VoteExecutionData.AuditApplication -> {
            val applicant = community.member[data.applicantUUID]
            if (applicant != null) {
                if (data.accept) {
                    applicant.basicRoleType = com.imyvm.community.domain.model.community.MemberRoleType.MEMBER
                    applicant.joinedTime = System.currentTimeMillis()
                } else {
                    applicant.basicRoleType = com.imyvm.community.domain.model.community.MemberRoleType.REFUSED
                }
            }
        }
        null -> {
        }
    }
}
