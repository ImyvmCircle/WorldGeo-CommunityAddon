package com.imyvm.community.entrypoint.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.inner_community.runOpenSettingPlayerTargeted
import com.imyvm.community.application.interaction.screen.inner_community.runOpenSettingRegional
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.createPlayerHeadItemStack
import com.imyvm.community.entrypoint.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.util.Translator
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class CommunitySettingMenu(
    syncId: Int,
    playerExecutor: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = generateCommunitySettingMenuTitle(community),
    runBack = runBack
) {
    init {
        addButton(
            slot = 10,
            name = community.generateCommunityMark(),
            itemStack = getPlayerHeadButtonItemStackCommunity(community)
        ) {}

        addButton(
            slot = 19,
            name = Translator.tr("ui.community.setting.button.regional").string ?: "Regional Settings",
            item = Items.GRASS_BLOCK
        ) { runOpenSettingRegional(playerExecutor, community, runBack) }

        addButton(
            slot = 21,
            name = Translator.tr("ui.community.setting.button.player").string ?: "Player-targeting Settings",
            itemStack = createPlayerHeadItemStack(playerExecutor.name.string, playerExecutor.uuid)
        ) { runOpenSettingPlayerTargeted(playerExecutor, community, runBack) }
    }

    companion object {
        fun generateCommunitySettingMenuTitle(community: Community): Component {
            return Translator.tr("ui.community.setting.title.full", community.generateCommunityMark())
                ?: Component.literal("Community Settings")
        }
    }
}