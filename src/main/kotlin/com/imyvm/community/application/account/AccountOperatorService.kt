package com.imyvm.community.application.account

import com.imyvm.community.domain.model.account.AccountAttempt
import com.imyvm.community.domain.model.account.AccountAuditRecord
import com.imyvm.community.domain.model.account.AccountBalanceRules
import com.imyvm.community.domain.model.account.AccountInspection
import com.imyvm.community.domain.model.account.AccountTransactionState
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.account.ManualAccountAction
import com.imyvm.community.infra.account.AccountAuditLog
import com.imyvm.community.infra.account.AccountFact
import com.imyvm.community.infra.account.AccountTransactionStore
import com.imyvm.community.infra.account.CommunityDataWriter
import com.imyvm.community.infra.account.PlayerIdentityDirectory
import com.imyvm.community.infra.economy.EconomyWalletAdapter
import net.minecraft.server.MinecraftServer
import java.util.UUID
import java.util.concurrent.CompletableFuture

class AccountOperatorService(
    private val server: MinecraftServer,
    private val store: AccountTransactionStore,
    private val writer: CommunityDataWriter,
    private val identities: PlayerIdentityDirectory,
    private val wallet: EconomyWalletAdapter,
    private val audit: AccountAuditLog,
    private val transactions: AccountTransactionService
) {
    fun inspect(shortId: String): CompletableFuture<AccountInspection> = store.findByShortId(shortId).thenCompose { state ->
        requireNotNull(state) { "Unknown account transaction" }
        inspect(state)
    }

    fun audit(shortId: String, limit: Int): CompletableFuture<List<AccountAuditRecord>> =
        store.findByShortId(shortId).thenCompose { state ->
            requireNotNull(state) { "Unknown account transaction" }
            audit.find(state.transaction.transactionId, limit)
        }

    fun act(shortId: String, action: ManualAccountAction, actorName: String): CompletableFuture<AccountTransactionState> =
        inspect(shortId).thenCompose { inspection ->
            require(inspection.state.status == AccountTransactionStatus.NEEDS_OP) { "Transaction does not need OP action" }
            record(inspection, actorName, action, "REQUESTED").thenCompose {
                when (action) {
                    ManualAccountAction.CONFIRM_APPLIED -> confirmApplied(inspection)
                    ManualAccountAction.CLOSE_UNCHANGED -> closeUnchanged(inspection)
                    ManualAccountAction.RETRY_ORIGINAL -> retryOriginal(inspection)
                }
            }.thenCompose { updated ->
                record(inspection.copy(state = updated), actorName, action, "COMPLETED").thenApply { updated }
            }.whenComplete { updated, error ->
                if (error == null && updated.status.isTerminal()) transactions.terminalUpdated(updated)
            }
        }

    private fun inspect(state: AccountTransactionState): CompletableFuture<AccountInspection> = trustedName(state).thenCompose { name ->
        serverCall {
            val balance = wallet.withWallet(server, state.transaction.subjectUuid, name) { balance() }
            AccountInspection(state, name, balance)
        }
    }

    private fun trustedName(state: AccountTransactionState): CompletableFuture<String> = serverCall {
        server.playerList.getPlayer(state.transaction.subjectUuid)?.gameProfile?.name
    }.thenCompose { onlineName ->
        if (onlineName != null) CompletableFuture.completedFuture(onlineName)
        else writer.submit { identities.find(state.transaction.subjectUuid)?.trustedName }
            .thenApply { requireNotNull(it) { "Trusted player name unavailable" } }
    }

    private fun confirmApplied(inspection: AccountInspection): CompletableFuture<AccountTransactionState> {
        val expected = inspection.state.attempts.lastOrNull { it.callStartedAtMillis != null }?.expectedBalance
            ?: error("No public API call boundary")
        require(inspection.currentBalance == expected) { "Current balance does not equal expected balance" }
        return stateChange(inspection.state, AccountTransactionStatus.SUCCEEDED, "OP_CONFIRMED", null, expected)
    }

    private fun closeUnchanged(inspection: AccountInspection): CompletableFuture<AccountTransactionState> {
        val before = inspection.state.attempts.lastOrNull { it.callStartedAtMillis != null }?.balanceBefore
            ?: error("No public API call boundary")
        require(inspection.currentBalance == before) { "Current balance does not equal balance before the call" }
        return stateChange(inspection.state, AccountTransactionStatus.RESOLVED, "OP_CLOSED_UNCHANGED", null, before)
    }

    private fun retryOriginal(inspection: AccountInspection): CompletableFuture<AccountTransactionState> {
        val state = inspection.state
        val previous = state.attempts.lastOrNull { it.callStartedAtMillis != null }
            ?: error("No public API call boundary")
        require(inspection.currentBalance == previous.balanceBefore) { "Current balance changed; original operation cannot be retried" }
        val expected = AccountBalanceRules.expected(
            inspection.currentBalance,
            state.transaction.direction,
            state.transaction.amount
        )
        val attempt = AccountAttempt(UUID.randomUUID(), System.currentTimeMillis(), inspection.currentBalance, expected)
        return store.recordAttempt(state.transaction.transactionId, attempt).thenCompose { attempted ->
            serverCall {
                val current = wallet.withWallet(server, state.transaction.subjectUuid, inspection.trustedName) { balance() }
                require(current == attempt.balanceBefore) { "Balance changed before retry call" }
            }.thenCompose {
                store.recordCallStarted(state.transaction.transactionId, attempt.attemptId, System.currentTimeMillis())
            }.thenCompose { started ->
                serverCall {
                    try {
                        val applied = wallet.withWallet(server, state.transaction.subjectUuid, inspection.trustedName) {
                            mutate(state.transaction.direction, state.transaction.amount)
                        }
                        val finalBalance = wallet.withWallet(server, state.transaction.subjectUuid, inspection.trustedName) { balance() }
                        ManualResult(started, applied, finalBalance, null)
                    } catch (error: Throwable) {
                        ManualResult(started, false, null, error.javaClass.simpleName)
                    }
                }
            }.thenCompose { result ->
                when {
                    result.error != null -> stateChange(result.state, AccountTransactionStatus.NEEDS_OP,
                        "OP_RETRY_UNKNOWN", result.error, null)
                    result.applied && result.balance == expected -> stateChange(result.state,
                        AccountTransactionStatus.SUCCEEDED, "OP_RETRY_APPLIED", null, result.balance)
                    !result.applied && result.balance == attempt.balanceBefore -> stateChange(result.state,
                        AccountTransactionStatus.RESOLVED, "OP_RETRY_REJECTED", null, result.balance)
                    else -> stateChange(result.state, AccountTransactionStatus.NEEDS_OP,
                        "OP_RETRY_UNEXPECTED_BALANCE", null, result.balance)
                }
            }
        }
    }

    private fun stateChange(
        state: AccountTransactionState,
        status: AccountTransactionStatus,
        stage: String,
        reason: String?,
        balance: Long?
    ): CompletableFuture<AccountTransactionState> = store.changeState(AccountFact.StateChanged(
        state.transaction.transactionId,
        status,
        stage,
        reason,
        state.retryCount,
        null,
        balance
    ))

    private fun record(
        inspection: AccountInspection,
        actorName: String,
        action: ManualAccountAction,
        result: String
    ): CompletableFuture<Unit> = audit.append(AccountAuditRecord(
        inspection.state.transaction.transactionId,
        System.currentTimeMillis(),
        actorName,
        action.name,
        inspection.currentBalance,
        result
    ))

    private fun <T> serverCall(action: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        val task = Runnable {
            try { result.complete(action()) } catch (error: Throwable) { result.completeExceptionally(error) }
        }
        if (server.isSameThread) task.run() else server.execute(task)
        return result
    }

    private data class ManualResult(
        val state: AccountTransactionState,
        val applied: Boolean,
        val balance: Long?,
        val error: String?
    )
}
