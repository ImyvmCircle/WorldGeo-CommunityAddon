package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.infra.CommunityDatabase

fun filterCommunitiesByType(type: CommunityListFilterType): List<Community> {
    return when (type) {
        CommunityListFilterType.ALL -> CommunityDatabase.communities
        CommunityListFilterType.JOIN_ABLE -> CommunityDatabase.communities.filter {
            (it.status == CommunityStatus.RECRUITING_REALM || it.status == CommunityStatus.PENDING_MANOR
                    || it.status == CommunityStatus.PENDING_REALM || it.status == CommunityStatus.ACTIVE_MANOR
                    || it.status == CommunityStatus.ACTIVE_REALM) &&
                    (it.joinPolicy == CommunityJoinPolicy.OPEN || it.joinPolicy == CommunityJoinPolicy.APPLICATION)
        }
        CommunityListFilterType.RECRUITING -> CommunityDatabase.communities.filter { it.status == CommunityStatus.RECRUITING_REALM }
        CommunityListFilterType.AUDITING -> CommunityDatabase.communities.filter {
            it.status == CommunityStatus.PENDING_MANOR || it.status == CommunityStatus.PENDING_REALM
        }
        CommunityListFilterType.ACTIVE -> CommunityDatabase.communities.filter {
            it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.ACTIVE_REALM
        }
        CommunityListFilterType.REVOKED -> CommunityDatabase.communities.filter {
            it.status == CommunityStatus.REVOKED_MANOR || it.status == CommunityStatus.REVOKED_REALM
        }
    }
}