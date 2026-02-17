package com.imyvm.community.entrypoints.screen.inner_community.administration_only.annoucement

import com.imyvm.community.application.interaction.screen.inner_community.affairs.onCreateAnnouncementConfirm
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class AdministrationAnnouncementInputMenuAnvil(
    player: ServerPlayerEntity,
    val community: Community,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractRenameMenuAnvil(player, "") {
    
    override fun processRenaming(finalName: String) {
        onCreateAnnouncementConfirm(player, community, finalName, runBack)
    }
    
    override fun getMenuTitle(): Text {
        return Translator.tr("ui.community.administration.announcement_input.title") ?: Text.of("Create Announcement")
    }
}
