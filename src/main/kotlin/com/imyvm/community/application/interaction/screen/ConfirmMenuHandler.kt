package com.imyvm.community.application.interaction.screen

import com.imyvm.community.application.interaction.common.onCreateCommunityRequest
import com.imyvm.community.application.interaction.common.onJoinCommunityDirectly
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.component.ConfirmTaskType
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun runConfirmDispatcher(
    playerExecutor: ServerPlayerEntity,
    confirmTaskType: ConfirmTaskType,
    communityType: String? = null,
    communityName: String? = null,
    shapeName: String? = null,
    targetCommunity: Community? = null
) {
    playerExecutor.closeHandledScreen()

    when (confirmTaskType) {
        ConfirmTaskType.CREATE_COMMUNITY -> runCommunityCreation(playerExecutor, communityType, communityName, shapeName)
        ConfirmTaskType.JOIN_COMMUNITY -> runCommunityJoin(playerExecutor, targetCommunity)
        ConfirmTaskType.LEAVE_COMMUNITY -> runCommunityLeave(playerExecutor, targetCommunity)
        ConfirmTaskType.INVITATION_ACCEPT, ConfirmTaskType.INVITATION_REJECT -> {}
    }
}

private fun runCommunityCreation(
    playerExecutor: ServerPlayerEntity,
    communityType: String? = null,
    communityName: String? = null ,
    shapeName: String? = null
) {
    if (communityType == null || communityName == null || shapeName == null) {
        playerExecutor.sendMessage(Translator.tr("ui.confirm.creation.error.missing_data"))
    } else {
        onCreateCommunityRequest(
            player = playerExecutor,
            communityType = communityType,
            communityName = communityName,
            shapeName = shapeName
        )
    }
}

private fun runCommunityJoin(
    playerExecutor: ServerPlayerEntity,
    targetCommunity: Community?
) {
    if (targetCommunity == null) {
        playerExecutor.sendMessage(Translator.tr("community.join.error.missing_data"))
        return
    }

    onJoinCommunityDirectly(playerExecutor, targetCommunity)
}

private fun runCommunityLeave(
    playerExecutor: ServerPlayerEntity,
    targetCommunity: Community?
) {
    if (targetCommunity == null) {
        playerExecutor.sendMessage(Translator.tr("community.leave.error.missing_data"))
        return
    }

    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canQuitCommunity(playerExecutor, targetCommunity) }
    ) {
        val communityName = targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
        
        targetCommunity.member.remove(playerExecutor.uuid)
        CommunityDatabase.save()
        
        playerExecutor.sendMessage(
            Translator.tr("community.leave.success", communityName)
        )
        
        val notification = Translator.tr("community.notification.member_left", playerExecutor.name.string, communityName)
            ?: net.minecraft.text.Text.literal("${playerExecutor.name.string} has left $communityName")
        notifyOfficials(targetCommunity, playerExecutor.server, notification)
    }
}

private fun notifyOfficials(community: Community, server: net.minecraft.server.MinecraftServer, message: net.minecraft.text.Text) {
    for ((memberUUID, memberAccount) in community.member) {
        val isOfficial = memberAccount.basicRoleType == com.imyvm.community.domain.model.community.MemberRoleType.OWNER ||
                        memberAccount.basicRoleType == com.imyvm.community.domain.model.community.MemberRoleType.ADMIN ||
                        memberAccount.isCouncilMember
        
        if (isOfficial) {
            val officialPlayer = server.playerManager.getPlayer(memberUUID)
            if (officialPlayer != null) {
                officialPlayer.sendMessage(message)
            }
            memberAccount.mail.add(message)
        }
    }
}
