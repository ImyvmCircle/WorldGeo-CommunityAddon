package com.imyvm.community.application.account

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.account.AccountAttempt
import com.imyvm.community.domain.model.account.AccountBalanceRules
import com.imyvm.community.domain.model.account.AccountExecutionRules
import com.imyvm.community.domain.model.account.RecoveryDecision
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionState
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.infra.account.AccountFact
import com.imyvm.community.infra.account.AccountTransactionStore
import com.imyvm.community.infra.account.CommunityDataWriter
import com.imyvm.community.infra.account.PlayerIdentityDirectory
import com.imyvm.community.infra.economy.EconomyWalletAdapter
import net.minecraft.server.MinecraftServer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AccountTransactionService(
    private val server: MinecraftServer,
    private val store: AccountTransactionStore,
    private val writer: CommunityDataWriter,
    private val identities: PlayerIdentityDirectory,
    private val walletAdapter: EconomyWalletAdapter,
    private val scheduler: ScheduledThreadPoolExecutor
) {
    private val activeAccounts = HashSet<UUID>()
    private val scheduledRetries = AtomicInteger()
    private val observers = LinkedHashMap<UUID, (AccountTransactionState) -> Unit>()

    fun submit(transaction: AccountTransaction): CompletableFuture<AccountTransactionState> =
        submit(transaction, null)

    fun submit(
        transaction: AccountTransaction,
        observer: ((AccountTransactionState) -> Unit)?
    ): CompletableFuture<AccountTransactionState> {
        val result = CompletableFuture<AccountTransactionState>()
        store.determine(transaction).whenComplete { state, error ->
            if (error != null) {
                result.completeExceptionally(error)
            } else {
                result.complete(state)
                onServer {
                    if (observer != null) {
                        if (state.status.isTerminal()) observer(state)
                        else if (observers.size < MAX_OBSERVERS) observers[state.transaction.transactionId] = observer
                    }
                    kickAccount(state.transaction.subjectUuid)
                }
            }
        }
        return result
    }

    fun recover() {
        scanRecoveryPage(null)
    }

    fun identityAvailable(subjectUuid: UUID) {
        onServer { kickAccount(subjectUuid) }
    }

    fun kick(subjectUuid: UUID) {
        onServer { kickAccount(subjectUuid) }
    }

    fun terminalUpdated(state: AccountTransactionState) {
        onServer {
            if (state.status.isTerminal()) observers.remove(state.transaction.transactionId)?.invoke(state)
            kickAccount(state.transaction.subjectUuid)
        }
    }

    private fun scanRecoveryPage(token: String?) {
        store.scanUnresolved(token, RECOVERY_PAGE_SIZE).whenComplete { page, error ->
            if (error != null) {
                WorldGeoCommunityAddon.logger.error("Failed to scan unresolved account transactions", error)
                return@whenComplete
            }
            onServer {
                page.items.map { it.transaction.subjectUuid }.distinct().forEach(::kickAccount)
            }
            if (page.items.size == RECOVERY_PAGE_SIZE && page.nextToken != null) scanRecoveryPage(page.nextToken)
        }
    }

    private fun kickAccount(subjectUuid: UUID) {
        check(server.isSameThread)
        if (!activeAccounts.add(subjectUuid)) return
        findFirstUnresolved(subjectUuid, null)
    }

    private fun findFirstUnresolved(subjectUuid: UUID, token: String?) {
        store.scanAccountOrder(subjectUuid, token, ACCOUNT_PAGE_SIZE).whenComplete { page, error ->
            onServer {
                if (error != null) {
                    activeAccounts.remove(subjectUuid)
                    WorldGeoCommunityAddon.logger.error("Failed to read account order for $subjectUuid", error)
                    return@onServer
                }
                val unresolved = page.items.firstOrNull { !it.status.isTerminal() }
                when {
                    unresolved != null -> resume(unresolved)
                    page.items.size == ACCOUNT_PAGE_SIZE && page.nextToken != null ->
                        findFirstUnresolved(subjectUuid, page.nextToken)
                    else -> activeAccounts.remove(subjectUuid)
                }
            }
        }
    }

    private fun resume(state: AccountTransactionState) {
        if (state.status == AccountTransactionStatus.NEEDS_OP) {
            activeAccounts.remove(state.transaction.subjectUuid)
            return
        }
        val retryAt = state.nextRetryAtMillis
        if (state.status == AccountTransactionStatus.PENDING && retryAt != null && retryAt > System.currentTimeMillis()) {
            scheduleRetry(state, retryAt - System.currentTimeMillis())
            return
        }
        resolveIdentity(state)
    }

    private fun resolveIdentity(state: AccountTransactionState) {
        val online = server.playerList.getPlayer(state.transaction.subjectUuid)
        if (online != null) {
            execute(state, online.gameProfile.name)
            return
        }
        writer.submit { identities.find(state.transaction.subjectUuid) }.whenComplete { identity, error ->
            onServer {
                when {
                    error != null -> safeFailure(state, "IDENTITY_READ", error)
                    identity == null -> waitForIdentity(state)
                    else -> execute(state, identity.trustedName)
                }
            }
        }
    }

    private fun waitForIdentity(state: AccountTransactionState) {
        if (state.status == AccountTransactionStatus.WAITING_IDENTITY) {
            activeAccounts.remove(state.transaction.subjectUuid)
            return
        }
        changeState(state, AccountTransactionStatus.WAITING_IDENTITY, "IDENTITY", "Trusted player name unavailable") {
            activeAccounts.remove(state.transaction.subjectUuid)
        }
    }

    private fun execute(state: AccountTransactionState, trustedName: String) {
        check(server.isSameThread)
        try {
            walletAdapter.withWallet(server, state.transaction.subjectUuid, trustedName) {
                val balance = balance()
                when (AccountExecutionRules.recoveryDecision(state, balance)) {
                    RecoveryDecision.SUCCEEDED -> succeed(state, balance)
                    RecoveryDecision.NEEDS_OP -> needsOp(state, "UNKNOWN_CALL_RESULT", balance)
                    RecoveryDecision.PROCEED -> executeNewAttempt(state, trustedName, balance)
                }
            }
        } catch (error: Throwable) {
            if (!AccountExecutionRules.canRetrySafely(state, RETRY_DELAYS_SECONDS.size)) {
                needsOp(state, "RECOVERY_BALANCE_READ:${error.javaClass.simpleName}", null)
            } else {
                safeFailure(state, "BALANCE_READ", error)
            }
        }
    }

    private fun executeNewAttempt(state: AccountTransactionState, trustedName: String, balanceBefore: Long) {
        val expected = try {
            AccountBalanceRules.expected(balanceBefore, state.transaction.direction, state.transaction.amount)
        } catch (error: IllegalArgumentException) {
            changeState(state, AccountTransactionStatus.RESOLVED, "BUSINESS_REJECTED", error.message, balanceBefore) {
                processNext(state.transaction.subjectUuid)
            }
            return
        } catch (error: ArithmeticException) {
            changeState(state, AccountTransactionStatus.RESOLVED, "BALANCE_OVERFLOW", error.message, balanceBefore) {
                processNext(state.transaction.subjectUuid)
            }
            return
        }
        val attempt = AccountAttempt(UUID.randomUUID(), System.currentTimeMillis(), balanceBefore, expected)
        store.recordAttempt(state.transaction.transactionId, attempt).whenComplete { attempted, recordError ->
            onServer {
                if (recordError != null) {
                    safeFailure(state, "ATTEMPT_RECORD", recordError)
                    return@onServer
                }
                beforeCall(attempted, attempt, trustedName)
            }
        }
    }

    private fun beforeCall(state: AccountTransactionState, attempt: AccountAttempt, trustedName: String) {
        val currentBalance = try {
            walletAdapter.withWallet(server, state.transaction.subjectUuid, trustedName) { balance() }
        } catch (error: Throwable) {
            safeFailure(state, "PRE_CALL_BALANCE_READ", error)
            return
        }
        if (currentBalance != attempt.balanceBefore) {
            executeNewAttempt(state, trustedName, currentBalance)
            return
        }
        store.recordCallStarted(state.transaction.transactionId, attempt.attemptId, System.currentTimeMillis())
            .whenComplete { started, recordError ->
                onServer {
                    if (recordError != null) {
                        safeFailure(state, "CALL_BOUNDARY_RECORD", recordError)
                        return@onServer
                    }
                    callWallet(started, trustedName)
                }
            }
    }

    private fun callWallet(state: AccountTransactionState, trustedName: String) {
        try {
            val applied = walletAdapter.withWallet(server, state.transaction.subjectUuid, trustedName) {
                mutate(state.transaction.direction, state.transaction.amount)
            }
            val balance = walletAdapter.withWallet(server, state.transaction.subjectUuid, trustedName) { balance() }
            when {
                applied && balance == state.attempts.last().expectedBalance -> succeed(state, balance)
                !applied && balance == state.attempts.last().balanceBefore ->
                    changeState(state, AccountTransactionStatus.RESOLVED, "API_REJECTED", "Wallet rejected mutation", balance) {
                        processNext(state.transaction.subjectUuid)
                    }
                else -> needsOp(state, "UNEXPECTED_FINAL_BALANCE", balance)
            }
        } catch (error: Throwable) {
            needsOp(state, "UNKNOWN_CALL_RESULT:${error.javaClass.simpleName}", null)
        }
    }

    private fun succeed(state: AccountTransactionState, balance: Long) {
        changeState(state, AccountTransactionStatus.SUCCEEDED, finalBalance = balance) {
            processNext(state.transaction.subjectUuid)
        }
    }

    private fun needsOp(state: AccountTransactionState, reason: String, balance: Long?) {
        changeState(state, AccountTransactionStatus.NEEDS_OP, "RECONCILIATION", reason, balance) {
            activeAccounts.remove(state.transaction.subjectUuid)
        }
    }

    private fun safeFailure(state: AccountTransactionState, stage: String, error: Throwable) {
        if (!AccountExecutionRules.canRetrySafely(state, RETRY_DELAYS_SECONDS.size)) {
            needsOp(state, "$stage:${error.javaClass.simpleName}", null)
            return
        }
        val delaySeconds = RETRY_DELAYS_SECONDS[state.retryCount]
        val nextRetryAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delaySeconds)
        val fact = AccountFact.StateChanged(
            state.transaction.transactionId,
            AccountTransactionStatus.PENDING,
            stage,
            error.javaClass.simpleName,
            state.retryCount + 1,
            nextRetryAt
        )
        store.changeState(fact).whenComplete { pending, persistError ->
            onServer {
                if (persistError != null) {
                    activeAccounts.remove(state.transaction.subjectUuid)
                    WorldGeoCommunityAddon.logger.error("Failed to persist account retry ${state.transaction.shortId}", persistError)
                    return@onServer
                }
                scheduleRetry(pending, TimeUnit.SECONDS.toMillis(delaySeconds))
            }
        }
    }

    private fun scheduleRetry(state: AccountTransactionState, delayMillis: Long) {
        if (scheduledRetries.incrementAndGet() > MAX_SCHEDULED_RETRIES) {
            scheduledRetries.decrementAndGet()
            needsOp(state, "RETRY_QUEUE_FULL", null)
            return
        }
        scheduler.schedule({
            scheduledRetries.decrementAndGet()
            continueScheduled(state.transaction.transactionId, state.transaction.subjectUuid)
        }, delayMillis, TimeUnit.MILLISECONDS)
    }

    private fun continueScheduled(transactionId: UUID, subjectUuid: UUID) {
        store.find(transactionId).whenComplete { latest, error ->
            onServer {
                when {
                    error != null -> {
                        activeAccounts.remove(subjectUuid)
                        WorldGeoCommunityAddon.logger.error("Failed to reload scheduled account transaction", error)
                    }
                    latest == null -> activeAccounts.remove(subjectUuid)
                    latest.status.isTerminal() -> processNext(subjectUuid)
                    latest.status == AccountTransactionStatus.NEEDS_OP -> activeAccounts.remove(subjectUuid)
                    else -> resolveIdentity(latest)
                }
            }
        }
    }

    private fun processNext(subjectUuid: UUID) {
        check(server.isSameThread)
        findFirstUnresolved(subjectUuid, null)
    }

    private fun changeState(
        state: AccountTransactionState,
        status: AccountTransactionStatus,
        stage: String? = null,
        reason: String? = null,
        finalBalance: Long? = null,
        completed: () -> Unit
    ) {
        store.changeState(AccountFact.StateChanged(
            state.transaction.transactionId,
            status,
            stage,
            reason,
            state.retryCount,
            null,
            finalBalance
        )).whenComplete { updated, error ->
            onServer {
                if (error != null) {
                    activeAccounts.remove(state.transaction.subjectUuid)
                    WorldGeoCommunityAddon.logger.error("Failed to persist account state ${state.transaction.shortId}", error)
                } else {
                    if (updated.status.isTerminal()) observers.remove(updated.transaction.transactionId)?.invoke(updated)
                    completed()
                }
            }
        }
    }

    private fun onServer(action: () -> Unit) {
        if (server.isSameThread) action() else server.execute(action)
    }

    companion object {
        private const val RECOVERY_PAGE_SIZE = 128
        private const val ACCOUNT_PAGE_SIZE = 128
        private const val MAX_SCHEDULED_RETRIES = 256
        private const val MAX_OBSERVERS = 256
        private val RETRY_DELAYS_SECONDS = longArrayOf(10, 60, 300)
    }
}
