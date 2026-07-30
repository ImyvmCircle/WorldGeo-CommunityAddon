package com.imyvm.community.domain.model.account

import java.util.UUID

enum class ManualAccountAction {
    CONFIRM_APPLIED,
    CLOSE_UNCHANGED,
    RETRY_ORIGINAL
}

data class AccountInspection(
    val state: AccountTransactionState,
    val trustedName: String,
    val currentBalance: Long
)

data class AccountAuditRecord(
    val transactionId: UUID,
    val recordedAtMillis: Long,
    val actorName: String,
    val action: String,
    val observedBalance: Long?,
    val result: String
)
