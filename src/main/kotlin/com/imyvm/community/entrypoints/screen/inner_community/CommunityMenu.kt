package com.imyvm.community.entrypoints.screen.inner_community

import com.imyvm.community.application.interaction.screen.inner_community.*
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenAssetsMenu
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenMemberAnnouncementListMenu
import com.imyvm.community.application.permission.PermissionCheck
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.entrypoints.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity

class CommunityMenu(
    syncId: Int,
    val player: ServerPlayerEntity,
    val community: Community,
    val runBack : ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = community.getRegion()?.let { Translator.tr("ui.community.title", it.name , it.numberID)},
    runBack = runBack
) {
    init {
        addOwnerHeadButton()
        addAdministrationButtonTrail()
        addDescriptionButton()
        addInteractionButton()
    }

    private fun addOwnerHeadButton() {
        addButton(
            slot = 10,
            name = community.generateCommunityMark(),
            itemStack = getPlayerHeadButtonItemStackCommunity(community)
        ) {}
    }

    private fun addAdministrationButtonTrail() {
        if (PermissionCheck.canExecuteAdministration(player, community).isAllowed()) {
            addButton(
                slot = 12,
                name = Translator.tr("ui.community.button.interaction.operations")?.string ?: "Community Operations",
                item = Items.ANVIL
            ) { runOpenOperationMenu(player, community, runBack) }
        }

        if (community.council.enabled && PermissionCheck.canAccessCouncil(player, community).isAllowed()) {
            addButton(
                slot = 13,
                name = Translator.tr("ui.community.button.interaction.council")?.string ?: "Council",
                item = Items.LECTERN
            ) { 
                com.imyvm.community.application.interaction.screen.inner_community.council.runOpenCouncilMenu(
                    player, community, runBack
                )
            }
        }
    }

    private fun addDescriptionButton(){
        addButton(
            slot = 19,
            name = Translator.tr("ui.community.button.description.region")?.string ?: "Description",
            item = Items.BOOKSHELF
        ) { runSendingCommunityDescription(player, community) }

        addButton(
            slot = 20,
            name = Translator.tr("ui.community.button.description.announcement")?.string ?: "Announcement",
            item = Items.MAP
        ) { runOpenMemberAnnouncementListMenu(player, community, runBack) }


        addButton(
            slot = 22,
            name = Translator.tr("ui.community.button.description.members")?.string ?: "Member",
            item = Items.ARMOR_STAND
        ) { runOpenMemberListMenu(player, community, runBack) }

        addButton(
            slot = 21,
            name = Translator.tr("ui.community.button.description.assets")?.string ?: "Asset",
            item = Items.GOLD_INGOT
        ) { runOpenAssetsMenu(player, community, runBack) }
    }

    private fun addInteractionButton(){
        addButton(
            slot = 23,
            name = Translator.tr("ui.community.button.interaction.settings")?.string ?: "Settings",
            item = Items.HEART_OF_THE_SEA
        ) { runOpenSettingMenu(player, community, runBack) }

        addButton(
            slot = 24,
            name = Translator.tr("ui.community.button.interaction.teleport")?.string ?: "Teleport to Community",
            item = Items.ENDER_PEARL
        ) { runTeleportCommunity(player, community) }

        addButton(
            slot = 25,
            Translator.tr("ui.community.button.interaction.teleport.scope")?.string ?: "Teleportation Scope",
            item = Items.COMPASS
        ) { runTeleportToScope(player, community, runBack) }

        addButton(
            slot = 28,
            name = Translator.tr("ui.community.button.interaction.chat")?.string ?: "Community Chat",
            item = Items.LECTERN
        ) {}

        addButton(
            slot = 29,
            name = Translator.tr("ui.community.button.interaction.advancement")?.string ?: "Advancement",
            item = Items.WRITABLE_BOOK
        ) {}

        addButton(
            slot = 30,
            name = Translator.tr("ui.community.button.interaction.donate")?.string ?: "Donate to Community",
            item = Items.EMERALD
        ) { runOpenAssetsMenu(player, community, runBack) }

        addButton(
            slot = 31,
            name = Translator.tr("ui.community.button.interaction.shop")?.string ?: "Community Shop",
            item = Items.CHEST
        ) {}

        addButton(
            slot = 32,
            name = Translator.tr("ui.community.button.interaction.like")?.string ?: "Like Community",
            item = Items.PINK_DYE
        ) {}

        addButton(
            slot = 33,
            name = Translator.tr("ui.community.button.interaction.leave")?.string ?: "Leave Community",
            item = Items.ZOMBIE_VILLAGER_SPAWN_EGG
        ) { runShowLeaveConfirmMenu(player, community, runBack) }

        addButton(
            slot = 34,
            name = Translator.tr ("ui.community.button.interaction.invite")?.string ?: "Invite Member",
            item = Items.VILLAGER_SPAWN_EGG
        ) {}
    }
}

