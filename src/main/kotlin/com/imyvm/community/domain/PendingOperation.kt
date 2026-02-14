package com.imyvm.community.domain

import java.util.*

class PendingOperation(
    val expireAt: Long,
    val type: PendingOperationType,
    val inviterUUID: UUID? = null,
    val inviteeUUID: UUID? = null
)

enum class PendingOperationType(val value: Int) {
    CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT(0),
    DELETE_COMMUNITY(1),
    LEAVE_COMMUNITY(2),
    JOIN_COMMUNITY(3),
    CHANGE_ROLE(4),
    CHANGE_JOIN_POLICY(5),
    AUDITING_COMMUNITY_REQUEST(6),
    INVITATION(7);
}