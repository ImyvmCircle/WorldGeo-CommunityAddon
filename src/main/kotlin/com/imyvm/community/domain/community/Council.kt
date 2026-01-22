package com.imyvm.community.domain.community

import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.council.CouncilVote
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