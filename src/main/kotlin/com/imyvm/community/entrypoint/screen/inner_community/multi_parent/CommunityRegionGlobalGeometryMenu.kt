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
            Translator.trStringOrFallback("ui.admin.region.geometry.global.title", "Global Geographic Scope Operations")
    ),
    runBack = runBack
) {
    init {
        addButton(
            slot = 21,
            name = Translator.trStringOrFallback("ui.admin.region.global.add", "Add Administrative District"),
            item = Items.WOOL.lime()
        ) { runAddScopeForCommunity(it, community, runBack) }

        addButton(
            slot = 22,
            name = Translator.trStringOrFallback("ui.admin.region.global.delete", "Delete Administrative District"),
            item = Items.WOOL.red()
        ) { runDeleteScopeForCommunity(it, community, runBack) }

        addButton(
            slot = 23,
            name = Translator.trStringOrFallback("ui.admin.region.global.transfer", "Transfer Administrative District"),
            item = Items.WOOL.yellow()
        ) { runTransferScopeFromGlobalMenu(it, community, runBack) }
    }
}
