package com.imyvm.community.domain.model.community.council

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdministrationPermission
import com.imyvm.community.domain.model.community.Council
import com.imyvm.community.infra.CommunityConfig
import java.util.*

class CouncilVote(
    val id: UUID = UUID.randomUUID(),
    val permission: AdministrationPermission? = null,
    val proposeTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis() + (CommunityConfig.COUNCIL_VOTE_DURATION_HOURS.value * 60 * 60 * 1000),
    val proposerUUID: UUID? = null,
    val yeaVotes: MutableList<UUID> = mutableListOf(),
    val nayVotes: MutableList<UUID> = mutableListOf(),
    var isEnacted: Boolean? = null,
    val executionData: VoteExecutionData? = null,
    val title: String = "",
    val description: String = ""
) {

    fun castVote(voterUUID: UUID, isYea: Boolean, council: Council, community: Community) {
        if (!certificateVoter(voterUUID, council, community)) return
        if (isExpired()) return
        if (isEnacted != null) return

        if (isYea) {
            if (!yeaVotes.contains(voterUUID)) yeaVotes.add(voterUUID)
            nayVotes.remove(voterUUID)
        } else {
            if (!nayVotes.contains(voterUUID)) nayVotes.add(voterUUID)
            yeaVotes.remove(voterUUID)
        }
    }

    fun hasVoted(voterUUID: UUID): Boolean {
        return yeaVotes.contains(voterUUID) || nayVotes.contains(voterUUID)
    }

    fun getVoteStatus(voterUUID: UUID): VoteStatus {
        return when {
            yeaVotes.contains(voterUUID) -> VoteStatus.YEA
            nayVotes.contains(voterUUID) -> VoteStatus.NAY
            else -> VoteStatus.NOT_VOTED
        }
    }

    fun isExpired(): Boolean {
        return System.currentTimeMillis() >= endTime
    }

    fun isPassed(): Boolean {
        return yeaVotes.size > nayVotes.size
    }

    fun finalizeVote() {
        if (this.isEnacted == null) {
            this.isEnacted = isPassed()
        }
    }

    fun getVoteList(isYea: Boolean): MutableList<UUID> {
        return if (isYea) yeaVotes else nayVotes
    }

    fun getRemainingTime(): Long {
        val remaining = endTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    fun getTotalVotes(): Int {
        return yeaVotes.size + nayVotes.size
    }

    private fun certificateVoter(voterUuid: UUID, council: Council, community: Community): Boolean {
        return council.isCouncilMember(voterUuid, community)
    }

    enum class VoteStatus {
        YEA, NAY, NOT_VOTED
    }
}

sealed class VoteExecutionData {
    data class ChangeJoinPolicy(val newPolicy: com.imyvm.community.domain.model.community.CommunityJoinPolicy) : VoteExecutionData()
    data class ManageMember(
        val targetUUID: UUID,
        val action: MemberAction
    ) : VoteExecutionData() {
        enum class MemberAction {
            PROMOTE, DEMOTE, REMOVE
        }
    }
    data class AuditApplication(
        val applicantUUID: UUID,
        val accept: Boolean
    ) : VoteExecutionData()
}