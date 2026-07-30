package com.imyvm.community.application.account

import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.domain.model.transaction.TreasuryLedgerFact
import com.imyvm.community.infra.account.AccountSubsystem
import java.util.UUID

internal fun appendTreasuryLedgerEntry(
    regionId: Int,
    amount: Long,
    direction: ResourceDirection,
    operationType: String,
    source: String,
    objectRef: String,
    descriptionKey: String? = null,
    descriptionArgs: List<String> = emptyList()
) {
    val runtime = AccountSubsystem.runtimeOrNull() ?: return
    val factId = UUID.randomUUID()
    runtime.sharedStore.append(
        TreasuryLedgerFact(
            factId, regionId, System.currentTimeMillis(), amount, direction,
            source, "community:$operationType:$factId",
            operationType, objectRef, descriptionKey, descriptionArgs
        )
    )
}
