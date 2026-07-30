package com.imyvm.community.domain.model.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyAmountTest {
    @Test
    fun parsesExactPositiveCents() {
        assertEquals(100L, MoneyAmount.parseCents("1.00"))
        assertEquals(50L, MoneyAmount.parseCents("0.50"))
    }

    @Test
    fun rejectsNegativeFractionalAndOverflowingInput() {
        listOf("-1", "0", "1.001", "92233720368547759").forEach { input ->
            assertFailsWith<RuntimeException> { MoneyAmount.parseCents(input) }
        }
    }
}
