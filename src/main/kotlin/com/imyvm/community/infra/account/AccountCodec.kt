package com.imyvm.community.infra.account

import com.imyvm.community.domain.model.account.AccountAttempt
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionState
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object AccountCodec {
    private const val MAX_STRING_BYTES = 64 * 1024
    private const val MAX_ATTEMPTS = 1_000

    fun encodeFact(fact: AccountFact): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            when (fact) {
                is AccountFact.Determined -> {
                    output.writeByte(1)
                    writeTransaction(output, fact.transaction)
                }
                is AccountFact.Attempted -> {
                    output.writeByte(2)
                    writeUuid(output, fact.transactionId)
                    writeAttempt(output, fact.attempt)
                }
                is AccountFact.CallStarted -> {
                    output.writeByte(3)
                    writeUuid(output, fact.transactionId)
                    writeUuid(output, fact.attemptId)
                    output.writeLong(fact.startedAtMillis)
                }
                is AccountFact.StateChanged -> {
                    output.writeByte(4)
                    writeUuid(output, fact.transactionId)
                    output.writeInt(fact.status.ordinal)
                    writeNullableString(output, fact.failureStage)
                    writeNullableString(output, fact.failureReason)
                    output.writeInt(fact.retryCount)
                    writeNullableLong(output, fact.nextRetryAtMillis)
                    writeNullableLong(output, fact.finalBalance)
                }
            }
        }
    }.toByteArray()

    fun decodeFact(payload: ByteArray): AccountFact = DataInputStream(ByteArrayInputStream(payload)).use { input ->
        val fact = when (input.readUnsignedByte()) {
            1 -> AccountFact.Determined(readTransaction(input))
            2 -> AccountFact.Attempted(readUuid(input), readAttempt(input))
            3 -> AccountFact.CallStarted(readUuid(input), readUuid(input), input.readLong())
            4 -> AccountFact.StateChanged(
                transactionId = readUuid(input),
                status = enumValue(input.readInt(), AccountTransactionStatus.entries),
                failureStage = readNullableString(input),
                failureReason = readNullableString(input),
                retryCount = input.readInt().also { require(it >= 0) },
                nextRetryAtMillis = readNullableLong(input),
                finalBalance = readNullableLong(input)
            )
            else -> error("Unsupported account fact type")
        }
        require(input.available() == 0) { "Unread account fact bytes" }
        fact
    }

    fun encodeState(state: AccountTransactionState): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(1)
            writeTransaction(output, state.transaction)
            output.writeInt(state.status.ordinal)
            require(state.attempts.size <= MAX_ATTEMPTS)
            output.writeInt(state.attempts.size)
            state.attempts.forEach { writeAttempt(output, it) }
            writeNullableString(output, state.failureStage)
            writeNullableString(output, state.failureReason)
            output.writeInt(state.retryCount)
            writeNullableLong(output, state.nextRetryAtMillis)
            writeNullableLong(output, state.finalBalance)
        }
    }.toByteArray()

    fun decodeState(payload: ByteArray): AccountTransactionState = DataInputStream(ByteArrayInputStream(payload)).use { input ->
        require(input.readInt() == 1) { "Unsupported account state version" }
        val transaction = readTransaction(input)
        val status = enumValue(input.readInt(), AccountTransactionStatus.entries)
        val attemptCount = input.readInt()
        require(attemptCount in 0..MAX_ATTEMPTS) { "Invalid account attempt count" }
        val attempts = List(attemptCount) { readAttempt(input) }
        val state = AccountTransactionState(
            transaction = transaction,
            status = status,
            attempts = attempts,
            failureStage = readNullableString(input),
            failureReason = readNullableString(input),
            retryCount = input.readInt().also { require(it >= 0) },
            nextRetryAtMillis = readNullableLong(input),
            finalBalance = readNullableLong(input)
        )
        require(input.available() == 0) { "Unread account state bytes" }
        state
    }

    private fun writeTransaction(output: DataOutputStream, transaction: AccountTransaction) {
        writeUuid(output, transaction.transactionId)
        writeString(output, transaction.shortId)
        output.writeLong(transaction.createdAtMillis)
        writeString(output, transaction.periodKey)
        writeUuid(output, transaction.subjectUuid)
        writeNullableString(output, transaction.subjectName)
        output.writeLong(transaction.amount)
        output.writeInt(transaction.direction.ordinal)
        writeString(output, transaction.source)
        writeString(output, transaction.externalReference)
        writeNullableUuid(output, transaction.previousTransactionId)
    }

    private fun readTransaction(input: DataInputStream): AccountTransaction = AccountTransaction(
        transactionId = readUuid(input),
        shortId = readString(input),
        createdAtMillis = input.readLong(),
        periodKey = readString(input),
        subjectUuid = readUuid(input),
        subjectName = readNullableString(input),
        amount = input.readLong().also { require(it > 0) },
        direction = enumValue(input.readInt(), AccountDirection.entries),
        source = readString(input),
        externalReference = readString(input),
        previousTransactionId = readNullableUuid(input)
    )

    private fun writeAttempt(output: DataOutputStream, attempt: AccountAttempt) {
        writeUuid(output, attempt.attemptId)
        output.writeLong(attempt.attemptedAtMillis)
        output.writeLong(attempt.balanceBefore)
        output.writeLong(attempt.expectedBalance)
        writeNullableLong(output, attempt.callStartedAtMillis)
    }

    private fun readAttempt(input: DataInputStream): AccountAttempt = AccountAttempt(
        attemptId = readUuid(input),
        attemptedAtMillis = input.readLong(),
        balanceBefore = input.readLong(),
        expectedBalance = input.readLong(),
        callStartedAtMillis = readNullableLong(input)
    )

    private fun writeString(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "Account string is too long" }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val length = input.readInt()
        require(length in 0..MAX_STRING_BYTES) { "Invalid account string length" }
        val bytes = input.readNBytes(length)
        require(bytes.size == length) { "Truncated account string" }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun writeNullableString(output: DataOutputStream, value: String?) {
        output.writeBoolean(value != null)
        if (value != null) writeString(output, value)
    }

    private fun readNullableString(input: DataInputStream): String? = if (input.readBoolean()) readString(input) else null

    private fun writeUuid(output: DataOutputStream, value: UUID) {
        output.writeLong(value.mostSignificantBits)
        output.writeLong(value.leastSignificantBits)
    }

    private fun readUuid(input: DataInputStream): UUID = UUID(input.readLong(), input.readLong())

    private fun writeNullableUuid(output: DataOutputStream, value: UUID?) {
        output.writeBoolean(value != null)
        if (value != null) writeUuid(output, value)
    }

    private fun readNullableUuid(input: DataInputStream): UUID? = if (input.readBoolean()) readUuid(input) else null

    private fun writeNullableLong(output: DataOutputStream, value: Long?) {
        output.writeBoolean(value != null)
        if (value != null) output.writeLong(value)
    }

    private fun readNullableLong(input: DataInputStream): Long? = if (input.readBoolean()) input.readLong() else null

    private fun <T> enumValue(ordinal: Int, values: List<T>): T {
        require(ordinal in values.indices) { "Invalid enum ordinal: $ordinal" }
        return values[ordinal]
    }
}
