package com.imyvm.community.entrypoint.screen.inner_community.administration_only.annoucement

import com.imyvm.community.application.interaction.screen.inner_community.affairs.onCreateAnnouncementConfirm
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class AdministrationAnnouncementInputMenuAnvil(
    player: ServerPlayer,
    val community: Community,
    val runBack: ((ServerPlayer) -> Unit)
) : AbstractRenameMenuAnvil(player, "") {

    override fun isNameValid(name: String): Boolean = name.isNotBlank()

    override fun reopenWith(errorHint: String?, currentInput: String) {
        AdministrationAnnouncementInputMenuAnvil(player, community, runBack).open()
    }
    
    override fun processRenaming(finalName: String) {
        onCreateAnnouncementConfirm(player, community, finalName, runBack)
    }
    
    override fun getMenuTitle(): Component {
        return Translator.tr("ui.admin.announcement_input.title") ?: Component.literal("Create Announcement")
    }
}
