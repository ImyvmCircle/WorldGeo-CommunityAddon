package com.imyvm.community.domain.model.account

import java.util.UUID

enum class AccountDirection {
    CREDIT,
    DEBIT
}

enum class AccountTransactionStatus {
    DETERMINED,
    WAITING_IDENTITY,
    SUCCEEDED,
    PENDING,
    NEEDS_OP,
    RESOLVED;

    fun isTerminal(): Boolean = this == SUCCEEDED || this == RESOLVED
}

data class AccountTransaction(
    val transactionId: UUID,
    val shortId: String,
    val createdAtMillis: Long,
    val periodKey: String,
    val subjectUuid: UUID,
    val subjectName: String?,
    val amount: Long,
    val direction: AccountDirection,
    val source: String,
    val externalReference: String,
    val previousTransactionId: UUID? = null
)

data class AccountAttempt(
    val attemptId: UUID,
    val attemptedAtMillis: Long,
    val balanceBefore: Long,
    val expectedBalance: Long,
    val callStartedAtMillis: Long? = null
)

data class AccountTransactionState(
    val transaction: AccountTransaction,
    val status: AccountTransactionStatus = AccountTransactionStatus.DETERMINED,
    val attempts: List<AccountAttempt> = emptyList(),
    val failureStage: String? = null,
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val nextRetryAtMillis: Long? = null,
    val finalBalance: Long? = null
)

data class PlayerIdentity(
    val uuid: UUID,
    val trustedName: String,
    val updatedAtMillis: Long
)
