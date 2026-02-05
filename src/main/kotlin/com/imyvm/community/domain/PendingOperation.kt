package com.imyvm.community.domain


class PendingOperation(
    val expireAt: Long,
    val type: PendingOperationType
)

enum class PendingOperationType(val value: Int) {
    CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT(0),
    DELETE_COMMUNITY(1),
    LEAVE_COMMUNITY(2),
    JOIN_COMMUNITY(3),
    CHANGE_ROLE(4),
    CHANGE_JOIN_POLICY(5),
    AUDITING_COMMUNITY_REQUEST(6);
}