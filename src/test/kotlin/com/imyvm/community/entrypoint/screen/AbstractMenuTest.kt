package com.imyvm.community.entrypoint.screen

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AbstractMenuTest {
    @Test
    fun duplicateButtonSlotsFailValidation() {
        assertFailsWith<IllegalArgumentException> { MenuSlotRules.validate(10, 54, listOf(10)) }
    }

    @Test
    fun outOfBoundsButtonSlotsFailValidation() {
        assertFailsWith<IllegalArgumentException> { MenuSlotRules.validate(54, 54, emptyList()) }
    }
}
