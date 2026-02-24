package com.imyvm.community.application.interaction.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdminPrivilegeMenu
import com.imyvm.community.infra.CommunityDatabase
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun runOpenAdminPrivilegeMenu(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    targetUUID: UUID,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canManageAdminPrivileges(playerExecutor, community) }
    ) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            AdminPrivilegeMenu(
                syncId = syncId,
                playerExecutor = playerExecutor,
                community = community,
                targetUUID = targetUUID,
                runBack = runBack
            )
        }
    }
}

fun runToggleAdminPrivilege(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    targetUUID: UUID,
    privilege: AdminPrivilege,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canManageAdminPrivileges(playerExecutor, community) }
    ) {
        val privileges = community.member[targetUUID]?.adminPrivileges ?: return@executeWithPermission
        privileges.toggle(privilege)
        CommunityDatabase.save()
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            AdminPrivilegeMenu(
                syncId = syncId,
                playerExecutor = playerExecutor,
                community = community,
                targetUUID = targetUUID,
                runBack = runBack
            )
        }
    }
}
