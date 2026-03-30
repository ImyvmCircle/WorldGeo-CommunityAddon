package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class AdministrationAdvancementMenu(
    syncId: Int,
    community: Community,
    playerExecutor: ServerPlayer,
    runBack: ((ServerPlayer) -> Unit)
): AbstractMenu(
    syncId,
    menuTitle = generateCommunityAdvancementMenuTitle(community),
    runBack = runBack
) {
    companion object {
        private fun generateCommunityAdvancementMenuTitle(community: Community): Component {
            return Component.literal(community.generateCommunityMark() + " - Community Advancement Menu: ")
        }
    }
}