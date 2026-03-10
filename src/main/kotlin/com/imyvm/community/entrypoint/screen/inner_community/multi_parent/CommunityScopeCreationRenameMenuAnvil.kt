package com.imyvm.community.entrypoint.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityScopeCreationRenameMenuAnvil(
    private val playerExecutor: ServerPlayerEntity,
    private val community: Community,
    initialName: String,
    private val runBackGrandfather: (ServerPlayerEntity) -> Unit
) : AbstractRenameMenuAnvil(
    playerExecutor,
    initialName
) {
    override fun processRenaming(finalName: String) {
        CommunityMenuOpener.open(playerExecutor) { newSyncId ->
            CommunityScopeCreationMenu(newSyncId, community, finalName, playerExecutor, runBackGrandfather)
        }
    }

    override fun getMenuTitle(): Text {
        return Translator.tr("ui.admin.region.global.add.rename.title")
            ?: Text.of("Rename Administrative District")
    }
}
