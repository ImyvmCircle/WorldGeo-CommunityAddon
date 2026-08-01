package com.imyvm.community.domain.model

import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.util.UUID

class PendingOperationExecutionGuardTest {
    @Test
    fun rejectsExpiredChangedExecutorAndOrphanedCommunity() {
        val executor = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val other = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val community = community(42, CommunityStatus.ACTIVE_REALM, executor)
        val operation = PendingOperation(
            expireAt = 100L,
            type = PendingOperationType.RENAME_CONFIRMATION,
            renameData = RenameConfirmationData(42, "global", "New", executor, 0L)
        )

        assertNull(validatePendingExecution(operation, community, executor, now = 99L))
        assertEquals(PendingExecutionRejectReason.EXPIRED, validatePendingExecution(operation, community, executor, now = 101L))
        assertEquals(PendingExecutionRejectReason.EXECUTOR_CHANGED, validatePendingExecution(operation, community, other, now = 99L))
        assertEquals(PendingExecutionRejectReason.COMMUNITY_ORPHANED, validatePendingExecution(operation, community(null, CommunityStatus.ACTIVE_REALM, executor), executor, now = 99L))
        assertEquals(PendingExecutionRejectReason.COMMUNITY_REVOKED, validatePendingExecution(operation, community(42, CommunityStatus.REVOKED_REALM, executor), executor, now = 99L))
    }

    private fun community(regionId: Int?, status: CommunityStatus, owner: UUID) = Community(
        regionNumberId = regionId,
        member = hashMapOf(owner to MemberAccount(0L, MemberRoleType.OWNER)),
        joinPolicy = CommunityJoinPolicy.OPEN,
        status = status
    )
}
