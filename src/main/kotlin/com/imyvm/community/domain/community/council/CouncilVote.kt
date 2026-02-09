package com.imyvm.community.domain.community.council

import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.Council
import java.util.*

class CouncilVote(
    val executionType: ExecutionType = ExecutionType.DEFAULT,
    val proposeTime: Long = 0L,
    val proposerUUID: UUID? = null,
    val yeaVotes: MutableList<UUID> = mutableListOf(),
    val nayVotes: MutableList<UUID> = mutableListOf(),
    var isEnacted: Boolean? = null,
) {
    fun castVote(voterUUID: UUID, isYea: Boolean, council: Council, community: Community) {
        if (!certificateVoter(voterUUID, council, community)) return

        if (isYea) {
            if (!yeaVotes.contains(voterUUID)) yeaVotes.add(voterUUID)
            nayVotes.remove(voterUUID)
        } else {
            if (!nayVotes.contains(voterUUID)) nayVotes.add(voterUUID)
            yeaVotes.remove(voterUUID)
        }
    }

    fun finalizeVote(){
        if (this.isEnacted == null) {
            this.isEnacted = yeaVotes.size > nayVotes.size
        }
    }

    fun getVoteList(isYea: Boolean): MutableList<UUID> {
        return if (isYea) yeaVotes else nayVotes
    }

    private fun certificateVoter(voterUuid: UUID, council: Council, community: Community): Boolean{
        return council.isCouncilMember(voterUuid, community)
    }
}