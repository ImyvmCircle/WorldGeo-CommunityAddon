package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.model.CreationConfirmationData
import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CommunityCreationAccountContractTest {
    private val data = CreationConfirmationData(
        communityName = "Test",
        communityType = "manor",
        shapeName = "RECTANGLE",
        regionNumberId = 42,
        creatorUUID = UUID.fromString("12345678-1234-5678-1234-567812345678"),
        totalCost = 12_345L
    )
    private val execution = PendingOperation(
        expireAt = 1_000_000L,
        type = PendingOperationType.CREATE_COMMUNITY_EXECUTION,
        creationData = data
    )

    @Test
    fun creationTransactionsUseStableDistinctReferences() {
        val debit = communityCreationTransaction(execution, AccountDirection.DEBIT, "Player")
        val repeatedDebit = communityCreationTransaction(execution, AccountDirection.DEBIT, null)
        val refund = communityCreationTransaction(execution, AccountDirection.CREDIT, null)

        assertEquals(debit.transactionId, repeatedDebit.transactionId)
        assertEquals(debit.externalReference, repeatedDebit.externalReference)
        assertNotEquals(debit.transactionId, refund.transactionId)
        assertNotEquals(debit.externalReference, refund.externalReference)
        assertNull(debit.previousTransactionId)
        assertEquals(debit.transactionId, refund.previousTransactionId)
        assertEquals(data.totalCost, debit.amount)
        assertEquals(data.totalCost, refund.amount)

        val laterExecution = PendingOperation(
            expireAt = execution.expireAt + 1,
            type = PendingOperationType.CREATE_COMMUNITY_EXECUTION,
            creationData = data
        )
        val laterDebit = communityCreationTransaction(laterExecution, AccountDirection.DEBIT, null)
        assertNotEquals(debit.transactionId, laterDebit.transactionId)
        assertNotEquals(debit.externalReference, laterDebit.externalReference)
    }

    @Test
    fun creationFactsUseStableIdsPerStepState() {
        val determined = communityCreationStep(
            execution, "account-debit", CombinationStepStatus.DETERMINED
        )
        val repeated = communityCreationStep(
            execution, "account-debit", CombinationStepStatus.DETERMINED
        )
        val succeeded = communityCreationStep(
            execution, "account-debit", CombinationStepStatus.SUCCEEDED
        )

        assertEquals(determined, repeated)
        assertEquals(communityCreationOperationId(execution), determined.operationId)
        assertNotEquals(determined.factId, succeeded.factId)
        assertEquals(determined.externalReference, succeeded.externalReference)
    }

    @Test
    fun executionTypeKeepsPublishedEnumValuesStable() {
        assertEquals(8, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION.value)
        assertEquals(15, PendingOperationType.TREASURY_GRANT_CONFIRMATION.value)
        assertEquals(16, PendingOperationType.CREATE_COMMUNITY_EXECUTION.value)
        assertEquals(
            PendingOperationType.CREATE_COMMUNITY_EXECUTION,
            PendingOperationType.fromValue(16)
        )
    }
}
