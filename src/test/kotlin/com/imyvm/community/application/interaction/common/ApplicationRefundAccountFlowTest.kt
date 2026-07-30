package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import com.mojang.authlib.GameProfile
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ApplicationRefundAccountFlowTest {
    private val profile = GameProfile(
        UUID.fromString("12345678-1234-5678-1234-567812345678"),
        "Player|Name"
    )
    private val plan = ApplicationRefundPlan.create(42, profile, 12_345L, 1_000_000L)

    @Test
    fun evidenceRoundTripsFrozenRefund() {
        assertEquals(plan, ApplicationRefundPlan.decode(plan.encode()))
        assertNull(ApplicationRefundPlan.decode("another-operation|v1"))
    }

    @Test
    fun creditTransactionIsStableAndUsesExactCents() {
        val first = applicationRefundTransaction(plan)
        val repeated = applicationRefundTransaction(plan)

        assertEquals(first, repeated)
        assertEquals(AccountDirection.CREDIT, first.direction)
        assertEquals(12_345L, first.amount)
        assertEquals(profile.id, first.subjectUuid)
        assertEquals("community:application-refund:credit:${plan.operationId}", first.externalReference)
    }

    @Test
    fun eachStepStateHasAStableDistinctFactId() {
        val determined = applicationRefundStep(
            plan, "application-refund-account-credit", CombinationStepStatus.DETERMINED
        )
        val repeated = applicationRefundStep(
            plan, "application-refund-account-credit", CombinationStepStatus.DETERMINED
        )
        val succeeded = applicationRefundStep(
            plan, "application-refund-account-credit", CombinationStepStatus.SUCCEEDED
        )

        assertEquals(determined, repeated)
        assertEquals(plan.encode(), determined.evidence)
        assertNotEquals(determined.factId, succeeded.factId)
        assertEquals(determined.externalReference, succeeded.externalReference)
    }
}
