package com.imyvm.community.entrypoint.screen.inner_community.multi_parent.element

import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.getPermissionButtonItemStack
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.runTogglingPermissionSetting
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.mojang.authlib.GameProfile
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class TargetSettingMenu(
    syncId: Int,
    val playerExecutor: ServerPlayerEntity,
    val community: Community,
    val scope: GeoScope? = null,
    private val playerObject: GameProfile? = null,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = generateRegionSettingMenuTitle(
        community = community,
        scope = scope,
        playerProfile = playerObject
    ),
    runBack = runBack
) {
    init {
        addPermissionSettingButtons()
        addEffectSettingButtons()
        if (playerObject == null) addRuleSettingButtons()
    }

    private fun addPermissionSettingButtons(){
        val isManageable = community.isManageable(playerExecutor)

        addButton(
            slot = 10,
            name = Translator.tr("ui.community.administration.region.setting.list.permission.header")?.string ?: "Permission Settings",
            item = Items.SHIELD
        ) {}

        addButton(
            slot = 12,
            name = Translator.tr("ui.community.administration.region.setting.list.permission.build_break")?.string ?: "Build/Break Permission",
            itemStack = getPermissionButtonItemStack(Items.DIAMOND_PICKAXE, community, scope, playerObject, PermissionKey.BUILD_BREAK)
        ) { if (isManageable) runTogglingPermissionSetting(playerExecutor, community, scope, playerObject, PermissionKey.BUILD_BREAK, runBack) }

        addButton(
            slot = 14,
            name = Translator.tr("ui.community.administration.region.setting.list.permission.container")?.string ?: "Container Permission",
            itemStack = getPermissionButtonItemStack(Items.CHEST, community, scope, playerObject, PermissionKey.CONTAINER)
        ) { if (isManageable) runTogglingPermissionSetting(playerExecutor, community, scope, playerObject, PermissionKey.CONTAINER, runBack) }
    }

    private fun addEffectSettingButtons(){
        addButton(
            slot = 28,
            name = Translator.tr("ui.community.administration.region.setting.list.effect.header")?.string ?: "Effect Settings",
            item = Items.BEACON
        ) {}
    }

    private fun addRuleSettingButtons(){
        addButton(
            slot = 46,
            name = Translator.tr("ui.community.administration.region.setting.list.rule.header")?.string ?: "Rule Settings",
            item = Items.WRITABLE_BOOK
        ) {}

    }

    companion object {
        private fun generateRegionSettingMenuTitle(
            community: Community,
            scope: GeoScope? = null,
            playerProfile: GameProfile? = null
        ): Text {
            val nullTag = Translator.tr("ui.community.administration.region.setting.list.title.unknown")?.string ?: "Unknown"
            val settingTag = Translator.tr("ui.community.administration.region.setting.list.title.setting")?.string ?: "Setting"
            var menuTitle = (community.getRegion()?.name ?: nullTag) + settingTag
            if (scope != null) menuTitle += " - ${scope.scopeName}"
            if (playerProfile != null) menuTitle += " - ${playerProfile.name}"
            return Text.of(menuTitle)
        }
    }
}