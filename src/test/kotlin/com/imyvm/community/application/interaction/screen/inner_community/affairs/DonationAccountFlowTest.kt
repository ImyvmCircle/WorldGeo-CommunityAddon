package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DonationAccountFlowTest {
    private val plan = DonationPlan(
        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        42,
        UUID.fromString("12345678-1234-5678-1234-567812345678"),
        "Player|Name",
        12_345L,
        1_000_000L
    )

    @Test
    fun evidenceRoundTripsFrozenPlan() {
        assertEquals(plan, DonationPlan.decode(plan.encode()))
        assertNull(DonationPlan.decode("another-operation|v1"))
    }

    @Test
    fun debitTransactionIsStableAndUsesExactCents() {
        val first = donationTransaction(plan)
        val repeated = donationTransaction(plan)

        assertEquals(first, repeated)
        assertEquals(AccountDirection.DEBIT, first.direction)
        assertEquals(12_345L, first.amount)
        assertEquals(plan.playerUuid, first.subjectUuid)
        assertEquals("community:donation:debit:${plan.operationId}", first.externalReference)
    }

    @Test
    fun eachStepStateHasAStableDistinctFactId() {
        val reference = "community:donation:treasury:${plan.operationId}"
        val determined = donationStep(
            plan, "donation-treasury-credit", "treasury", reference,
            CombinationStepStatus.DETERMINED
        )
        val repeated = donationStep(
            plan, "donation-treasury-credit", "treasury", reference,
            CombinationStepStatus.DETERMINED
        )
        val succeeded = donationStep(
            plan, "donation-treasury-credit", "treasury", reference,
            CombinationStepStatus.SUCCEEDED
        )

        assertEquals(determined, repeated)
        assertEquals(plan.encode(), determined.evidence)
        assertNotEquals(determined.factId, succeeded.factId)
        assertEquals(determined.externalReference, succeeded.externalReference)
    }
}
