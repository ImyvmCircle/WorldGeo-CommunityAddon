package com.imyvm.community.domain.policy.permission

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

object CommunityPermissionPolicy {

    fun canTransferOwnership(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")
        if (executorRole != MemberRoleType.OWNER) return PermissionResult.Denied("community.permission.error.owner_only")
        if (executor.uuid == targetUUID) return PermissionResult.Denied("community.permission.error.owner_self")

        val targetRole = community.getMemberRole(targetUUID)
            ?: return PermissionResult.Denied("community.permission.error.target_not_member")
        if (targetRole == MemberRoleType.APPLICANT || targetRole == MemberRoleType.REFUSED)
            return PermissionResult.Denied("community.permission.error.invalid_target_role")

        return PermissionResult.Allowed
    }

    fun canRenameCommunity(executor: ServerPlayerEntity, community: Community): PermissionResult =
        canExecuteAdministration(executor, community, AdminPrivilege.RENAME_COMMUNITY)

    fun canExecuteAdministration(
        executor: ServerPlayerEntity,
        community: Community,
        privilege: AdminPrivilege? = null
    ): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        return when (executorRole) {
            MemberRoleType.OWNER -> PermissionResult.Allowed

            MemberRoleType.ADMIN -> {
                if (!isActiveCommunity(community))
                    return PermissionResult.Denied("community.permission.error.not_active")
                if (privilege != null) {
                    val adminPrivileges = community.member[executor.uuid]?.adminPrivileges
                        ?: return PermissionResult.Denied("community.permission.error.permission_disabled", arrayOf(privilege.displayKey))
                    if (!adminPrivileges.isEnabled(privilege))
                        return PermissionResult.Denied("community.permission.error.permission_disabled", arrayOf(privilege.displayKey))
                }
                PermissionResult.Allowed
            }

            MemberRoleType.MEMBER    -> PermissionResult.Denied("community.permission.error.insufficient")
            MemberRoleType.APPLICANT -> PermissionResult.Denied("community.permission.error.applicant")
            MemberRoleType.REFUSED   -> PermissionResult.Denied("community.permission.error.refused")
        }
    }

    fun canExecuteOperationInProto(
        executor: ServerPlayerEntity,
        community: Community,
        privilege: AdminPrivilege
    ): PermissionResult {
        if (!isProtoCommunity(community)) return PermissionResult.Allowed

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        return when (privilege) {
            AdminPrivilege.MANAGE_MEMBERS ->
                if (executorRole == MemberRoleType.OWNER) PermissionResult.Allowed
                else PermissionResult.Denied("community.permission.error.proto_owner_only")
            else -> PermissionResult.Denied("community.permission.error.proto_restricted")
        }
    }

    fun canManageMember(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")
        if (!isActiveCommunity(community)) return PermissionResult.Denied("community.permission.error.not_active")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")
        val targetRole = community.getMemberRole(targetUUID)
            ?: return PermissionResult.Denied("community.permission.error.target_not_member")

        if (executor.uuid == targetUUID && executorRole != MemberRoleType.OWNER)
            return PermissionResult.Denied("community.permission.error.cannot_manage_self")

        if (!canManageByRole(executorRole, targetRole))
            return PermissionResult.Denied("community.permission.error.cannot_manage_role")

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
        if (targetRole != MemberRoleType.MEMBER)
            return PermissionResult.Denied("community.permission.error.already_promoted")

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
        if (targetRole != MemberRoleType.ADMIN)
            return PermissionResult.Denied("community.permission.error.not_admin")

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
        if (executorRole == MemberRoleType.OWNER && executor.uuid == targetUUID)
            return PermissionResult.Denied("community.permission.error.owner_cannot_remove_self")

        return PermissionResult.Allowed
    }

    fun canAuditApplications(executor: ServerPlayerEntity, community: Community): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (isProtoCommunity(community)) {
            return if (executorRole == MemberRoleType.OWNER || executorRole == MemberRoleType.ADMIN)
                PermissionResult.Allowed
            else
                PermissionResult.Denied("community.permission.error.proto_only_owner_admin")
        }

        return when (executorRole) {
            MemberRoleType.OWNER -> PermissionResult.Allowed
            MemberRoleType.ADMIN -> {
                if (!isActiveCommunity(community))
                    return PermissionResult.Denied("community.permission.error.not_active")
                val adminPrivileges = community.member[executor.uuid]?.adminPrivileges
                    ?: return PermissionResult.Denied("community.permission.error.permission_disabled",
                        arrayOf(AdminPrivilege.AUDIT_APPLICATIONS.displayKey))
                if (!adminPrivileges.isEnabled(AdminPrivilege.AUDIT_APPLICATIONS))
                    return PermissionResult.Denied("community.permission.error.permission_disabled",
                        arrayOf(AdminPrivilege.AUDIT_APPLICATIONS.displayKey))
                PermissionResult.Allowed
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
        if (targetRole != MemberRoleType.APPLICANT)
            return PermissionResult.Denied("community.permission.error.not_applicant")

        return PermissionResult.Allowed
    }

    fun canRefuseApplicant(
        executor: ServerPlayerEntity,
        community: Community,
        targetUUID: UUID
    ): PermissionResult = canAcceptApplicant(executor, community, targetUUID)

    fun canChangeJoinPolicy(executor: ServerPlayerEntity, community: Community): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (isProtoCommunity(community)) {
            return if (executorRole == MemberRoleType.OWNER || executorRole == MemberRoleType.ADMIN)
                PermissionResult.Allowed
            else
                PermissionResult.Denied("community.permission.error.proto_only_owner_admin")
        }

        return canExecuteAdministration(executor, community, AdminPrivilege.CHANGE_JOIN_POLICY)
    }

    fun canQuitCommunity(executor: ServerPlayerEntity, community: Community): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")

        if (!isProtoCommunity(community) && !isActiveCommunity(community))
            return PermissionResult.Denied("community.permission.error.cannot_quit_status")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (executorRole == MemberRoleType.OWNER)
            return PermissionResult.Denied("community.permission.error.owner_cannot_quit")
        if (executorRole == MemberRoleType.APPLICANT || executorRole == MemberRoleType.REFUSED)
            return PermissionResult.Denied("community.permission.error.applicant_refused_cannot_quit")

        return PermissionResult.Allowed
    }

    fun canViewCommunity(executor: ServerPlayerEntity, community: Community): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (executorRole == MemberRoleType.APPLICANT) return PermissionResult.Denied("community.permission.error.applicant")
        if (executorRole == MemberRoleType.REFUSED) return PermissionResult.Denied("community.permission.error.refused")

        return PermissionResult.Allowed
    }

    fun canDonate(executor: ServerPlayerEntity, community: Community): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        if (executorRole == MemberRoleType.APPLICANT || executorRole == MemberRoleType.REFUSED)
            return PermissionResult.Denied("community.permission.error.cannot_donate")
        if (!isActiveCommunity(community)) return PermissionResult.Denied("community.permission.error.not_active")

        return PermissionResult.Allowed
    }

    fun canManageAdminPrivileges(executor: ServerPlayerEntity, community: Community): PermissionResult {
        if (isRevokedCommunity(community)) return PermissionResult.Denied("community.permission.error.revoked")

        val executorRole = community.getMemberRole(executor.uuid)
            ?: return PermissionResult.Denied("community.permission.error.not_member")

        return if (executorRole == MemberRoleType.OWNER) PermissionResult.Allowed
        else PermissionResult.Denied("community.permission.error.owner_only")
    }

    private fun canManageByRole(executorRole: MemberRoleType, targetRole: MemberRoleType): Boolean =
        when (executorRole) {
            MemberRoleType.OWNER -> true
            MemberRoleType.ADMIN -> targetRole == MemberRoleType.MEMBER
            else -> false
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
            executor.closeHandledScreen()
            result.sendFeedback(executor)
            null
        }
    }
}
