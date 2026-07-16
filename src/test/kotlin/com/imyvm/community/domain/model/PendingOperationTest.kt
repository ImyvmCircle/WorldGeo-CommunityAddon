package com.imyvm.community.domain.model

import com.imyvm.community.application.event.getPendingOperation
import com.imyvm.community.application.event.removePendingOperation
import com.imyvm.community.application.event.restorePendingOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PendingOperationTest {
    @Test
    fun pendingOperationKeySeparatesTypesForSameSubject() {
        val subjectId = 42
        val renameKey = pendingOperationKey(subjectId, PendingOperationType.RENAME_CONFIRMATION)
        val settingKey = pendingOperationKey(subjectId, PendingOperationType.SETTING_CONFIRMATION)

        assertNotEquals(renameKey, settingKey)
        assertEquals(subjectId, pendingOperationSubjectId(renameKey))
        assertEquals(subjectId, pendingOperationSubjectId(settingKey))
    }

    @Test
    fun pendingOperationKeyPreservesNegativeSubjectIds() {
        val subjectId = -123456
        val key = pendingOperationKey(subjectId, PendingOperationType.INVITATION)

        assertEquals(subjectId, pendingOperationSubjectId(key))
    }
    @Test
    fun restorePendingOperationReinstatesExactOperation() {
        val subjectId = 99
        val operation = PendingOperation(
            expireAt = 1234L,
            type = PendingOperationType.CREATE_COMMUNITY_CONFIRMATION
        )

        removePendingOperation(subjectId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)
        restorePendingOperation(subjectId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION, operation)

        assertEquals(operation, getPendingOperation(subjectId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION))
        removePendingOperation(subjectId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)
    }

}
