package com.imyvm.community.domain.model.community

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.council.CouncilVote
import com.imyvm.community.infra.CommunityConfig
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class Council(
    var enabled: Boolean = false,
    val voteSet: MutableSet<CouncilVote> = mutableSetOf(),
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

    fun getCouncilMemberCount(community: Community): Int {
        return getCouncilMembers(community).size
    }

    fun getActiveVotes(): List<CouncilVote> {
        return voteSet.filter { !it.isExpired() && it.isEnacted == null }
    }

    fun getExpiredVotes(): List<CouncilVote> {
        return voteSet.filter { it.isExpired() && it.isEnacted == null }
    }

    fun getFinalizedVotes(): List<CouncilVote> {
        return voteSet.filter { it.isEnacted != null }
    }

    fun getVotesCreatedToday(): Int {
        val today = LocalDate.now(ZoneId.systemDefault())
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return voteSet.count { vote ->
            vote.proposeTime in todayStart until todayEnd
        }
    }

    fun canCreateVoteToday(): Boolean {
        return getVotesCreatedToday() < CommunityConfig.COUNCIL_MAX_VOTES_PER_DAY.value
    }

    fun addVote(vote: CouncilVote): Boolean {
        if (!canCreateVoteToday()) return false
        return voteSet.add(vote)
    }

    fun getVote(voteId: UUID): CouncilVote? {
        return voteSet.find { it.id == voteId }
    }

    fun removeVote(voteId: UUID): Boolean {
        return voteSet.removeIf { it.id == voteId }
    }

    fun finalizeExpiredVotes() {
        getExpiredVotes().forEach { it.finalizeVote() }
    }
}