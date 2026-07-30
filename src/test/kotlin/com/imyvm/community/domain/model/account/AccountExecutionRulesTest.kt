package com.imyvm.community.domain.model.account

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountExecutionRulesTest {
    @Test
    fun retriesOnlyBeforeThePublicApiCallBoundary() {
        val state = state().copy(retryCount = 2)
        assertTrue(AccountExecutionRules.canRetrySafely(state, 3))
        assertFalse(AccountExecutionRules.canRetrySafely(state.copy(retryCount = 3), 3))

        val started = state.copy(attempts = listOf(attempt(callStartedAtMillis = 20L)))
        assertFalse(AccountExecutionRules.canRetrySafely(started, 3))
    }

    @Test
    fun recoverySucceedsOnlyWhenExpectedBalanceIsObserved() {
        val untouched = state()
        assertEquals(RecoveryDecision.PROCEED, AccountExecutionRules.recoveryDecision(untouched, 100L))

        val uncertain = untouched.copy(attempts = listOf(attempt(callStartedAtMillis = 20L)))
        assertEquals(RecoveryDecision.SUCCEEDED, AccountExecutionRules.recoveryDecision(uncertain, 125L))
        assertEquals(RecoveryDecision.NEEDS_OP, AccountExecutionRules.recoveryDecision(uncertain, 100L))
        assertEquals(RecoveryDecision.NEEDS_OP, AccountExecutionRules.recoveryDecision(uncertain, 110L))
    }

    private fun state(): AccountTransactionState = AccountTransactionState(
        AccountTransaction(
            UUID.randomUUID(),
            "A0001",
            1L,
            "manual",
            UUID.randomUUID(),
            "TrustedName",
            25L,
            AccountDirection.CREDIT,
            "test",
            "test:execution"
        )
    )

    private fun attempt(callStartedAtMillis: Long?): AccountAttempt = AccountAttempt(
        UUID.randomUUID(),
        10L,
        100L,
        125L,
        callStartedAtMillis
    )
}
