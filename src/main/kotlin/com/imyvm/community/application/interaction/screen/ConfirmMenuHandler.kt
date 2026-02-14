package com.imyvm.community.application.interaction.screen

import com.imyvm.community.application.interaction.common.onCreateCommunityRequest
import com.imyvm.community.application.interaction.common.onJoinCommunityDirectly
import com.imyvm.community.application.permission.PermissionCheck
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.Turnover
import com.imyvm.community.entrypoints.screen.component.ConfirmTaskType
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
        ConfirmTaskType.INVITATION_ACCEPT, ConfirmTaskType.INVITATION_REJECT -> {
        }
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

    val result = onJoinCommunityDirectly(playerExecutor, targetCommunity)

    if (result == 1) {
        val newAccount = targetCommunity.member[playerExecutor.uuid]
        if (newAccount != null) {
            newAccount.turnover.add(Turnover(amount = -500, timestamp = System.currentTimeMillis()))
            CommunityDatabase.save()
        }
    }
}

private fun runCommunityLeave(
    playerExecutor: ServerPlayerEntity,
    targetCommunity: Community?
) {
    if (targetCommunity == null) {
        playerExecutor.sendMessage(Translator.tr("community.leave.error.missing_data"))
        return
    }

    PermissionCheck.executeWithPermission(
        playerExecutor,
        { PermissionCheck.canQuitCommunity(playerExecutor, targetCommunity) }
    ) {
        targetCommunity.member.remove(playerExecutor.uuid)
        CommunityDatabase.save()
        
        playerExecutor.sendMessage(
            Translator.tr("community.leave.success", 
                targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
            )
        )
    }
}
