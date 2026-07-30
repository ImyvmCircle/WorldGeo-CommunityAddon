package com.imyvm.community.infra.transaction

import com.imyvm.community.domain.model.transaction.CombinationStepFact
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import com.imyvm.community.domain.model.transaction.CommunityAuditFact
import com.imyvm.community.domain.model.transaction.CommunityFact
import com.imyvm.community.domain.model.transaction.MemberLedgerFact
import com.imyvm.community.domain.model.transaction.PurposeCursorFact
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.domain.model.transaction.TreasuryLedgerFact
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object CommunityFactCodec {
    fun encode(fact: CommunityFact): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeByte(typeOf(fact))
            writeUuid(output, fact.factId)
            output.writeInt(fact.regionId)
            output.writeLong(fact.recordedAtMillis)
            when (fact) {
                is CombinationStepFact -> {
                    writeUuid(output, fact.operationId)
                    writeString(output, fact.stepKey)
                    writeString(output, fact.resource)
                    writeString(output, fact.externalReference)
                    output.writeByte(fact.status.ordinal)
                    writeNullableString(output, fact.evidence)
                }
                is TreasuryLedgerFact -> {
                    output.writeLong(fact.amount)
                    output.writeByte(fact.direction.ordinal)
                    writeString(output, fact.source)
                    writeString(output, fact.externalReference)
                    writeString(output, fact.operationType)
                    writeString(output, fact.objectReference)
                    writeNullableString(output, fact.descriptionKey)
                    writeStrings(output, fact.descriptionArgs)
                }
                is MemberLedgerFact -> {
                    writeUuid(output, fact.memberUuid)
                    output.writeLong(fact.amount)
                    output.writeByte(fact.direction.ordinal)
                    writeString(output, fact.source)
                    writeString(output, fact.externalReference)
                    writeNullableString(output, fact.descriptionKey)
                    writeStrings(output, fact.descriptionArgs)
                }
                is CommunityAuditFact -> {
                    output.writeBoolean(fact.actorUuid != null)
                    fact.actorUuid?.let { writeUuid(output, it) }
                    writeString(output, fact.actorName)
                    writeString(output, fact.action)
                    writeString(output, fact.target)
                    writeString(output, fact.result)
                    writeNullableString(output, fact.detail)
                }
                is PurposeCursorFact -> {
                    writeString(output, fact.purpose)
                    writeString(output, fact.consumerUnitType)
                    writeString(output, fact.consumerUnit)
                    writeString(output, fact.cursor)
                }
            }
        }
    }.toByteArray()

    fun decode(payload: ByteArray): CommunityFact = DataInputStream(ByteArrayInputStream(payload)).use { input ->
        require(input.readInt() == VERSION) { "Unsupported community fact version" }
        val type = input.readUnsignedByte()
        val factId = readUuid(input)
        val regionId = input.readInt()
        val recordedAtMillis = input.readLong()
        val fact = when (type) {
            TYPE_STEP -> CombinationStepFact(
                factId, regionId, recordedAtMillis, readUuid(input), readString(input), readString(input),
                readString(input), readEnum(input, CombinationStepStatus.entries), readNullableString(input)
            )
            TYPE_TREASURY -> TreasuryLedgerFact(
                factId, regionId, recordedAtMillis, input.readLong(), readEnum(input, ResourceDirection.entries),
                readString(input), readString(input), readString(input), readString(input),
                readNullableString(input), readStrings(input)
            )
            TYPE_MEMBER -> MemberLedgerFact(
                factId, regionId, recordedAtMillis, readUuid(input), input.readLong(),
                readEnum(input, ResourceDirection.entries), readString(input), readString(input),
                readNullableString(input), readStrings(input)
            )
            TYPE_AUDIT -> CommunityAuditFact(
                factId, regionId, recordedAtMillis, if (input.readBoolean()) readUuid(input) else null,
                readString(input), readString(input), readString(input), readString(input), readNullableString(input)
            )
            TYPE_CURSOR -> PurposeCursorFact(
                factId, regionId, recordedAtMillis, readString(input), readString(input),
                readString(input), readString(input)
            )
            else -> error("Unknown community fact type: $type")
        }
        require(input.available() == 0) { "Unread community fact bytes" }
        fact
    }

    private fun typeOf(fact: CommunityFact): Int = when (fact) {
        is CombinationStepFact -> TYPE_STEP
        is TreasuryLedgerFact -> TYPE_TREASURY
        is MemberLedgerFact -> TYPE_MEMBER
        is CommunityAuditFact -> TYPE_AUDIT
        is PurposeCursorFact -> TYPE_CURSOR
    }

    private fun writeUuid(output: DataOutputStream, uuid: UUID) {
        output.writeLong(uuid.mostSignificantBits)
        output.writeLong(uuid.leastSignificantBits)
    }

    private fun readUuid(input: DataInputStream): UUID = UUID(input.readLong(), input.readLong())

    private fun writeString(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "Community fact string is too long" }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val length = input.readInt()
        require(length in 0..MAX_STRING_BYTES) { "Invalid community fact string length" }
        val bytes = input.readNBytes(length)
        require(bytes.size == length) { "Truncated community fact string" }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun writeNullableString(output: DataOutputStream, value: String?) {
        output.writeBoolean(value != null)
        value?.let { writeString(output, it) }
    }

    private fun readNullableString(input: DataInputStream): String? =
        if (input.readBoolean()) readString(input) else null

    private fun writeStrings(output: DataOutputStream, values: List<String>) {
        require(values.size <= MAX_ARGUMENTS) { "Too many community fact arguments" }
        output.writeInt(values.size)
        values.forEach { writeString(output, it) }
    }

    private fun readStrings(input: DataInputStream): List<String> {
        val size = input.readInt()
        require(size in 0..MAX_ARGUMENTS) { "Invalid community fact argument count" }
        return List(size) { readString(input) }
    }

    private fun <T> readEnum(input: DataInputStream, values: List<T>): T {
        val ordinal = input.readUnsignedByte()
        require(ordinal in values.indices) { "Invalid community fact enum value" }
        return values[ordinal]
    }

    private const val VERSION = 1
    private const val TYPE_STEP = 1
    private const val TYPE_TREASURY = 2
    private const val TYPE_MEMBER = 3
    private const val TYPE_AUDIT = 4
    private const val TYPE_CURSOR = 5
    private const val MAX_STRING_BYTES = 64 * 1024
    private const val MAX_ARGUMENTS = 1_000
}
