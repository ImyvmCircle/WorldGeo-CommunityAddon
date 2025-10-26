package com.imyvm.community.inter.screen.inner_community

import com.imyvm.community.application.interaction.screen.inner_community.runOPRenameCommunity
import com.imyvm.community.application.interaction.screen.inner_community.runOpManageMembers
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.inter.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityOperationMenu(
    syncId: Int,
    community: Community,
    player: ServerPlayerEntity
): AbstractMenu(
    syncId,
    menuTitle = Text.of(
        community.generateCommunityMark()
                + " - " + Translator.tr("ui.community.operation.title")?.string
                + ":" + player.name.string
    )
){
    init {
        addStaticButtons(player, community)
        addChangeableButtons(player, community)
    }

    private fun addStaticButtons(player: ServerPlayerEntity, community: Community) {
        addButton(
            slot = 10,
            name = Translator.tr("ui.community.operation.button.name")?.string ?: "Community Name",
            item = Items.NAME_TAG
        ) { runOPRenameCommunity(player, community) }

        addButton(
            slot = 11,
            name = Translator.tr("ui.community.operation.button.members")?.string ?: "Manage Members",
            item = Items.PLAYER_HEAD
        ) { runOpManageMembers(player, community) }

        addButton(
            slot = 12,
            name = Translator.tr("ui.community.operation.button.audit")?.string ?: "Community Audit",
            item = Items.REDSTONE_TORCH
        ){}

        addButton(
            slot = 13,
            name = Translator.tr("ui.community.operation.button.scope")?.string ?: "Scope",
            item = Items.MAP
        ){}

        addButton(
            slot = 14,
            name = Translator.tr("ui.community.operation.button.announcement")?.string ?: "Announcement",
            item = Items.PAPER
        ) {}

        addButton(
            slot = 15,
            name = Translator.tr("ui.community.operation.button.advancement")?.string ?: "Advancement",
            item = Items.ITEM_FRAME
        ) {}

        addButton(
            slot = 16,
            name = Translator.tr("ui.community.operation.button.assets")?.string ?: "Assets",
            item = Items.EMERALD_ORE
        ) {}
    }

    private fun addChangeableButtons(player: ServerPlayerEntity, community: Community) {
        addButton(
            slot = 19,
            name = Translator.tr("ui.community.operation.button.join_policy")?.string
                ?: ("Join Policy: " + community.joinPolicy.toString()),
            item = when (community.joinPolicy) {
                CommunityJoinPolicy.OPEN -> Items.GREEN_WOOL
                CommunityJoinPolicy.APPLICATION -> Items.YELLOW_WOOL
                CommunityJoinPolicy.INVITE_ONLY -> Items.RED_WOOL
            }
        ) {}
    }
}