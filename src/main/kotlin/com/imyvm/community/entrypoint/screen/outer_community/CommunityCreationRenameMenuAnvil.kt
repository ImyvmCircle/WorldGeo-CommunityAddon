package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.entrypoint.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class CommunityCreationRenameMenuAnvil(
    val playerExecutor: ServerPlayer,
    initialName: String,
    private val currentShape: GeoShapeType,
    private val isManor: Boolean,
    private val runBackGrandfather: ((ServerPlayer) -> Unit),
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

    override fun getMenuTitle(): Component = buildTitle(Translator.tr("ui.create.rename.title") ?: Component.literal("Rename Community"))
}