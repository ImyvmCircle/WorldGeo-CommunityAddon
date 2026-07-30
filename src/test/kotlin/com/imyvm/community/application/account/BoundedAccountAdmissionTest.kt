package com.imyvm.community.application.account

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundedAccountAdmissionTest {
    @Test
    fun neverAdmitsMoreThanItsHardCapacity() {
        val admission = BoundedAccountAdmission(2)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val third = UUID.randomUUID()
        assertEquals(AdmissionResult.ACQUIRED, admission.acquire(first))
        assertEquals(AdmissionResult.ALREADY_ACTIVE, admission.acquire(first))
        assertEquals(AdmissionResult.ACQUIRED, admission.acquire(second))
        assertEquals(AdmissionResult.SATURATED, admission.acquire(third))
        assertEquals(2, admission.size())
        admission.release(first)
        assertEquals(AdmissionResult.ACQUIRED, admission.acquire(third))
        assertEquals(2, admission.size())
    }
}
