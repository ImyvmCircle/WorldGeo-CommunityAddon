package com.imyvm.community.entrypoint.screen.inner_community.multi_parent.element

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.getPermissionButtonItemStack
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.getRuleButtonItemStack
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.runTogglingPermissionSetting
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.runTogglingRuleSetting
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
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
    private val page: Int = 0,
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
    private val isGlobal = playerObject == null

    init {
        // Pagination buttons at edge slots 0 (prev) and 8 (next)
        if (page > 0) {
            addButton(
                slot = 0,
                name = Translator.tr("ui.general.list.prev")?.string ?: "Previous",
                item = Items.ARROW
            ) { openNewPage(it, page - 1) }
        }
        if (isGlobal && page < 1) {
            addButton(
                slot = 8,
                name = Translator.tr("ui.general.list.next")?.string ?: "Next",
                item = Items.ARROW
            ) { openNewPage(it, page + 1) }
        }

        when (page) {
            0 -> addPermissionSettingButtons()
            1 -> {
                addRuleSettingButtons()
                addEffectSettingButtons()
            }
        }
    }

    private fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            TargetSettingMenu(syncId, playerExecutor, community, scope, playerObject, newPage, runBack)
        }
    }

    private fun addPermissionSettingButtons() {
        // Permission header at row 1 col 1 (slot 10), gap at slot 11
        addButton(
            slot = 10,
            name = Translator.tr("ui.community.administration.region.setting.list.permission.header")?.string ?: "Permissions",
            item = Items.SHIELD
        ) {}

        val permissionKeys = listOf(
            PermissionKey.BUILD_BREAK to Items.DIAMOND_PICKAXE,
            PermissionKey.BUILD to Items.BRICKS,
            PermissionKey.BREAK to Items.IRON_PICKAXE,
            PermissionKey.BUCKET_BUILD to Items.WATER_BUCKET,
            PermissionKey.BUCKET_SCOOP to Items.BUCKET,
            PermissionKey.INTERACTION to Items.OAK_BUTTON,
            PermissionKey.CONTAINER to Items.CHEST,
            PermissionKey.REDSTONE to Items.REDSTONE,
            PermissionKey.TRADE to Items.EMERALD,
            PermissionKey.PVP to Items.IRON_SWORD,
            PermissionKey.ANIMAL_KILLING to Items.BEEF,
            PermissionKey.VILLAGER_KILLING to Items.EMERALD_ORE,
            PermissionKey.THROWABLE to Items.BOW,
            PermissionKey.EGG_USE to Items.EGG,
            PermissionKey.SNOWBALL_USE to Items.SNOWBALL,
            PermissionKey.POTION_USE to Items.POTION,
            PermissionKey.FARMING to Items.WHEAT,
            PermissionKey.IGNITE to Items.FLINT_AND_STEEL,
            PermissionKey.ARMOR_STAND to Items.ARMOR_STAND,
            PermissionKey.ITEM_FRAME to Items.ITEM_FRAME
        )

        // Slot 10: header, slot 11: gap, slots 12-16 (5), 19-25 (7), 28-34 (7), 37 (1) = 20 items
        val permissionSlots = (12..16).toList() + (19..25).toList() + (28..34).toList() + listOf(37)

        permissionKeys.forEachIndexed { index, (key, item) ->
            val slot = permissionSlots[index]
            val nameKey = "ui.community.administration.region.setting.list.permission.${key.toString().lowercase()}"
            addButton(
                slot = slot,
                name = Translator.tr(nameKey)?.string ?: key.toString().lowercase().replace("_", " "),
                itemStack = getPermissionButtonItemStack(item, community, scope, playerObject, key)
            ) { runTogglingPermissionSetting(playerExecutor, community, scope, playerObject, key, runBack) }
        }
    }

    private fun addRuleSettingButtons() {
        // Rule header at row 1 col 1 (slot 10), gap at slot 11, items at slots 12-14
        addButton(
            slot = 10,
            name = Translator.tr("ui.community.administration.region.setting.list.rule.header")?.string ?: "Rules",
            item = Items.WRITABLE_BOOK
        ) {}

        val ruleEntries = listOf(
            RuleKey.SPAWN_MONSTERS to Items.ZOMBIE_SPAWN_EGG,
            RuleKey.SPAWN_PHANTOMS to Items.PHANTOM_MEMBRANE,
            RuleKey.TNT_BLOCK_PROTECTION to Items.TNT
        )

        ruleEntries.forEachIndexed { index, (key, item) ->
            val slot = 12 + index
            val nameKey = "ui.community.administration.region.setting.list.rule.${key.toString().lowercase()}"
            addButton(
                slot = slot,
                name = Translator.tr(nameKey)?.string ?: key.toString().lowercase().replace("_", " "),
                itemStack = getRuleButtonItemStack(item, community, scope, key)
            ) { runTogglingRuleSetting(playerExecutor, community, scope, key, runBack) }
        }
    }

    private fun addEffectSettingButtons() {
        // Row 2 (slots 19-25): empty gap row between rules and effects
        // Effect header at row 3 col 1 (slot 28), gap at slot 29, items at slots 30-34 (5) + 37-39 (3) = 8 total
        addButton(
            slot = 28,
            name = Translator.tr("ui.community.administration.region.setting.list.effect.header")?.string ?: "Effects",
            item = Items.BEACON
        ) {}

        val effectEntries = listOf(
            EffectKey.SPEED to Items.SUGAR,
            EffectKey.HASTE to Items.GOLDEN_PICKAXE,
            EffectKey.DAMAGE_RESISTANCE to Items.IRON_CHESTPLATE,
            EffectKey.JUMP to Items.RABBIT_FOOT,
            EffectKey.STRENGTH to Items.BLAZE_POWDER,
            EffectKey.REGENERATION to Items.GHAST_TEAR,
            EffectKey.SLOW_FALLING to Items.FEATHER,
            EffectKey.LUCK to Items.NETHER_STAR
        )

        // Slots 30-34 (5 items) + 37-39 (3 items) = 8 items total
        val effectSlots = (30..34).toList() + (37..39).toList()

        effectEntries.forEachIndexed { index, (key, item) ->
            val slot = effectSlots[index]
            val nameKey = "ui.community.administration.region.setting.list.effect.${key.toString().lowercase()}"
            addButton(
                slot = slot,
                name = Translator.tr(nameKey)?.string ?: key.toString().lowercase().replace("_", " "),
                item = item
            ) {
                playerExecutor.closeHandledScreen()
                playerExecutor.sendMessage(Translator.tr("community.setting.effect.coming_soon") ?: Text.literal("This feature is under development. Stay tuned!"))
            }
        }
    }

    companion object {
        private fun generateRegionSettingMenuTitle(
            community: Community,
            scope: GeoScope? = null,
            playerProfile: GameProfile? = null
        ): Text {
            val nullTag = Translator.tr("ui.community.administration.region.setting.list.title.unknown")?.string ?: "Unknown"
            val settingTag = Translator.tr("ui.community.administration.region.setting.list.title.setting")?.string ?: "Region Settings"
            var menuTitle = (community.getRegion()?.name ?: nullTag) + settingTag
            if (scope != null) menuTitle += " - ${scope.scopeName}"
            if (playerProfile != null) menuTitle += " - ${playerProfile.name}"
            return Text.of(menuTitle)
        }
    }
}