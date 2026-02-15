package com.imyvm.community.application.permission

import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.AdministrationPermission
import com.imyvm.community.domain.community.CommunityStatus
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

object PermissionCheck {

    sealed class PermissionResult {
        object Allowed : PermissionResult()
        data class Denied(val reasonKey: String, val args: Array<out Any> = emptyArray()) : PermissionResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Denied

                if (reasonKey != other.reasonKey) return false
                if (!args.contentEquals(other.args)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = reasonKey.hashCode()
                result = 31 * result + args.contentHashCode()
                return result
            }
        }

        fun isAllowed(): Boolean = this is Allowed
        fun isDenied(): Boolean = this is Denied

        fun sendFeedback(player: ServerPlayerEntity) {
            if (this is Denied) {
                player.sendMessage(Translator.tr(reasonKey, *args))
            }
        }
    }

    fun canTransferOwnership(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (executorRole != MemberRoleType.OWNER) {
            return PermissionResult.Denied("community.permission.error.owner_only")
        }

        if (executor.uuid == targetUUID) {
            return PermissionResult.Denied("community.permission.error.owner_self")
        }

        val targetRole = community.getMemberRole(targetUUID)
            ?: return PermissionResult.Denied("community.permission.error.target_not_member")

        if (targetRole == MemberRoleType.APPLICANT || targetRole == MemberRoleType.REFUSED) {
            return PermissionResult.Denied("community.permission.error.invalid_target_role")
        }

        return PermissionResult.Allowed
    }

    fun canToggleCouncil(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        return if (executorRole == MemberRoleType.OWNER) {
            PermissionResult.Allowed
        } else {
            PermissionResult.Denied("community.permission.error.owner_only")
        }
    }

    fun canTogglePermissions(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        return if (executorRole == MemberRoleType.OWNER) {
            PermissionResult.Allowed
        } else {
            PermissionResult.Denied("community.permission.error.owner_only")
        }
    }

    fun canRenameCommunity(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        return if (executorRole == MemberRoleType.OWNER) {
            PermissionResult.Allowed
        } else {
            PermissionResult.Denied("community.permission.error.owner_only")
        }
    }

    fun canExecuteAdministration(
        executor: ServerPlayerEntity,
        community: Community,
        permission: AdministrationPermission? = null
    ): PermissionResult {
        if (isCommunityRevoked(community)) {
            return PermissionResult.Denied("community.permission.error.revoked")
        }

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        return when (executorRole) {
            MemberRoleType.OWNER -> PermissionResult.Allowed

            MemberRoleType.ADMIN -> {
                if (!isCommunityActive(community)) {
                    return PermissionResult.Denied("community.permission.error.not_active")
                }
                if (permission != null && !community.administrationPermissions.isEnabledForAdmin(permission)) {
                    return PermissionResult.Denied("community.permission.error.permission_disabled", arrayOf(permission.displayKey))
                }
                PermissionResult.Allowed
            }

            MemberRoleType.MEMBER -> PermissionResult.Denied("community.permission.error.insufficient")
            MemberRoleType.APPLICANT -> PermissionResult.Denied("community.permission.error.applicant")
            MemberRoleType.REFUSED -> PermissionResult.Denied("community.permission.error.refused")
        }
    }

    fun canAccessCouncil(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        val memberAccount = community.member[executor.uuid]
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (!memberAccount.isCouncilMember) {
            return PermissionResult.Denied("community.permission.error.not_council_member")
        }

        if (!community.council.enabled) {
            return PermissionResult.Denied("community.permission.error.council_disabled")
        }

        if (!isCommunityActive(community)) {
            return PermissionResult.Denied("community.permission.error.not_active")
        }

        return PermissionResult.Allowed
    }

    fun canManageMember(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        if (!isCommunityActive(community)) {
            return PermissionResult.Denied("community.permission.error.not_active")
        }

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        val targetRole = community.getMemberRole(targetUUID)
            ?: return PermissionResult.Denied("community.permission.error.target_not_member")

        if (!canManageByRole(executorRole, targetRole)) {
            return PermissionResult.Denied("community.permission.error.cannot_manage_role")
        }

        if (!canManageByPrivilege(executor, community, targetUUID, executorRole)) {
            return PermissionResult.Denied("community.permission.error.cannot_manage_privileged")
        }

        if (executor.uuid == targetUUID && executorRole != MemberRoleType.OWNER) {
            return PermissionResult.Denied("community.permission.error.cannot_manage_self")
        }

        return PermissionResult.Allowed
    }

    fun canPromoteMember(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        val manageResult = canManageMember(executor, community, targetUUID)
        if (manageResult.isDenied()) return manageResult

        val targetRole = community.getMemberRole(targetUUID)!!
        if (targetRole != MemberRoleType.MEMBER) {
            return PermissionResult.Denied("community.permission.error.already_promoted")
        }

        return PermissionResult.Allowed
    }

    fun canDemoteMember(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        val manageResult = canManageMember(executor, community, targetUUID)
        if (manageResult.isDenied()) return manageResult

        val targetRole = community.getMemberRole(targetUUID)!!
        if (targetRole != MemberRoleType.ADMIN) {
            return PermissionResult.Denied("community.permission.error.not_admin")
        }

        return PermissionResult.Allowed
    }

    fun canRemoveMember(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        val manageResult = canManageMember(executor, community, targetUUID)
        if (manageResult.isDenied()) return manageResult

        val executorRole = community.getMemberRole(executor.uuid)!!
        if (executorRole == MemberRoleType.OWNER && executor.uuid == targetUUID) {
            return PermissionResult.Denied("community.permission.error.owner_cannot_remove_self")
        }

        return PermissionResult.Allowed
    }

    fun canAuditApplications(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        return when (executorRole) {
            MemberRoleType.OWNER -> PermissionResult.Allowed
            MemberRoleType.ADMIN -> {
                if (isCommunityActive(community) &&
                    community.administrationPermissions.isEnabledForAdmin(AdministrationPermission.AUDIT_APPLICATIONS)
                ) {
                    PermissionResult.Allowed
                } else {
                    PermissionResult.Denied("community.permission.error.permission_disabled", 
                        arrayOf(AdministrationPermission.AUDIT_APPLICATIONS.displayKey))
                }
            }
            else -> PermissionResult.Denied("community.permission.error.insufficient")
        }
    }

    fun canAcceptApplicant(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        val auditResult = canAuditApplications(executor, community)
        if (auditResult.isDenied()) return auditResult

        val targetRole = community.getMemberRole(targetUUID)
            ?: return PermissionResult.Denied("community.permission.error.target_not_member")

        if (targetRole != MemberRoleType.APPLICANT) {
            return PermissionResult.Denied("community.permission.error.not_applicant")
        }

        return PermissionResult.Allowed
    }

    fun canRefuseApplicant(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        return canAcceptApplicant(executor, community, targetUUID)
    }

    fun canChangeJoinPolicy(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (isProtoCommunity(community)) {
            return if (executorRole == MemberRoleType.OWNER || executorRole == MemberRoleType.ADMIN) {
                PermissionResult.Allowed
            } else {
                PermissionResult.Denied("community.permission.error.proto_only_owner_admin")
            }
        }

        return canExecuteAdministration(executor, community, AdministrationPermission.CHANGE_JOIN_POLICY)
    }

    fun canQuitCommunity(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (executorRole == MemberRoleType.OWNER) {
            return PermissionResult.Denied("community.permission.error.owner_cannot_quit")
        }

        if (executorRole == MemberRoleType.APPLICANT || executorRole == MemberRoleType.REFUSED) {
            return PermissionResult.Denied("community.permission.error.applicant_refused_cannot_quit")
        }

        if (!isProtoCommunity(community) && !isCommunityActive(community)) {
            return PermissionResult.Denied("community.permission.error.cannot_quit_status")
        }

        if (isCommunityRevoked(community)) {
            return PermissionResult.Denied("community.permission.error.revoked")
        }

        return PermissionResult.Allowed
    }

    fun canViewCommunity(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (executorRole == MemberRoleType.APPLICANT) {
            return PermissionResult.Denied("community.permission.error.applicant")
        }

        if (executorRole == MemberRoleType.REFUSED) {
            return PermissionResult.Denied("community.permission.error.refused")
        }

        if (isCommunityRevoked(community)) {
            return PermissionResult.Denied("community.permission.error.revoked")
        }

        return PermissionResult.Allowed
    }

    fun canDonate(executor: ServerPlayerEntity, community: Community): PermissionResult {
        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (executorRole == MemberRoleType.APPLICANT || executorRole == MemberRoleType.REFUSED) {
            return PermissionResult.Denied("community.permission.error.cannot_donate")
        }

        if (!isCommunityActive(community)) {
            return PermissionResult.Denied("community.permission.error.not_active")
        }

        return PermissionResult.Allowed
    }

    private fun canManageByRole(executorRole: MemberRoleType, targetRole: MemberRoleType): Boolean {
        return when (executorRole) {
            MemberRoleType.OWNER -> true
            MemberRoleType.ADMIN -> targetRole == MemberRoleType.MEMBER
            else -> false
        }
    }

    private fun canManageByPrivilege(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID,
        executorRole: MemberRoleType
    ): Boolean {
        if (executorRole == MemberRoleType.OWNER) return true

        if (executorRole == MemberRoleType.ADMIN) {
            val targetAccount = community.member[targetUUID] ?: return false
            return !targetAccount.isCouncilMember && targetAccount.governorship == -1
        }

        return false
    }

    private fun isProtoCommunity(community: Community): Boolean {
        return community.status == CommunityStatus.RECRUITING_REALM ||
                community.status == CommunityStatus.PENDING_MANOR ||
                community.status == CommunityStatus.PENDING_REALM
    }

    private fun isCommunityActive(community: Community): Boolean {
        return community.status == CommunityStatus.ACTIVE_MANOR ||
                community.status == CommunityStatus.ACTIVE_REALM
    }

    private fun isCommunityRevoked(community: Community): Boolean {
        return community.status == CommunityStatus.REVOKED_MANOR ||
                community.status == CommunityStatus.REVOKED_REALM
    }

    inline fun <T> executeWithPermission(
        executor: ServerPlayerEntity,
        permissionCheck: () -> PermissionResult,
        action: () -> T
    ): T? {
        val result = permissionCheck()
        return if (result.isAllowed()) {
            action()
        } else {
            result.sendFeedback(executor)
            null
        }
    }
}
