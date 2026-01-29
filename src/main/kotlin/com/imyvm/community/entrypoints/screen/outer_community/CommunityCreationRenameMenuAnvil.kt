package com.imyvm.community.entrypoints.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.entrypoints.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityCreationRenameMenuAnvil(
    val playerExecutor: ServerPlayerEntity,
    initialName: String,
    private val currentShape: GeoShapeType,
    private val isManor: Boolean,
    private val runBackGrandfather: ((ServerPlayerEntity) -> Unit)
) : AbstractRenameMenuAnvil(
    playerExecutor,
    initialName
) {

    override fun processRenaming(finalName: String) {
        CommunityMenuOpener.open(playerExecutor) { newSyncId ->
            CommunityCreationMenu(newSyncId, finalName, currentShape, isManor, playerExecutor, runBackGrandfather)
        }
    }


    override fun getMenuTitle(): Text = Translator.tr ("ui.create.rename.title") ?: Text.of("Rename Community")
}