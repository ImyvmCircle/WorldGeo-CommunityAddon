package com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.NotificationMenuAnvil
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.element.CommunityMemberMenu
import com.imyvm.community.util.Translator
import com.imyvm.community.util.Translator.trMenu
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity

fun runOpenPlayerRegionScopeChoice(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile,
    runBackGrandfather: (ServerPlayerEntity) -> Unit
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canManageMember(playerExecutor, community, playerObject.id) }
    ) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            CommunityRegionScopeMenu(
                syncId = syncId,
                playerExecutor = playerExecutor,
                community = community,
                geographicFunctionType = GeographicFunctionType.SETTING_ADJUSTMENT,
                playerObject = playerObject
            ) { runBackToMemberMenu(playerExecutor, community, playerObject, runBackGrandfather) }
        }
    }
}

fun runRemoveMember(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canRemoveMember(playerExecutor, community, playerObject.id) }
    ) {
        val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
        
        val targetNotification = com.imyvm.community.util.Translator.tr(
            "community.notification.target.removed",
            communityName,
            playerExecutor.name.string
        ) ?: net.minecraft.text.Text.literal("You were removed from $communityName by ${playerExecutor.name.string}")
        com.imyvm.community.application.interaction.common.notifyTargetPlayer(
            playerExecutor.server, playerObject.id, targetNotification, community
        )
        
        community.member.remove(playerObject.id)
        com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.revokeGrantedPermissions(playerObject.id, community)

        trMenu(
            playerExecutor,
            "community.member_management.remove.success",
            playerObject.name
        )
        
        val notification = com.imyvm.community.util.Translator.tr(
            "community.notification.member_removed",
            playerObject.name,
            playerExecutor.name.string,
            communityName
        ) ?: net.minecraft.text.Text.literal("${playerObject.name} was removed from $communityName by ${playerExecutor.name.string}")
        com.imyvm.community.application.interaction.common.notifyOfficials(community, playerExecutor.server, notification, playerExecutor)
        
        com.imyvm.community.infra.CommunityDatabase.save()
    }
}

fun runNotifyMember(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canManageMember(playerExecutor, community, playerObject.id) }
    ) {
        val handler = NotificationMenuAnvil(
            playerExecutor,
            initialName = Translator.tr("ui.community.administration.member.notify.to_edit")?.string ?: "(Edit your notification here)",
            playerObject = playerObject,
            community = community
        )
        handler.open()
    }
}

fun runPromoteMember(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile,
    governorship: Int = -1,
    isPromote: Boolean = true
) {
    if (isPromote) {
        if (governorship == -1) {
            handleRolePromotion(community, playerExecutor, playerObject)
        } else {
            handleGovernorshipUpdate(community, playerExecutor, playerObject, governorship)
        }
    } else {
        handleRoleDemotion(community, playerExecutor, playerObject)
    }
}

private fun handleRolePromotion(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canPromoteMember(playerExecutor, community, playerObject.id) }
    ) {
        val memberValue = community.member[playerObject.id]
        if (memberValue != null) {
            memberValue.basicRoleType = com.imyvm.community.domain.model.community.MemberRoleType.ADMIN
            
            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            
            val targetNotification = com.imyvm.community.util.Translator.tr(
                "community.notification.target.promoted",
                communityName,
                playerExecutor.name.string
            ) ?: net.minecraft.text.Text.literal("You were promoted to Admin in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyTargetPlayer(
                playerExecutor.server, playerObject.id, targetNotification, community
            )
            
            trMenu(
                playerExecutor,
                "community.member_management.promote.success",
                playerObject.name
            )
            
            val notification = com.imyvm.community.util.Translator.tr(
                "community.notification.member_promoted",
                playerObject.name,
                playerExecutor.name.string,
                communityName
            ) ?: net.minecraft.text.Text.literal("${playerObject.name} was promoted to Admin in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyOfficials(community, playerExecutor.server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
        }
    }
}

private fun handleRoleDemotion(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canDemoteMember(playerExecutor, community, playerObject.id) }
    ) {
        val memberValue = community.member[playerObject.id]
        if (memberValue != null) {
            memberValue.basicRoleType = com.imyvm.community.domain.model.community.MemberRoleType.MEMBER
            
            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            
            val targetNotification = com.imyvm.community.util.Translator.tr(
                "community.notification.target.demoted",
                communityName,
                playerExecutor.name.string
            ) ?: net.minecraft.text.Text.literal("You were demoted to Member in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyTargetPlayer(
                playerExecutor.server, playerObject.id, targetNotification, community
            )
            
            trMenu(
                playerExecutor,
                "community.member_management.demote.success",
                playerObject.name
            )
            
            val notification = com.imyvm.community.util.Translator.tr(
                "community.notification.member_demoted",
                playerObject.name,
                playerExecutor.name.string,
                communityName
            ) ?: net.minecraft.text.Text.literal("${playerObject.name} was demoted to Member in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyOfficials(community, playerExecutor.server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
        }
    }
}

private fun handleGovernorshipUpdate(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile,
    governorship: Int
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canManageMember(playerExecutor, community, playerObject.id) }
    ) {
        val memberValue = community.member[playerObject.id]
        if (memberValue != null) {
            memberValue.governorship = governorship
            trMenu(
                playerExecutor,
                "community.member_management.governorship.success",
                playerObject.name,
                governorship
            )
            
            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            val notification = com.imyvm.community.util.Translator.tr(
                "community.notification.governorship_changed",
                playerObject.name,
                governorship,
                playerExecutor.name.string,
                communityName
            ) ?: net.minecraft.text.Text.literal("${playerObject.name}'s governorship was set to $governorship in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyOfficials(community, playerExecutor.server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
        }
    }
}

private fun runBackToMemberMenu(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    playerObject: GameProfile,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        CommunityMemberMenu(
            syncId = syncId,
            community = community,
            playerObject = playerObject,
            playerExecutor = playerExecutor,
            runBack = runBack
        )
    }
}