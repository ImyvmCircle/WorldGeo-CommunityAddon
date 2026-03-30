package com.imyvm.community.entrypoint.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class CommunityScopeCreationRenameMenuAnvil(
    private val playerExecutor: ServerPlayer,
    private val community: Community,
    initialName: String,
    private val runBackGrandfather: (ServerPlayer) -> Unit,
    errorHint: String? = null
) : AbstractRenameMenuAnvil(
    playerExecutor,
    initialName,
    errorHint
) {
    override fun processRenaming(finalName: String) {
        CommunityMenuOpener.open(playerExecutor) { newSyncId ->
            CommunityScopeCreationMenu(newSyncId, community, finalName, playerExecutor, runBackGrandfather)
        }
    }

    override fun reopenWith(errorHint: String?, currentInput: String) {
        CommunityScopeCreationRenameMenuAnvil(
            playerExecutor, community, currentInput, runBackGrandfather, errorHint
        ).open()
    }

    override fun getMenuTitle(): Component {
        val base = Translator.tr("ui.admin.region.global.add.rename.title")
            ?: Component.literal("Rename Administrative District")
        return buildTitle(base)
    }
}
