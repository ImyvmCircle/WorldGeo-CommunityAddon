package com.imyvm.community.entrypoint.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.executeScopeModification
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runToggleScopeMod
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoScope
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ScopeGeometryModificationConfirmMenu(
    syncId: Int,
    val playerExecutor: ServerPlayerEntity,
    val community: Community,
    val scope: GeoScope,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.admin.scope.modify.confirm_menu.title", community.getRegion()?.name ?: "", scope.scopeName)
        ?: Text.literal("Modify: ${scope.scopeName}"),
    runBack = runBack
) {
    init {
        addButton(
            slot = 11,
            name = Translator.tr("ui.admin.scope.modify.confirm_menu.button.toggle_off")?.string
                ?: "Cancel Selection Mode",
            item = Items.REDSTONE_BLOCK
        ) { p -> runToggleScopeMod(p, community, scope, runBack) }

        addButton(
            slot = 13,
            name = Translator.tr("ui.admin.scope.modify.confirm_menu.button.exit_to_select")?.string
                ?: "Exit to Continue Selecting",
            item = Items.ENDER_PEARL
        ) { p -> p.closeHandledScreen() }

        addButton(
            slot = 15,
            name = Translator.tr("ui.admin.scope.modify.confirm_menu.button.confirm")?.string
                ?: "Confirm Modification",
            item = Items.EMERALD_BLOCK
        ) { p -> executeScopeModification(p, community, scope) }
    }
}
