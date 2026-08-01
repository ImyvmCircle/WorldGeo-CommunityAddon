package com.imyvm.community.entrypoint.screen.inner_community

import com.imyvm.community.application.interaction.screen.inner_community.*
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenAssetsMenu
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenCommunitySpaceInfoMenu
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenMemberAnnouncementListMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

class CommunityMenu(
    syncId: Int,
    val player: ServerPlayer,
    val community: Community,
    val runBack: ((ServerPlayer) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = community.getRegion()?.let { Translator.tr("ui.community.title", it.name, it.numberID) },
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
        if (CommunityPermissionPolicy.canExecuteAdministration(player, community).isAllowed()) {
            addButton(
                slot = 12,
                name = Translator.tr("ui.community.button.interaction.operations").string,
                item = Items.ANVIL
            ) { runOpenOperationMenu(player, community, runBack) }
        }
    }

    private fun addDescriptionButton() {
        addButton(
            slot = 19,
            name = Translator.tr("ui.community.button.description.region").string,
            item = Items.BOOKSHELF
        ) { runSendingCommunityDescription(player, community) }

        addButton(
            slot = 20,
            name = Translator.tr("ui.community.button.description.announcement").string,
            item = Items.MAP
        ) { runOpenMemberAnnouncementListMenu(player, community) { runBackToCommunityMenu(player, community, runBack) } }

        addButton(
            slot = 22,
            name = Translator.tr("ui.community.button.description.members").string,
            item = Items.ARMOR_STAND
        ) { runOpenMemberListMenu(player, community, runBack) }

        addButton(
            slot = 21,
            name = Translator.tr("ui.community.button.description.assets").string,
            item = Items.GOLD_INGOT
        ) { runOpenAssetsMenu(player, community, runBack) }

        addButton(
            slot = 31,
            name = Translator.tr("ui.community.button.description.space").string,
            item = Items.SPYGLASS
        ) { runOpenCommunitySpaceInfoMenu(player, community, runBack) }
    }

    private fun addInteractionButton() {
        addButton(
            slot = 23,
            name = Translator.tr("ui.community.button.interaction.settings").string,
            item = Items.HEART_OF_THE_SEA
        ) { runOpenSettingMenu(player, community, runBack) }

        addButton(
            slot = 24,
            name = Translator.tr("ui.community.button.interaction.teleport").string,
            item = Items.ENDER_PEARL
        ) { runTeleportCommunity(player, community) }

        addButton(
            slot = 25,
            name = Translator.tr("ui.community.button.interaction.teleport.scope").string,
            item = Items.COMPASS
        ) { runTeleportToScope(player, community, runBack) }

        addButton(
            slot = 28,
            name = Translator.tr("ui.community.button.interaction.chat").string,
            item = Items.WRITABLE_BOOK
        ) { com.imyvm.community.application.interaction.screen.inner_community.chat.runOpenChatRoomMenu(player, community) { runBackToCommunityMenu(player, community, runBack) } }

        addButton(
            slot = 29,
            name = Translator.tr("ui.community.button.interaction.building").string,
            item = Items.BRICKS
        ) { runOpenCommunityBuildingMenu(player, community, runBack) }

        addButton(
            slot = 30,
            name = Translator.tr("ui.community.button.interaction.donate").string,
            item = Items.EMERALD
        ) { runOpenAssetsMenu(player, community, runBack) }

        addButton(
            slot = 35,
            name = Translator.tr("ui.community.button.interaction.fiscal").string,
            item = Items.LECTERN
        ) { runSendFiscalStatus(player, community) }

        addButton(
            slot = 36,
            name = Translator.tr("ui.community.button.interaction.development").string,
            item = Items.AMETHYST_SHARD
        ) { runSendDevelopmentAndLandPriceStatus(player, community) }

        addButton(
            slot = 37,
            name = Translator.tr("ui.community.button.interaction.title").string,
            item = Items.NAME_TAG
        ) { runSendTitleStatus(player, community) }

        addButton(
            slot = 32,
            name = Translator.tr("ui.community.button.interaction.like").string,
            item = Items.DYE.pink()
        ) { runLikeCommunity(player, community) }

        addButton(
            slot = 33,
            name = Translator.tr("ui.community.button.interaction.leave").string,
            item = Items.ZOMBIE_VILLAGER_SPAWN_EGG
        ) { runShowLeaveConfirmMenu(player, community, runBack) }

        addButton(
            slot = 34,
            name = Translator.tr("ui.community.button.interaction.invite").string,
            item = Items.VILLAGER_SPAWN_EGG
        ) { runOpenInviteMemberMenu(player, community) { runBackToCommunityMenu(player, community, runBack) } }
    }
}
