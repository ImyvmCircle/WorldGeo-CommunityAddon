package com.imyvm.community.domain.model.community

enum class CommunityStatus(val value: Int) {
    RECRUITING_REALM(0),
    PENDING_MANOR(1),
    PENDING_REALM(2),
    ACTIVE_MANOR(3),
    ACTIVE_REALM(4),
    REVOKED_MANOR(5),
    REVOKED_REALM(6);
    companion object {
        fun fromValue(value: Int): CommunityStatus {
            return CommunityStatus.entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Invalid CommunityStatus value: $value")
        }
    }
}