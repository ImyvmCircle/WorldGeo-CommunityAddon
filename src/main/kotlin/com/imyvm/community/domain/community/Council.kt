package com.imyvm.community.domain.community

import com.imyvm.community.domain.Community
import java.util.*

class Council(
    val enabled: Boolean = false,
    val voteSet: Set<CouncilVote> = setOf(),
) {
    fun isCouncilMember(playerUuid: UUID, community: Community): Boolean {
        val councilMembers = getCouncilMembers(community)
        return councilMembers.contains(playerUuid)
    }

    fun getCouncilMembers(community: Community): List<UUID> {
        return community.member.entries
            .filter { it.value.isCouncilMember }
            .map { it.key }
    }
}

class CouncilVote(
    val executionType: ExecutionType = ExecutionType.DEFAULT,
    val proposeTime: Long = 0L,
    val proposorUUID: UUID? = null,
    val yeaVotes: MutableList<UUID> = mutableListOf(),
    val nayVotes: MutableList<UUID> = mutableListOf(),
    var isEnacted: Boolean? = null,
) {
    fun castVote(voterUUID: UUID, isYea: Boolean) {
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
}

enum class ExecutionType {
    DEFAULT,
    RENAME,
    SETTING_CHANGE,
    APPLICATION,
    MEMBER_PROMOTION,
    MEMBER_DEMOTION,
    MEMBER_REMOVE,
    MEMBER_INVITE,
    CHANGE_JOIN_POLICY
}