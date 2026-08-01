package com.imyvm.community.entrypoint.screen.inner_community.building

import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingMenu
import com.imyvm.community.application.interaction.screen.inner_community.runSaveCommunityBuildingDraft
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

class CommunityBuildingEditorMenu(
    syncId: Int,
    private val player: ServerPlayer,
    private val community: Community,
    private val parentBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.community.building.editor.title", community.generateCommunityMark()),
    runBack = { runOpenCommunityBuildingMenu(it, community, parentBack) }
) {
    init {
        val draft = requireNotNull(CommunityBuildingService.getDraft(player.uuid))
        addButton(10, draft.baseBlockId, CommunityBuildingService.getBlockItem(draft.baseBlockId)) {}
        addButton(20, Translator.tr("ui.community.building.editor.unit.value", draft.unitCost.toString()).string, Items.PAPER) {}
        addButton(29, Translator.tr("ui.community.building.editor.reward.value", CommunityBuildingService.formatMoney(draft.rewardPerBlock)).string, Items.GOLD_NUGGET) {}
        addButton(32, Translator.tr("ui.community.building.editor.linked_count", draft.linkedBlockIds.size.toString()).string, Items.IRON_BARS) {}
        addButton(41, Translator.tr("ui.community.building.editor.save").string, Items.EMERALD_BLOCK) {
            runSaveCommunityBuildingDraft(it, community, parentBack)
        }
    }
}
