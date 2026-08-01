package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.townbuilding.CommunityBuildingDraft
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingCandidateListMenu
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingEditorMenu
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingMenu
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingPoolMenu
import com.imyvm.community.entrypoint.screen.inner_community.building.CommunityBuildingStyleListMenu
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer

fun runSendCommunityBuildingAdministrationSummary(player: ServerPlayer, community: Community) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    player.closeContainer()
    CommunityBuildingService.sendAdministrationSummary(player, community)
}

fun runOpenCommunityBuildingPoolMenu(player: ServerPlayer, page: Int, runBack: (ServerPlayer) -> Unit) {
    if (!net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
        player.sendSystemMessage(Translator.tr("command.community.permission.op_required"))
        return
    }
    CommunityMenuOpener.open(player) { syncId -> CommunityBuildingPoolMenu(syncId, player, page, runBack) }
}

fun runOpenCommunityBuildingMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    val permission = CommunityPermissionPolicy.canViewCommunity(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    CommunityMenuOpener.open(player) { syncId ->
        CommunityBuildingMenu(syncId, player, community, runBackGrandfather)
    }
}

fun runOpenCommunityBuildingStyleList(player: ServerPlayer, community: Community, adminMode: Boolean, page: Int, runBack: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityBuildingStyleListMenu(syncId, player, community, adminMode, page, runBack)
    }
}

fun runOpenCommunityBuildingCandidates(player: ServerPlayer, community: Community, page: Int, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    CommunityMenuOpener.open(player) { syncId ->
        CommunityBuildingCandidateListMenu(syncId, player, community, page, runBack)
    }
}

fun runOpenCommunityBuildingEditor(player: ServerPlayer, community: Community, draft: CommunityBuildingDraft, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    CommunityBuildingService.setDraft(player.uuid, draft)
    CommunityMenuOpener.open(player) { syncId ->
        CommunityBuildingEditorMenu(syncId, player, community, runBack)
    }
}

fun runSaveCommunityBuildingDraft(player: ServerPlayer, community: Community, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    val draft = CommunityBuildingService.getDraft(player.uuid) ?: return
    val result = CommunityBuildingService.upsertEntry(community, draft.baseBlockId, draft.unitCost, draft.rewardPerBlock, draft.linkedBlockIds)
    if (result.isSuccess) {
        player.sendSystemMessage(Translator.tr("community.building.entry.saved", draft.baseBlockId))
        CommunityBuildingService.clearDraft(player.uuid)
        runOpenCommunityBuildingStyleList(player, community, true, 0, runBack)
    } else {
        player.sendSystemMessage(Translator.tr("community.building.error.save", result.exceptionOrNull()?.message ?: "error"))
        runOpenCommunityBuildingEditor(player, community, draft, runBack)
    }
}

fun runRemoveCommunityBuildingEntry(player: ServerPlayer, community: Community, baseBlockId: String, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    val result = CommunityBuildingService.removeEntry(community, baseBlockId)
    if (result.isSuccess) {
        player.sendSystemMessage(Translator.tr("community.building.entry.removed", baseBlockId))
    } else {
        player.sendSystemMessage(Translator.tr("community.building.error.remove", result.exceptionOrNull()?.message ?: "error"))
    }
    runOpenCommunityBuildingStyleList(player, community, true, 0, runBack)
}

fun runBuyCommunityBuildingCapacity(player: ServerPlayer, community: Community, buyUnits: Int, runBack: (ServerPlayer) -> Unit) {
    val permission = adminPermission(player, community)
    if (permission.isDenied()) {
        permission.sendSuccess(player)
        return
    }
    val result = CommunityBuildingService.buyCapacity(community, buyUnits)
    if (result.isSuccess) {
        player.sendSystemMessage(Translator.tr("community.building.capacity.bought", buyUnits.toString(), CommunityBuildingService.formatMoney(result.getOrThrow())))
    } else {
        player.sendSystemMessage(Translator.tr("community.building.error.capacity", result.exceptionOrNull()?.message ?: "error"))
    }
    runOpenCommunityBuildingMenu(player, community, runBack)
}

private fun adminPermission(player: ServerPlayer, community: Community) =
    CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_BUILDING).let { adminCheck ->
        if (adminCheck.isDenied()) adminCheck
        else CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_BUILDING)
    }
