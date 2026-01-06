package com.imyvm.community.application.interaction.screen

import com.imyvm.community.application.interaction.common.onCreateCommunity
import com.imyvm.community.inter.screen.component.ConfirmTaskType
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun runConfirmDispatcher(
    playerExecutor: ServerPlayerEntity,
    confirmTaskType: ConfirmTaskType,
    communityType: String? = null,
    communityName: String? = null,
    shapeName: String? = null
) {
    playerExecutor.closeHandledScreen()

    when (confirmTaskType) {
        ConfirmTaskType.CREATE_COMMUNITY -> runCommunityCreation(playerExecutor, communityType, communityName, shapeName)
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
        onCreateCommunity(
            player = playerExecutor,
            communityType = communityType,
            communityName = communityName,
            shapeName = shapeName
        )
    }
}

