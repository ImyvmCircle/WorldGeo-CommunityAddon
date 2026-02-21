package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class AdministrationAdvancementMenu(
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