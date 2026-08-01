package com.imyvm.community.domain.model

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperationByKey
import com.imyvm.community.application.event.getPendingOperation
import com.imyvm.community.application.event.getPendingOperationByKey
import com.imyvm.community.application.event.removePendingOperation
import com.imyvm.community.application.event.removePendingOperationByKey
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

    @Test
    fun buildingConfirmationDataSurvivesPendingOperationModel() {
        val executor = java.util.UUID.fromString("00000000-0000-0000-0000-000000000099")
        val data = BuildingConfirmationData(42, executor, "select", "minecraft:oak_planks", 0, 1200L)
        val operation = PendingOperation(
            expireAt = 1234L,
            type = PendingOperationType.BUILDING_CONFIRMATION,
            buildingData = data
        )

        assertEquals(PendingOperationType.BUILDING_CONFIRMATION, PendingOperationType.fromValue(17))
        assertEquals(data, operation.buildingData)
    }

    @Test
    fun pendingOperationByKeyUsesExactLongKey() {
        val operationKey = 0x1234_5678_9abc_def0L
        try {
            addPendingOperationByKey(
                operationKey = operationKey,
                type = PendingOperationType.INVITATION,
                expireMinutes = 5
            )

            assertEquals(PendingOperationType.INVITATION, getPendingOperationByKey(operationKey)?.type)
        } finally {
            removePendingOperationByKey(operationKey)
            WorldGeoCommunityAddon.pendingOperations.clear()
        }
    }

}
