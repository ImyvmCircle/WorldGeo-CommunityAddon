package com.imyvm.community.entrypoints.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.inner_community.administration_only.runAccept
import com.imyvm.community.application.interaction.screen.inner_community.administration_only.runRefuse
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.mojang.authlib.GameProfile
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class AdministrationAuditMenu(
    syncId: Int,
    community: Community,
    private val playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile,
    runBack: (ServerPlayerEntity) -> Unit
): AbstractMenu(
    syncId,
    menuTitle = generateAdministrationAuditMenuTitle(playerObject),
    runBack = runBack
) {
    init {
        addButton(
            slot = 21,
            name = Translator.tr("ui.community.administration.audit.button.accept") ?.string ?: "Accept",
            item = Items.GREEN_WOOL
        ) { runAccept(community, playerExecutor, playerObject) }

        addButton(
            slot = 26,
            name = Translator.tr("ui.community.administration.audit.button.refuse") ?.string ?: "Refuse",
            item = Items.BARRIER
        ) { runRefuse(community, playerExecutor, playerObject) }
    }
    companion object {
        fun generateAdministrationAuditMenuTitle(playerObject: GameProfile): Text =
            Text.of((Translator.tr("ui.community.administration.audit.title")?.string ?: "Audit") + playerObject.name)
    }
}