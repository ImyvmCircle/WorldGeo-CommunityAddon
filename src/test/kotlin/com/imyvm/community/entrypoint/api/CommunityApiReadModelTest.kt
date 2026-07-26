package com.imyvm.community.entrypoint.api

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.CommunityTitle
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.community.CommunityPolicyState
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.AdminPrivileges
import com.imyvm.community.infra.CommunityDatabase
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CommunityApiReadModelTest {
    @AfterTest
    fun cleanup() {
        CommunityDatabase.communities = mutableListOf()
    }

    @Test
    fun listPlayerOrganizationsAndPolicyReadsExposeReadOnlySummaries() {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val admin = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val member = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val outsider = UUID.fromString("00000000-0000-0000-0000-000000000004")

        val manor = Community(
            regionNumberId = 77,
            member = hashMapOf(
                owner to MemberAccount(0L, MemberRoleType.OWNER),
                admin to MemberAccount(0L, MemberRoleType.ADMIN, AdminPrivileges(mutableSetOf(AdminPrivilege.MANAGE_MEMBERS))),
                member to MemberAccount(0L, MemberRoleType.MEMBER)
            ),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_MANOR,
            communityIncome = arrayListOf(Turnover(5000L, 1L, TurnoverSource.SYSTEM)),
            expenditures = arrayListOf(Turnover(1200L, 2L, TurnoverSource.SYSTEM)),
            titles = mutableListOf(
                CommunityTitle(1, "title.owner", owner, 1L, active = true),
                CommunityTitle(2, "title.member", member, 2L, active = true),
                CommunityTitle(3, "title.hidden", member, 3L, active = false)
            ),
            policy = CommunityPolicyState("growth", "tax_cut", "2026-W32", "2026-W30")
        )

        val realm = Community(
            regionNumberId = 78,
            member = hashMapOf(
                member to MemberAccount(0L, MemberRoleType.ADMIN, AdminPrivileges(mutableSetOf(AdminPrivilege.GRANT_COINS_FROM_TREASURY)))
            ),
            joinPolicy = CommunityJoinPolicy.INVITE_ONLY,
            status = CommunityStatus.ACTIVE_REALM,
            titles = mutableListOf(CommunityTitle(1, "title.realm", member, 4L, active = true)),
            policy = CommunityPolicyState("default", null, null, null)
        )

        CommunityDatabase.communities = mutableListOf(manor, realm)

        val snapshot = CommunityApi.getCommunityByRegion(77)
        assertNotNull(snapshot)
        assertEquals(CommunityOrganizationType.MANOR, snapshot.organizationType)
        assertEquals(3, snapshot.memberCount)
        assertEquals(3800L, snapshot.totalAssets)
        assertEquals(listOf("title.member", "title.owner"), snapshot.displayTitleKeys)
        assertEquals("growth", snapshot.publicPolicy.activePolicyKey)
        assertEquals("tax_cut", snapshot.publicPolicy.pendingPolicyKey)

        val organizations = CommunityApi.listPlayerOrganizations(member)
        assertEquals(2, organizations.size)
        assertEquals(77, organizations[0].regionNumberId)
        assertEquals(MemberRoleType.MEMBER, organizations[0].memberRole)
        assertEquals(listOf("title.member"), organizations[0].displayTitleKeys)
        assertEquals(true, organizations[0].canRepresent)
        assertEquals(78, organizations[1].regionNumberId)
        assertEquals(MemberRoleType.ADMIN, organizations[1].memberRole)
        assertEquals(listOf("GRANT_COINS_FROM_TREASURY"), organizations[1].administrationPrivileges)
        assertEquals(listOf("title.realm"), organizations[1].displayTitleKeys)

        assertEquals(3800L, CommunityApi.getTreasuryBalance(77))
        assertEquals("growth", CommunityApi.getCommunityPolicies(77)?.activePolicyKey)
        assertNull(CommunityApi.getTreasuryBalance(999))
        assertNull(CommunityApi.getCommunityPolicies(999))
        assertEquals(emptyList(), CommunityApi.listPlayerOrganizations(outsider))
    }
}
