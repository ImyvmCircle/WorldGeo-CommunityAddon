package com.imyvm.community.domain.model

import com.imyvm.community.domain.model.community.CommunityStatus
import java.util.UUID

enum class PendingExecutionRejectReason {
    EXPIRED,
    EXECUTOR_CHANGED,
    COMMUNITY_ORPHANED,
    COMMUNITY_REVOKED
}

fun validatePendingExecution(
    operation: PendingOperation,
    community: Community,
    executorUuid: UUID,
    now: Long = System.currentTimeMillis()
): PendingExecutionRejectReason? {
    if (now > operation.expireAt) return PendingExecutionRejectReason.EXPIRED
    val expectedExecutor = operation.creationData?.creatorUUID
        ?: operation.modificationData?.executorUUID
        ?: operation.teleportPointData?.executorUUID
        ?: operation.settingData?.executorUUID
        ?: operation.renameData?.executorUUID
        ?: operation.transferData?.executorUUID
        ?: operation.treasuryGrantData?.executorUUID
        ?: operation.inviterUUID
    if (expectedExecutor != null && expectedExecutor != executorUuid) return PendingExecutionRejectReason.EXECUTOR_CHANGED
    if (community.regionNumberId == null) return PendingExecutionRejectReason.COMMUNITY_ORPHANED
    if (community.status == CommunityStatus.REVOKED_MANOR || community.status == CommunityStatus.REVOKED_REALM) {
        return PendingExecutionRejectReason.COMMUNITY_REVOKED
    }
    return null
}
