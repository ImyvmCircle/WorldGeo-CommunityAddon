package com.imyvm.community.entrypoint.api

import com.imyvm.community.domain.model.Community
import com.imyvm.community.infra.CommunityDatabase

object CommunityApi {

    fun getCommunityByRegion(regionNumberId: Int): Community? {
        return CommunityDatabase.getCommunityById(regionNumberId)
    }

    fun listCommunities(): List<Community> {
        return CommunityDatabase.communities.toList()
    }
}
