package com.imyvm.community.entrypoint.screen.inner_community.building

import com.imyvm.community.application.interaction.screen.inner_community.runBuyCommunityBuildingCapacity
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingCandidates
import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingStyleList
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

class CommunityBuildingMenu(
    syncId: Int,
    private val player: ServerPlayer,
    private val community: Community,
    private val parentBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.building.title", community.generateCommunityMark()),
    runBack = parentBack
) {
    init {
        val state = CommunityBuildingService.getState(community)
        addButton(10, Translator.tr("ui.community.building.button.package").string, Items.BRICKS) {
            runOpenCommunityBuildingStyleList(it, community, false, 0, parentBack)
        }
        addButton(12, Translator.tr("ui.community.building.button.personal_income", CommunityBuildingService.formatMoney(CommunityBuildingService.getPlayerWeekIncome(community, player.uuid))).string, Items.GOLD_INGOT) {}
        addButton(14, Translator.tr("ui.community.building.button.personal_remaining", CommunityBuildingService.formatMoney(CommunityBuildingService.getPlayerWeekRemainingCap(community, player.uuid))).string, Items.CLOCK) {}
        addButton(16, Translator.tr("ui.community.building.button.next_settlement", CommunityBuildingService.getNextHourSettlementText()).string, Items.COMPASS) {}
        addButton(28, Translator.tr("ui.community.building.button.capacity", state.usedCapacityUnits().toString(), state.capacityUnits.toString()).string, Items.CHEST) {}
        addButton(30, Translator.tr("ui.community.building.button.treasury", CommunityBuildingService.formatMoney(community.getTotalAssets())).string, Items.EMERALD) {}
        addButton(32, Translator.tr("ui.community.building.button.pending", state.pendingPayouts.count { it.playerUuid == player.uuid }.toString()).string, Items.PAPER) {}
        if (CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_BUILDING).isAllowed()) {
            addButton(37, Translator.tr("ui.community.building.button.manage_entries").string, Items.IRON_PICKAXE) {
                runOpenCommunityBuildingStyleList(it, community, true, 0, parentBack)
            }
            addButton(39, Translator.tr("ui.community.building.button.add_entry").string, Items.WOOL.lime()) {
                runOpenCommunityBuildingCandidates(it, community, 0, parentBack)
            }
            addButton(41, Translator.tr("ui.community.building.button.buy_capacity_one").string, Items.CHEST_MINECART) {
                runBuyCommunityBuildingCapacity(it, community, 1, parentBack)
            }
            addButton(42, Translator.tr("ui.community.building.button.buy_capacity_eight").string, Items.MINECART) {
                runBuyCommunityBuildingCapacity(it, community, 8, parentBack)
            }
        }
    }
}
