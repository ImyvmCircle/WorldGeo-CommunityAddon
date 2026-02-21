package com.imyvm.community.entrypoint.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runOpenScopeCreationMenu
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runUnimplementedGeometryGlobalAction
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityRegionGlobalGeometryMenu(
    syncId: Int,
    private val playerExecutor: ServerPlayerEntity,
    private val community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = Text.of(
        community.generateCommunityMark() + " - " +
            (Translator.tr("ui.community.administration.region.geometry.global.title")?.string
                ?: "Global Geometry Operations")
    ),
    runBack = runBack
) {
    init {
        addButton(
            slot = 21,
            name = Translator.tr("ui.community.administration.region.global.add")?.string ?: "Add Administrative District",
            item = Items.LIME_WOOL
        ) { runOpenScopeCreationMenu(it, community, runBack) }

        addButton(
            slot = 22,
            name = Translator.tr("ui.community.administration.region.global.delete")?.string ?: "Delete Administrative District",
            item = Items.RED_WOOL
        ) { runUnimplementedGeometryGlobalAction(it) }

        addButton(
            slot = 23,
            name = Translator.tr("ui.community.administration.region.global.transfer")?.string ?: "Transfer Administrative District",
            item = Items.YELLOW_WOOL
        ) { runUnimplementedGeometryGlobalAction(it) }
    }
}
