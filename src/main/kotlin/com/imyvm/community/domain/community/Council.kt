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
)

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