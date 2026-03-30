package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.inner_community.administration_only.runAccept
import com.imyvm.community.application.interaction.screen.inner_community.administration_only.runRefuse
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.mojang.authlib.GameProfile
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class AdministrationAuditMenu(
    syncId: Int,
    community: Community,
    private val playerExecutor: ServerPlayer,
    playerObject: GameProfile,
    runBack: (ServerPlayer) -> Unit
): AbstractMenu(
    syncId,
    menuTitle = generateAdministrationAuditMenuTitle(playerObject),
    runBack = runBack
) {
    init {
        addButton(
            slot = 21,
            name = Translator.tr("ui.admin.audit.button.accept") ?.string ?: "Accept",
            item = Items.GREEN_WOOL
        ) { runAccept(community, playerExecutor, playerObject) }

        addButton(
            slot = 26,
            name = Translator.tr("ui.admin.audit.button.refuse") ?.string ?: "Refuse",
            item = Items.BARRIER
        ) { runRefuse(community, playerExecutor, playerObject) }
    }
    companion object {
        fun generateAdministrationAuditMenuTitle(playerObject: GameProfile): Component =
            Component.literal((Translator.tr("ui.admin.audit.title").string ?: "Audit") + playerObject.name)
    }
}