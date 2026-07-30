package com.imyvm.community.infra.account

import com.imyvm.community.domain.model.account.AccountAttempt
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import java.util.UUID

sealed interface AccountFact {
    val transactionId: UUID

    data class Determined(val transaction: AccountTransaction) : AccountFact {
        override val transactionId: UUID = transaction.transactionId
    }

    data class Attempted(
        override val transactionId: UUID,
        val attempt: AccountAttempt
    ) : AccountFact

    data class CallStarted(
        override val transactionId: UUID,
        val attemptId: UUID,
        val startedAtMillis: Long
    ) : AccountFact

    data class StateChanged(
        override val transactionId: UUID,
        val status: AccountTransactionStatus,
        val failureStage: String? = null,
        val failureReason: String? = null,
        val retryCount: Int = 0,
        val nextRetryAtMillis: Long? = null,
        val finalBalance: Long? = null
    ) : AccountFact
}

data class SequencedAccountFact(val sequence: Long, val fact: AccountFact)
