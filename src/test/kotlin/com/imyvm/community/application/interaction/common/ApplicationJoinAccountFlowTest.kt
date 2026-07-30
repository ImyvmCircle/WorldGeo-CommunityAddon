package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ApplicationJoinAccountFlowTest {
    private val plan = ApplicationJoinPlan(
        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        42,
        UUID.fromString("12345678-1234-5678-1234-567812345678"),
        "Player|Name",
        12_345L,
        1_000_000L
    )

    @Test
    fun evidenceRoundTripsFrozenApplication() {
        assertEquals(plan, ApplicationJoinPlan.decode(plan.encode()))
        assertNull(ApplicationJoinPlan.decode("another-operation|v1"))
    }

    @Test
    fun debitAndRefundAreStableExactAndLinked() {
        val debit = applicationJoinTransaction(plan, AccountDirection.DEBIT)
        val repeated = applicationJoinTransaction(plan, AccountDirection.DEBIT)
        val refund = applicationJoinTransaction(plan, AccountDirection.CREDIT)

        assertEquals(debit, repeated)
        assertEquals(12_345L, debit.amount)
        assertEquals(AccountDirection.DEBIT, debit.direction)
        assertEquals(AccountDirection.CREDIT, refund.direction)
        assertEquals(debit.transactionId, refund.previousTransactionId)
        assertNotEquals(debit.transactionId, refund.transactionId)
    }

    @Test
    fun eachStepStateHasAStableDistinctFactId() {
        val determined = applicationJoinStep(
            plan, "application-community-state", CombinationStepStatus.DETERMINED
        )
        val repeated = applicationJoinStep(
            plan, "application-community-state", CombinationStepStatus.DETERMINED
        )
        val succeeded = applicationJoinStep(
            plan, "application-community-state", CombinationStepStatus.SUCCEEDED
        )

        assertEquals(determined, repeated)
        assertEquals(plan.encode(), determined.evidence)
        assertNotEquals(determined.factId, succeeded.factId)
        assertEquals(determined.externalReference, succeeded.externalReference)
    }
}
