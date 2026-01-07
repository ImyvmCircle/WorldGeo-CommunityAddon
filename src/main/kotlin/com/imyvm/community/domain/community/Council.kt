package com.imyvm.community.domain.community

import java.util.*

class Council(
    val enabled: Boolean = false,
    val vote: CouncilVote = CouncilVote(),
)

class CouncilVote(
    val executionType: ExecutionType = ExecutionType.DEFAULT,
    val proposeTime: Long = 0L,
    val proposorUUID: UUID? = null,
    val yeaVotes: MutableList<UUID> = mutableListOf(),
    val nayVotes: MutableList<UUID> = mutableListOf(),
)

enum class ExecutionType {
    DEFAULT,
    APPLICATION,
}