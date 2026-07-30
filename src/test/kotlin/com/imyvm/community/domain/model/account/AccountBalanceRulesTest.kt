package com.imyvm.community.domain.model.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountBalanceRulesTest {
    @Test
    fun calculatesCreditAndDebitWithoutOverflow() {
        assertEquals(125L, AccountBalanceRules.expected(100L, AccountDirection.CREDIT, 25L))
        assertEquals(75L, AccountBalanceRules.expected(100L, AccountDirection.DEBIT, 25L))
    }

    @Test
    fun rejectsInvalidAmountInsufficientFundsAndOverflow() {
        assertFailsWith<IllegalArgumentException> {
            AccountBalanceRules.expected(10L, AccountDirection.CREDIT, 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            AccountBalanceRules.expected(10L, AccountDirection.DEBIT, 11L)
        }
        assertFailsWith<ArithmeticException> {
            AccountBalanceRules.expected(Long.MAX_VALUE, AccountDirection.CREDIT, 1L)
        }
    }
}
