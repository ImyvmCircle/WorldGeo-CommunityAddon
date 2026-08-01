package com.imyvm.community.application.account

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.TreasuryMutationResult
import com.imyvm.community.domain.model.TreasuryReferenceRecord
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.domain.model.transaction.TreasuryLedgerFact
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.account.AccountSubsystem
import java.nio.charset.StandardCharsets
import java.util.UUID

fun mutateTreasury(
    community: Community,
    amount: Long,
    direction: ResourceDirection,
    source: String,
    externalReference: String,
    operationType: String,
    objectReference: String,
    descriptionKey: String? = null,
    descriptionArgs: List<String> = emptyList()
): Result<TreasuryMutationResult> {
    val regionId = community.regionNumberId
        ?: return Result.failure(IllegalStateException("community region not bound"))
    if (amount <= 0L) return Result.failure(IllegalArgumentException("amount must be positive"))
    val beforeBalance = community.treasuryBalance
    val beforeReference = community.treasuryReferences[externalReference]
    val record = TreasuryReferenceRecord(externalReference, amount, direction, source, operationType, objectReference)
    return try {
        val result = community.applyTreasuryMutation(record)
        if (result == TreasuryMutationResult.APPLIED) {
            CommunityDatabase.save()
            appendTreasuryLedgerEntry(regionId, amount, direction, operationType, source, objectReference, descriptionKey, descriptionArgs, externalReference)
        }
        Result.success(result)
    } catch (error: Exception) {
        community.treasuryBalance = beforeBalance
        if (beforeReference == null) community.treasuryReferences.remove(externalReference)
        else community.treasuryReferences[externalReference] = beforeReference
        Result.failure(error)
    }
}

internal fun appendTreasuryLedgerEntry(
    regionId: Int,
    amount: Long,
    direction: ResourceDirection,
    operationType: String,
    source: String,
    objectRef: String,
    descriptionKey: String? = null,
    descriptionArgs: List<String> = emptyList(),
    externalReference: String = "community:$operationType:${UUID.randomUUID()}"
) {
    val runtime = AccountSubsystem.runtimeOrNull() ?: return
    val factId = UUID.nameUUIDFromBytes(externalReference.toByteArray(StandardCharsets.UTF_8))
    runtime.sharedStore.append(
        TreasuryLedgerFact(
            factId, regionId, System.currentTimeMillis(), amount, direction,
            source, externalReference,
            operationType, objectRef, descriptionKey, descriptionArgs
        )
    )
}
