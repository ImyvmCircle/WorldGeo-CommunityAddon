package com.imyvm.community.domain.model.account

enum class RecoveryDecision {
    PROCEED,
    SUCCEEDED,
    NEEDS_OP
}

object AccountExecutionRules {
    fun recoveryDecision(state: AccountTransactionState, observedBalance: Long): RecoveryDecision {
        val started = state.attempts.lastOrNull { it.callStartedAtMillis != null }
            ?: return RecoveryDecision.PROCEED
        return if (observedBalance == started.expectedBalance) {
            RecoveryDecision.SUCCEEDED
        } else {
            RecoveryDecision.NEEDS_OP
        }
    }

    fun canRetrySafely(state: AccountTransactionState, maximumRetries: Int): Boolean {
        require(maximumRetries >= 0)
        return state.attempts.none { it.callStartedAtMillis != null } && state.retryCount < maximumRetries
    }
}
