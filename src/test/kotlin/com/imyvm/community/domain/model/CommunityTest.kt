package com.imyvm.community.domain.model

import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.UUID

class CommunityTest {
    @Test
    fun totalAssetsIncludesDonationsAndGrantsMinusExpenditures() {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val member = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val community = Community(
            regionNumberId = 42,
            member = hashMapOf(
                owner to MemberAccount(0L, MemberRoleType.OWNER, turnover = arrayListOf(
                    Turnover(1200L, 1L, TurnoverSource.PLAYER)
                )),
                member to MemberAccount(0L, MemberRoleType.MEMBER, turnover = arrayListOf(
                    Turnover(300L, 2L, TurnoverSource.PLAYER)
                ))
            ),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_REALM,
            communityIncome = arrayListOf(Turnover(500L, 3L, TurnoverSource.COMMUNITY_GRANT)),
            expenditures = arrayListOf(Turnover(250L, 4L, TurnoverSource.SERVER_ADMIN))
        )

        assertEquals(1750L, community.getTotalAssets())
        assertEquals(owner, community.getOwnerUUID())
        assertEquals(listOf(member), community.getMemberUUIDs())
    }
}
