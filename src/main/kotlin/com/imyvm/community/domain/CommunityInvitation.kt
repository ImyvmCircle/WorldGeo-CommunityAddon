package com.imyvm.community.domain

import java.util.UUID

data class CommunityInvitation(
    val inviterUUID: UUID,
    val inviteeUUID: UUID,
    val communityRegionId: Int,
    val expireAt: Long
)
