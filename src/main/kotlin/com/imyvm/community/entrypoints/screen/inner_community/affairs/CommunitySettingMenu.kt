package com.imyvm.community.entrypoints.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.inner_community.runOpenSettingPlayerTargeted
import com.imyvm.community.application.interaction.screen.inner_community.runOpenSettingRegional
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.entrypoints.screen.component.createPlayerHeadItemStack
import com.imyvm.community.entrypoints.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunitySettingMenu(
    syncId: Int,
    playerExecutor: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
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
            name = Translator.tr("ui.community.setting.button.regional")?.string ?: "Regional Settings",
            item = Items.GRASS_BLOCK
        ) { runOpenSettingRegional(playerExecutor, community, runBack) }

        addButton(
            slot = 21,
            name = Translator.tr("ui.community.setting.button.player")?.string ?: "Player-targeting Settings",
            itemStack = createPlayerHeadItemStack(playerExecutor.name.string, playerExecutor.uuid)
        ) { runOpenSettingPlayerTargeted(playerExecutor, community, runBack) }
    }

    companion object {
        fun generateCommunitySettingMenuTitle(community: Community): Text {
            return Translator.tr("ui.community.setting.title.full", community.generateCommunityMark())
                ?: Text.of("Community Settings")
        }
    }
}