package com.imyvm.community.application.interaction.screen.inner_community.council

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.AdministrationPermission
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.domain.community.council.VoteExecutionData
import com.imyvm.community.entrypoints.screen.inner_community.CommunityAdministrationMenu
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun runOpenCouncilVoteCreationMenu(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityAdministrationMenu(
            syncId,
            community,
            player,
            runBack,
            voteCreationMode = true
        )
    }
}

fun createChangeJoinPolicyVote(
    player: ServerPlayerEntity,
    community: Community,
    newPolicy: CommunityJoinPolicy,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val executionData = VoteExecutionData.ChangeJoinPolicy(newPolicy)
    runCreateVoteProposal(
        player,
        community,
        title = "Change Join Policy",
        description = "Change join policy to: $newPolicy",
        executionData,
        permission = null,
        runBack
    )
}

fun createManageMemberVote(
    player: ServerPlayerEntity,
    community: Community,
    targetPlayer: UUID,
    action: VoteExecutionData.ManageMember.MemberAction,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val executionData = VoteExecutionData.ManageMember(targetPlayer, action)
    val actionName = when (action) {
        VoteExecutionData.ManageMember.MemberAction.PROMOTE -> "Promote to Administrator"
        VoteExecutionData.ManageMember.MemberAction.DEMOTE -> "Demote to Member"
        VoteExecutionData.ManageMember.MemberAction.REMOVE -> "Remove from community"
    }
    runCreateVoteProposal(
        player,
        community,
        title = "Manage Member",
        description = "$actionName: $targetPlayer",
        executionData,
        permission = AdministrationPermission.MANAGE_MEMBERS,
        runBack
    )
}

fun createAuditApplicationVote(
    player: ServerPlayerEntity,
    community: Community,
    applicantId: UUID,
    approve: Boolean,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val executionData = VoteExecutionData.AuditApplication(applicantId, approve)
    runCreateVoteProposal(
        player,
        community,
        title = if (approve) "Approve Application" else "Reject Application",
        description = "${if (approve) "Approve" else "Reject"} application from: $applicantId",
        executionData,
        permission = null,
        runBack
    )
}
