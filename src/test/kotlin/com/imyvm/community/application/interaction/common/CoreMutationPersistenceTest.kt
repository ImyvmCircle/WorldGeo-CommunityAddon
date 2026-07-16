package com.imyvm.community.application.interaction.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreMutationPersistenceTest {
    @Test
    fun saveSuccessDoesNotRestoreRollbackOrNotify() {
        val events = mutableListOf<String>()

        val result = saveCommunityStateOrRollback(
            operationName = "test operation",
            saveCommunityState = { events.add("save") },
            restoreCommunityState = { events.add("restore") },
            rollbackCoreState = { events.add("rollback") },
            notifyFailure = { events.add("notify") }
        )

        assertTrue(result)
        assertEquals(listOf("save"), events)
    }

    @Test
    fun saveFailureRestoresCommunityRollsBackCoreAndNotifies() {
        val events = mutableListOf<String>()

        val result = saveCommunityStateOrRollback(
            operationName = "test operation",
            saveCommunityState = {
                events.add("save")
                throw IllegalStateException("database failed")
            },
            restoreCommunityState = { events.add("restore") },
            rollbackCoreState = { events.add("rollback") },
            notifyFailure = { events.add("notify") }
        )

        assertFalse(result)
        assertEquals(listOf("save", "restore", "rollback", "notify"), events)
    }

    @Test
    fun restoreFailureStillRunsCoreRollbackAndNotification() {
        val events = mutableListOf<String>()

        val result = saveCommunityStateOrRollback(
            operationName = "test operation",
            saveCommunityState = {
                events.add("save")
                throw IllegalStateException("database failed")
            },
            restoreCommunityState = {
                events.add("restore")
                throw IllegalStateException("restore failed")
            },
            rollbackCoreState = { events.add("rollback") },
            notifyFailure = { events.add("notify") }
        )

        assertFalse(result)
        assertEquals(listOf("save", "restore", "rollback", "notify"), events)
    }
}
