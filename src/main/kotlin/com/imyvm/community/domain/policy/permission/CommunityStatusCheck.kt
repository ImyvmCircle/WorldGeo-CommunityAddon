package com.imyvm.community.domain.policy.permission

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.CommunityStatus

internal fun isProtoCommunity(community: Community): Boolean =
    community.status == CommunityStatus.RECRUITING_REALM ||
    community.status == CommunityStatus.PENDING_MANOR ||
    community.status == CommunityStatus.PENDING_REALM

internal fun isActiveCommunity(community: Community): Boolean =
    community.status == CommunityStatus.ACTIVE_MANOR ||
    community.status == CommunityStatus.ACTIVE_REALM

internal fun isRevokedCommunity(community: Community): Boolean =
    community.status == CommunityStatus.REVOKED_MANOR ||
    community.status == CommunityStatus.REVOKED_REALM
