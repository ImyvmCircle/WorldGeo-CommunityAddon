package com.imyvm.community.entrypoints.screen.inner_community

import com.imyvm.community.application.interaction.screen.inner_community.*
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenAnnouncementListMenu
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenAssetsMenu
import com.imyvm.community.application.permission.PermissionCheck
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.GeographicFunctionType
import com.imyvm.community.domain.community.AdministrationPermission
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityAdministrationMenu(
    syncId: Int,
    community: Community,
    playerExecutor: ServerPlayerEntity,
    val runBack : ((ServerPlayerEntity) -> Unit)
): AbstractMenu(
    syncId,
    menuTitle = generateCommunityOperationMenuTitle(community, playerExecutor),
    runBack = runBack
){
    init {
        addStaticButtons(playerExecutor, community)
        addChangeableButtons(playerExecutor, community)
    }

    private fun addStaticButtons(player: ServerPlayerEntity, community: Community) {

        if (PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.RENAME_COMMUNITY).isAllowed()) {
            addButton(
                slot = 10,
                name = Translator.tr("ui.community.administration.button.name")?.string ?: "Community Name",
                item = Items.NAME_TAG
            ) { runAdmRenameCommunity(player, community, runBack) }
        }

        if (PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_MEMBERS).isAllowed()) {
            addButton(
                slot = 11,
                name = Translator.tr("ui.community.administration.button.members")?.string ?: "Manage Members",
                item = Items.PLAYER_HEAD
            ) { runAdmManageMembers(player, community, runBack) }
        }

        if (PermissionCheck.canAuditApplications(player, community).isAllowed()) {
            addButton(
                slot = 12,
                name = Translator.tr("ui.community.administration.button.audit")?.string ?: "Community Audit",
                item = Items.REDSTONE_TORCH
            ) { runAdmAuditRequests(player, community, runBack) }
        }

        if (PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ANNOUNCEMENTS).isAllowed()) {
            addButton(
                slot = 13,
                name = Translator.tr("ui.community.administration.button.announcement")?.string ?: "Announcement",
                item = Items.PAPER
            ) { runOpenAnnouncementListMenu(player, community, runBack) }
        }

        if (PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ADVANCEMENT).isAllowed()) {
            addButton(
                slot = 14,
                name = Translator.tr("ui.community.administration.button.advancement")?.string ?: "Advancement",
                item = Items.ITEM_FRAME
            ) { runAdmAdvancement(player, community, runBack) }
        }

        if (PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_ASSETS).isAllowed()) {
            addButton(
                slot = 15,
                name = Translator.tr("ui.community.administration.button.assets")?.string ?: "Assets",
                item = Items.EMERALD_ORE
            ) { runOpenAssetsMenu(player, community, runBack) }
        }

        if (PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MODIFY_REGION_GEOMETRY).isAllowed()) {
            addButton(
                slot = 19,
                name = Translator.tr("ui.community.administration.button.region.geometry")?.string ?: "Region Geometry Modification",
                item = Items.MAP
            ) {
                runAdmRegion(
                    player,
                    community,
                    geographicFunctionType = GeographicFunctionType.GEOMETRY_MODIFICATION,
                    runBack
                )
            }
        }

        if (PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MODIFY_REGION_SETTINGS).isAllowed()) {
            addButton(
                slot = 20,
                name = Translator.tr("ui.community.administration.button.region.setting")?.string ?: "Region Settings",
                item = Items.HEART_OF_THE_SEA
            ) {
                runAdmRegion(
                    player,
                    community,
                    geographicFunctionType = GeographicFunctionType.SETTING_ADJUSTMENT,
                    runBack
                )
            }
        }

        if (PermissionCheck.canExecuteAdministration(player, community, AdministrationPermission.MANAGE_TELEPORT_POINTS).isAllowed()) {
            addButton(
                slot = 21,
                name = Translator.tr("ui.community.administration.button.teleport")?.string ?: "Teleport Point Management",
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
    }

    private fun addChangeableButtons(player: ServerPlayerEntity, community: Community) {
        if (PermissionCheck.canChangeJoinPolicy(player, community).isAllowed()) {
            addButton(
                slot = 28,
                name = (Translator.tr("ui.community.administration.button.join_policy")?.string
                    ?: "Join Policy: ") + community.joinPolicy.toString(),
                item = when (community.joinPolicy) {
                    CommunityJoinPolicy.OPEN -> Items.GREEN_WOOL
                    CommunityJoinPolicy.APPLICATION -> Items.YELLOW_WOOL
                    CommunityJoinPolicy.INVITE_ONLY -> Items.RED_WOOL
                }
            ) { runAdmChangeJoinPolicy(player, community, community.joinPolicy, runBack) }
        }

        if (PermissionCheck.canToggleCouncil(player, community).isAllowed()) {
            addButton(
                slot = 29,
                name = (Translator.tr("ui.community.administration.button.council")?.string
                    ?: "Council: ") + if (community.council.enabled) "Enabled" else "Disabled",
                item = if (community.council.enabled) Items.LIME_DYE else Items.GRAY_DYE
            ) { runAdmToggleCouncil(player, community, runBack) }
        }
    }

    companion object {
        private fun generateCommunityOperationMenuTitle(community: Community, playerExecutor: ServerPlayerEntity): Text {
            return Text.of(
                community.generateCommunityMark()
                        + " - " + Translator.tr("ui.community.administration.title")?.string
                        + ":" + playerExecutor.name.string
            )
        }
    }
}