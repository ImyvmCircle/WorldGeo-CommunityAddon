package com.imyvm.community.application.account

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.transaction.MemberLedgerFact
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.domain.model.transaction.TreasuryLedgerFact
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyTreasuryMigrationTest {
    private val memberUuid = UUID.fromString("12345678-1234-5678-1234-567812345678")
    private val projectedOperation = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

    @Test
    fun migrationIsStableExactAndOrdersCreditsBeforeDebits() {
        val community = Community(
            regionNumberId = 42,
            member = hashMapOf(memberUuid to MemberAccount(
                joinedTime = 1L,
                basicRoleType = MemberRoleType.MEMBER,
                turnover = arrayListOf(
                    Turnover(10_000L, 100L, TurnoverSource.PLAYER, "legacy.donation", listOf("Player")),
                    Turnover(
                        5_000L, 200L, TurnoverSource.PLAYER,
                        "community.treasury.desc.donation",
                        listOf("Player", projectedOperation.toString())
                    )
                )
            )),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_REALM,
            expenditures = arrayListOf(Turnover(4_000L, 400L)),
            communityIncome = arrayListOf(Turnover(3_000L, 300L))
        )

        val facts = legacyTreasuryFacts(listOf(community))
        val repeated = legacyTreasuryFacts(listOf(community))
        val treasury = facts.filterIsInstance<TreasuryLedgerFact>()
        val member = facts.filterIsInstance<MemberLedgerFact>()

        assertEquals(facts, repeated)
        assertEquals(4, facts.size)
        assertEquals(
            listOf(ResourceDirection.CREDIT, ResourceDirection.CREDIT, ResourceDirection.DEBIT),
            treasury.map { it.direction }
        )
        assertEquals(9_000L, treasury.sumOf {
            if (it.direction == ResourceDirection.CREDIT) it.amount else -it.amount
        })
        assertEquals(10_000L, member.single().amount)
        assertTrue(member.single().countsAsContribution)
    }

    @Test
    fun onlyCurrentDonationProjectionWithOperationIdIsSkipped() {
        assertTrue(isCurrentDonationProjection(
            "community.treasury.desc.donation",
            listOf("Player", projectedOperation.toString())
        ))
        assertFalse(isCurrentDonationProjection(
            "community.treasury.desc.donation",
            listOf("Player")
        ))
        assertFalse(isCurrentDonationProjection(
            "community.treasury.desc.donation",
            listOf("Player", "not-a-uuid")
        ))
    }
}
