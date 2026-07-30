package com.imyvm.community.domain.model.transaction

import java.util.UUID

sealed interface CommunityFact {
    val factId: UUID
    val regionId: Int
    val recordedAtMillis: Long
}

enum class ResourceDirection {
    CREDIT,
    DEBIT
}

enum class CombinationStepStatus {
    DETERMINED,
    CALL_STARTED,
    SUCCEEDED,
    PENDING,
    NEEDS_OP,
    COMPENSATED
}

data class CombinationStepFact(
    override val factId: UUID,
    override val regionId: Int,
    override val recordedAtMillis: Long,
    val operationId: UUID,
    val stepKey: String,
    val resource: String,
    val externalReference: String,
    val status: CombinationStepStatus,
    val evidence: String? = null
) : CommunityFact

data class TreasuryLedgerFact(
    override val factId: UUID,
    override val regionId: Int,
    override val recordedAtMillis: Long,
    val amount: Long,
    val direction: ResourceDirection,
    val source: String,
    val externalReference: String,
    val operationType: String,
    val objectReference: String,
    val descriptionKey: String? = null,
    val descriptionArgs: List<String> = emptyList()
) : CommunityFact

data class MemberLedgerFact(
    override val factId: UUID,
    override val regionId: Int,
    override val recordedAtMillis: Long,
    val memberUuid: UUID,
    val amount: Long,
    val direction: ResourceDirection,
    val source: String,
    val externalReference: String,
    val descriptionKey: String? = null,
    val descriptionArgs: List<String> = emptyList(),
    val countsAsContribution: Boolean = false
) : CommunityFact

data class CommunityAuditFact(
    override val factId: UUID,
    override val regionId: Int,
    override val recordedAtMillis: Long,
    val actorUuid: UUID?,
    val actorName: String,
    val action: String,
    val target: String,
    val result: String,
    val detail: String? = null
) : CommunityFact

data class PurposeCursorFact(
    override val factId: UUID,
    override val regionId: Int,
    override val recordedAtMillis: Long,
    val purpose: String,
    val consumerUnitType: String,
    val consumerUnit: String,
    val cursor: String
) : CommunityFact

data class CommunityFactPage(
    val items: List<CommunityFact>,
    val nextToken: String?
)

data class CommunityFactRootSummary(
    val appliedSequence: Long = 0L,
    val activeSegment: String = "facts-active.log",
    val firstIndexPage: String? = null,
    val lastIndexPage: String? = null
)
