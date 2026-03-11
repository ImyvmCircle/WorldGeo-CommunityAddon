package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.entrypoint.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityCreationRenameMenuAnvil(
    val playerExecutor: ServerPlayerEntity,
    initialName: String,
    private val currentShape: GeoShapeType,
    private val isManor: Boolean,
    private val runBackGrandfather: ((ServerPlayerEntity) -> Unit),
    errorHint: String? = null
) : AbstractRenameMenuAnvil(
    playerExecutor,
    initialName,
    errorHint
) {

    override fun processRenaming(finalName: String) {
        CommunityMenuOpener.open(playerExecutor) { newSyncId ->
            CommunityCreationSelectionMenu(newSyncId, finalName, isManor, playerExecutor, runBackGrandfather)
        }
    }

    override fun reopenWith(errorHint: String?, currentInput: String) {
        CommunityCreationRenameMenuAnvil(
            playerExecutor, currentInput, currentShape, isManor, runBackGrandfather, errorHint
        ).open()
    }

    override fun getMenuTitle(): Text = buildTitle(Translator.tr("ui.create.rename.title") ?: Text.of("Rename Community"))
}