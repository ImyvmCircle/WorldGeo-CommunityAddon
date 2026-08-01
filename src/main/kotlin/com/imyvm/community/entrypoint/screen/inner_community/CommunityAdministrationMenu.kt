package com.imyvm.community.entrypoint.screen.inner_community

import com.imyvm.community.application.interaction.screen.inner_community.*
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenAnnouncementListMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class CommunityAdministrationMenu(
    syncId: Int,
    community: Community,
    playerExecutor: ServerPlayer,
    val runBack : ((ServerPlayer) -> Unit)
): AbstractMenu(
    syncId,
    menuTitle = generateCommunityOperationMenuTitle(community, playerExecutor),
    runBack = runBack
){
    init {
        addStaticButtons(playerExecutor, community)
        addChangeableButtons(playerExecutor, community)
    }

    private fun addStaticButtons(player: ServerPlayer, community: Community) {
        addButton(
            slot = 10,
            name = Translator.tr("ui.admin.button.members").string ?: "Manage Members",
            item = Items.PLAYER_HEAD
        ) { runAdmManageMembers(player, community, runBack) }

        addButton(
            slot = 11,
            name = Translator.tr("ui.admin.button.audit").string ?: "Community Audit",
            item = Items.REDSTONE_TORCH
        ) { runAdmAuditRequests(player, community, runBack) }

        addButton(
            slot = 12,
            name = Translator.tr("ui.admin.button.announcement").string ?: "Announcement",
            item = Items.PAPER
        ) { runOpenAnnouncementListMenu(player, community) { runBackToCommunityAdministrationMenu(player, community, runBack) } }

        addButton(
            slot = 13,
            name = Translator.tr("ui.admin.button.treasury_grant").string ?: "Grant Coins from Treasury",
            item = Items.GOLD_INGOT
        ) { runAdmGrantCoins(player, community, runBack) }

        addButton(
            slot = 14,
            name = fiscalPolicyButtonName(community),
            item = Items.EMERALD,
            loreLines = fiscalPolicyLore(community)
        ) { runAdmChangeFiscalPolicy(player, community, nextFiscalPolicy(community.fiscalState.pendingPolicy?.policy ?: community.fiscalState.activePolicy), runBack) }

        addButton(
            slot = 19,
            name = Translator.tr("ui.admin.button.name").string ?: "Community Name",
            item = Items.NAME_TAG
        ) {
            runAdmRegion(
                player,
                community,
                geographicFunctionType = GeographicFunctionType.NAME_MODIFICATION,
                runBack
            )
        }

        addButton(
            slot = 20,
            name = Translator.tr("ui.admin.button.region.geometry").string ?: "Region Geographic Scope",
            item = Items.MAP
        ) {
            runAdmRegion(
                player,
                community,
                geographicFunctionType = GeographicFunctionType.GEOMETRY_MODIFICATION,
                runBack
            )
        }

        addButton(
            slot = 21,
            name = Translator.tr("ui.admin.button.region.setting").string ?: "Region Settings",
            item = Items.HEART_OF_THE_SEA
        ) {
            runAdmRegion(
                player,
                community,
                geographicFunctionType = GeographicFunctionType.SETTING_ADJUSTMENT,
                runBack
            )
        }

        addButton(
            slot = 22,
            name = Translator.tr("ui.admin.button.teleport").string ?: "Teleport Points",
            item = Items.ENDER_PEARL
        ) {
            runAdmRegion(
                player,
                community,
                geographicFunctionType = GeographicFunctionType.TELEPORT_POINT_LOCATING,
                runBack
            )
        }
    }

    private fun addChangeableButtons(player: ServerPlayer, community: Community) {
        addButton(
            slot = 28,
            name = joinPolicyButtonName(community),
            item = when (community.joinPolicy) {
                CommunityJoinPolicy.OPEN -> Items.WOOL.green()
                CommunityJoinPolicy.APPLICATION -> Items.WOOL.yellow()
                CommunityJoinPolicy.INVITE_ONLY -> Items.WOOL.red()
            }
        ) { runAdmChangeJoinPolicy(player, community, community.joinPolicy, runBack) }

        addButton(
            slot = 29,
            name = Translator.tr("ui.admin.button.building").string ?: "Community Building",
            item = Items.BRICKS
        ) { runOpenCommunityBuildingMenu(player, community, runBack) }

        addButton(
            slot = 30,
            name = Translator.tr("ui.admin.button.title").string ?: "Titles",
            item = Items.NAME_TAG
        ) { runAdmTitle(player, community, runBack) }
    }

    private fun joinPolicyButtonName(community: Community): String =
        (Translator.tr("ui.admin.button.join_policy").string ?: "Join Policy: ") + joinPolicyName(community.joinPolicy)

    private fun fiscalPolicyButtonName(community: Community): String =
        (Translator.tr("ui.admin.button.fiscal").string ?: "Fiscal Policy: ") + fiscalPolicyName(community.fiscalState.pendingPolicy?.policy ?: community.fiscalState.activePolicy)

    private fun fiscalPolicyLore(community: Community): List<Component> {
        val pending = community.fiscalState.pendingPolicy
        val lines = mutableListOf<Component>(
            Translator.tr("ui.admin.fiscal.lore.active", fiscalPolicyName(community.fiscalState.activePolicy))
        )
        if (pending != null) {
            lines.add(Translator.tr("ui.admin.fiscal.lore.pending", fiscalPolicyName(pending.policy), pending.effectiveWeekKey))
            lines.add(Translator.tr("ui.admin.fiscal.lore.cooldown", pending.cooldownUntilWeekKey))
        }
        return lines
    }

    private fun nextFiscalPolicy(policy: CommunityFiscalPolicy): CommunityFiscalPolicy = when (policy) {
        CommunityFiscalPolicy.NEOLIBERALISM -> CommunityFiscalPolicy.VISIBLE_HAND
        CommunityFiscalPolicy.VISIBLE_HAND -> CommunityFiscalPolicy.HEAVEN_ON_EARTH
        CommunityFiscalPolicy.HEAVEN_ON_EARTH -> CommunityFiscalPolicy.ANARCHISM
        CommunityFiscalPolicy.ANARCHISM -> CommunityFiscalPolicy.NEOLIBERALISM
    }

    private fun joinPolicyName(policy: CommunityJoinPolicy): String = Translator.tr("community.join_policy.${policy.name.lowercase()}").string
    private fun fiscalPolicyName(policy: CommunityFiscalPolicy): String = Translator.tr("community.fiscal.policy.${policy.name.lowercase()}").string

    companion object {
        private fun generateCommunityOperationMenuTitle(
            community: Community,
            playerExecutor: ServerPlayer
        ): Component {
            return Component.literal(
                community.generateCommunityMark()
                        + " - " + Translator.tr("ui.admin.title").string
                        + ":" + playerExecutor.name.string
            )
        }
    }
}
