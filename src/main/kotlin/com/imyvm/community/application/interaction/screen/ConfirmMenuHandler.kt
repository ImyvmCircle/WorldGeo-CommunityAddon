package com.imyvm.community.application.interaction.screen

import com.imyvm.community.application.interaction.common.onCreateCommunityRequest
import com.imyvm.community.application.interaction.common.onJoinCommunityDirectly
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.component.ConfirmTaskType
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer

fun runConfirmDispatcher(
    playerExecutor: ServerPlayer,
    confirmTaskType: ConfirmTaskType,
    communityType: String? = null,
    communityName: String? = null,
    shapeName: String? = null,
    targetCommunity: Community? = null
) {
    playerExecutor.closeContainer()

    when (confirmTaskType) {
        ConfirmTaskType.CREATE_COMMUNITY -> runCommunityCreation(playerExecutor, communityType, communityName, shapeName)
        ConfirmTaskType.JOIN_COMMUNITY -> runCommunityJoin(playerExecutor, targetCommunity)
        ConfirmTaskType.LEAVE_COMMUNITY -> runCommunityLeave(playerExecutor, targetCommunity)
        ConfirmTaskType.INVITATION_ACCEPT, ConfirmTaskType.INVITATION_REJECT -> {}
    }
}

private fun runCommunityCreation(
    playerExecutor: ServerPlayer,
    communityType: String? = null,
    communityName: String? = null,
    shapeName: String? = null
) {
    if (communityType == null || communityName == null) {
        playerExecutor.sendSystemMessage(Translator.tr("ui.confirm.creation.error.missing_data"))
    } else {
        onCreateCommunityRequest(
            player = playerExecutor,
            communityType = communityType,
            communityName = communityName
        )
    }
}

private fun runCommunityJoin(
    playerExecutor: ServerPlayer,
    targetCommunity: Community?
) {
    if (targetCommunity == null) {
        playerExecutor.sendSystemMessage(Translator.tr("community.join.error.missing_data"))
        return
    }

    onJoinCommunityDirectly(playerExecutor, targetCommunity)
}

private fun runCommunityLeave(
    playerExecutor: ServerPlayer,
    targetCommunity: Community?
) {
    if (targetCommunity == null) {
        playerExecutor.sendSystemMessage(Translator.tr("community.leave.error.missing_data"))
        return
    }

    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canQuitCommunity(playerExecutor, targetCommunity) }
    ) {
        val communityName = targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
        
        targetCommunity.member.remove(playerExecutor.uuid)
        com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.revokeGrantedPermissions(playerExecutor.uuid, targetCommunity)
        CommunityDatabase.save()
        
        playerExecutor.sendSystemMessage(
            Translator.tr("community.leave.success", communityName)
        )
        
        val notification = Translator.tr("community.notification.member_left", playerExecutor.name.string, communityName)
            ?: net.minecraft.network.chat.Component.literal("${playerExecutor.name.string} has left $communityName")
        notifyOfficials(targetCommunity, playerExecutor.level().server, notification)
    }
}

private fun notifyOfficials(community: Community, server: net.minecraft.server.MinecraftServer, message: net.minecraft.network.chat.Component) {
    for ((memberUUID, memberAccount) in community.member) {
        val isOfficial = memberAccount.basicRoleType == com.imyvm.community.domain.model.community.MemberRoleType.OWNER ||
                        memberAccount.basicRoleType == com.imyvm.community.domain.model.community.MemberRoleType.ADMIN
        
        if (isOfficial) {
            val officialPlayer = server.playerList.getPlayer(memberUUID)
            if (officialPlayer != null) {
                officialPlayer.sendSystemMessage(message)
            }
            memberAccount.mail.add(message)
        }
    }
}
