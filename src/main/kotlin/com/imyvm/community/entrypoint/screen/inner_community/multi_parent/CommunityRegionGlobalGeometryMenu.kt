package com.imyvm.community.entrypoint.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runTransferScopeFromGlobalMenu
import com.imyvm.community.application.interaction.screen.outer_community.runAddScopeForCommunity
import com.imyvm.community.application.interaction.screen.outer_community.runDeleteScopeForCommunity
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class CommunityRegionGlobalGeometryMenu(
    syncId: Int,
    private val playerExecutor: ServerPlayer,
    private val community: Community,
    runBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = Component.literal(
        community.generateCommunityMark() + " - " +
            (Translator.tr("ui.admin.region.geometry.global.title").string
                ?: "Global Geographic Scope Operations")
    ),
    runBack = runBack
) {
    init {
        addButton(
            slot = 21,
            name = Translator.tr("ui.admin.region.global.add").string ?: "Add Administrative District",
            item = Items.LIME_WOOL
        ) { runAddScopeForCommunity(it, community, runBack) }

        addButton(
            slot = 22,
            name = Translator.tr("ui.admin.region.global.delete").string ?: "Delete Administrative District",
            item = Items.RED_WOOL
        ) { runDeleteScopeForCommunity(it, community, runBack) }

        addButton(
            slot = 23,
            name = Translator.tr("ui.admin.region.global.transfer").string ?: "Transfer Administrative District",
            item = Items.YELLOW_WOOL
        ) { runTransferScopeFromGlobalMenu(it, community, runBack) }
    }
}
