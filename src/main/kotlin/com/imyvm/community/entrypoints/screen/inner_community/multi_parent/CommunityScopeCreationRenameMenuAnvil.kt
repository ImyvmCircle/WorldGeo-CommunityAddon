package com.imyvm.community.entrypoints.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityScopeCreationRenameMenuAnvil(
    private val playerExecutor: ServerPlayerEntity,
    private val community: Community,
    initialName: String,
    private val currentShape: GeoShapeType,
    private val runBackGrandfather: (ServerPlayerEntity) -> Unit
) : AbstractRenameMenuAnvil(
    playerExecutor,
    initialName
) {
    override fun processRenaming(finalName: String) {
        CommunityMenuOpener.open(playerExecutor) { newSyncId ->
            CommunityScopeCreationMenu(newSyncId, community, finalName, currentShape, playerExecutor, runBackGrandfather)
        }
    }

    override fun getMenuTitle(): Text {
        return Translator.tr("ui.community.administration.region.global.add.rename.title")
            ?: Text.of("Rename Administrative District")
    }
}
