package com.imyvm.community.entrypoints.screen.inner_community.operation_only

import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityOperationAdvancementMenu(
    syncId: Int,
    community: Community,
    playerExecutor: ServerPlayerEntity,
    runBack: ((ServerPlayerEntity) -> Unit)
): AbstractMenu(
    syncId,
    menuTitle = generateCommunityAdvancementMenuTitle(community),
    runBack = runBack
) {
    companion object {
        private fun generateCommunityAdvancementMenuTitle(community: Community): Text {
            return Text.of(community.generateCommunityMark() + " - Community Advancement Menu: ")
        }
    }
}