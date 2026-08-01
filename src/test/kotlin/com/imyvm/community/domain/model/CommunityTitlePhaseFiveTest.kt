package com.imyvm.community.domain.model

import com.imyvm.community.application.title.CommunityTitleService
import com.imyvm.community.application.townbuilding.CommunityBuildingBlockStats
import com.imyvm.community.application.townbuilding.CommunityBuildingSettlement
import com.imyvm.community.domain.model.community.CommunityBuildingEntry
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.UUID

class CommunityTitlePhaseFiveTest {
    @Test
    fun foremanSlotQualificationUsesFormalMembers() {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val first = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val applicant = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val community = community(CommunityStatus.ACTIVE_MANOR, mapOf(
            owner to MemberRoleType.OWNER,
            first to MemberRoleType.MEMBER,
            applicant to MemberRoleType.APPLICANT
        ))
        assertEquals(2, CommunityTitleService.foremanSlotLimit(community))
        assertEquals(1, CommunityTitleService.availableForemanSlots(community))
    }

    @Test
    fun grantRevokeAndDisplaySelectionArePersistedInState() {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val member = UUID.fromString("00000000-0000-0000-0000-000000000012")
        val community = community(CommunityStatus.ACTIVE_MANOR, mapOf(owner to MemberRoleType.OWNER, member to MemberRoleType.MEMBER))
        CommunityDatabase.communities = mutableListOf(community)
        assertEquals(0, CommunityTitleService.grantForeman(community, member, 0L, persist = false).getOrThrow())
        assertEquals(null, CommunityTitleService.displayLabel(community, member))
        CommunityTitleService.selectDisplay(community, member, persist = false).getOrThrow()
        assertEquals("community.title.foreman", CommunityTitleService.displayLabel(community, member))
        assertEquals(0, CommunityTitleService.revokeForeman(community, member, 0L, persist = false).getOrThrow())
        assertFalse(community.titleState.selectedDisplay.contains(member))
        assertTrue(community.titleState.foremanSlots.first().cooldownUntilMillis > 0L)
    }

    @Test
    fun foremanBuildingRewardUsesMultiplierAndExtraCap() {
        val foreman = UUID.fromString("00000000-0000-0000-0000-000000000021")
        val regular = UUID.fromString("00000000-0000-0000-0000-000000000022")
        val plan = CommunityBuildingSettlement.plan(
            listOf(CommunityBuildingEntry("minecraft:oak_planks", 1, 100000L)),
            listOf(CommunityBuildingBlockStats("minecraft:oak_planks", 20, 0, mapOf(foreman to 10L, regular to 10L))),
            120000L,
            mapOf(foreman to 110000L, regular to 110000L),
            200000L,
            0L,
            mapOf(foreman to 120L),
            mapOf(foreman to 60000L)
        )
        assertEquals(2, plan.playerRewards.size)
        assertEquals(70000L, plan.playerRewards.first { it.playerUuid == foreman }.amount)
        assertEquals(10000L, plan.playerRewards.first { it.playerUuid == regular }.amount)
    }

    private fun community(status: CommunityStatus, roles: Map<UUID, MemberRoleType>) = Community(
        regionNumberId = 1,
        member = HashMap(roles.mapValues { MemberAccount(0L, it.value) }),
        joinPolicy = CommunityJoinPolicy.OPEN,
        status = status,
        treasuryBalance = 1_000_000L
    )
}
