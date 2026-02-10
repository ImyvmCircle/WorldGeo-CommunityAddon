package com.imyvm.community.application.interaction.common

import com.imyvm.community.application.interaction.common.helper.checkPlayerMembershipJoin
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.permission.PermissionCheck
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.MemberAccount
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.domain.community.CommunityStatus
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.entrypoints.screen.ConfirmMenu
import com.imyvm.community.entrypoints.screen.component.ConfirmTaskType
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.util.Translator
import com.imyvm.economy.EconomyMod
import net.minecraft.server.network.ServerPlayerEntity

fun onJoinCommunity(player: ServerPlayerEntity, targetCommunity: Community): Int {
    if (!checkPlayerMembershipJoin(player, targetCommunity)) return 0
    if (!checkMemberNumberManor(player, targetCommunity)) return 0
    if (!checkPlayerHasEnoughCurrency(player, targetCommunity)) return 0

    showJoinConfirmMenu(player, targetCommunity)
    return 1
}

fun onLeaveCommunity(player: ServerPlayerEntity, targetCommunity: Community): Int {
    val permissionResult = PermissionCheck.canQuitCommunity(player, targetCommunity)
    if (!permissionResult.isAllowed()) {
        permissionResult.sendFeedback(player)
        return 0
    }

    showLeaveConfirmMenu(player, targetCommunity)
    return 1
}

private fun showLeaveConfirmMenu(player: ServerPlayerEntity, targetCommunity: Community) {
    val communityName = targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
    val cautions = listOf(
        Translator.tr("ui.confirm.leave.caution", communityName)?.string 
            ?: "Leave $communityName? You cannot undo this action."
    )
    
    CommunityMenuOpener.open(player) { syncId ->
        ConfirmMenu(
            syncId = syncId,
            playerExecutor = player,
            confirmTaskType = ConfirmTaskType.LEAVE_COMMUNITY,
            cautions = cautions,
            runBack = { it.closeHandledScreen() },
            targetCommunity = targetCommunity
        )
    }
}

private fun checkPlayerHasEnoughCurrency(player: ServerPlayerEntity, targetCommunity: Community): Boolean {
    val totalAssets = EconomyMod.data.getOrCreate(player).money
    val cost = if(targetCommunity.isManor()) CommunityConfig.COMMUNITY_JOIN_COST_MANOR.value else CommunityConfig.COMMUNITY_JOIN_COST_REALM.value

    if (totalAssets < cost) {
        player.sendMessage(
            Translator.tr("community.join.error.insufficient_assets", cost / 100.0, totalAssets)
        )
        return false
    }
    
    return true
}

private fun showJoinConfirmMenu(player: ServerPlayerEntity, targetCommunity: Community) {
    val communityName = targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
    val cautions = listOf(
        Translator.tr("ui.confirm.join.caution", communityName, 500)?.string 
            ?: "Join $communityName for 500 assets?"
    )
    
    CommunityMenuOpener.open(player) { syncId ->
        ConfirmMenu(
            syncId = syncId,
            playerExecutor = player,
            confirmTaskType = ConfirmTaskType.JOIN_COMMUNITY,
            cautions = cautions,
            runBack = { it.closeHandledScreen() },
            targetCommunity = targetCommunity
        )
    }
}

fun onJoinCommunityDirectly(player: ServerPlayerEntity, targetCommunity: Community): Int {
    return tryJoinByPolicy(player, targetCommunity)
}

fun checkMemberNumberManor(player: ServerPlayerEntity,targetCommunity: Community): Boolean {
    if (CommunityConfig.IS_CHECKING_MANOR_MEMBER_SIZE.value) {
        if ((targetCommunity.status == CommunityStatus.ACTIVE_MANOR  || targetCommunity.status == CommunityStatus.PENDING_MANOR) &&
            targetCommunity.member.count {
                targetCommunity.getMemberRole(it.key) != MemberRoleType.APPLICANT &&
                        targetCommunity.getMemberRole(it.key) != MemberRoleType.REFUSED
            } >= CommunityConfig.MIN_NUMBER_MEMBER_REALM.value) {
            player.sendMessage(Translator.tr("community.join.error.full", CommunityConfig.MIN_NUMBER_MEMBER_REALM.value))
            return false
        }
    }
    return true
}

fun tryJoinByPolicy(player: ServerPlayerEntity, targetCommunity: Community): Int {
    when (targetCommunity.joinPolicy) {
        CommunityJoinPolicy.OPEN -> return joinUnderOpenPolicy(player, targetCommunity)
        CommunityJoinPolicy.APPLICATION -> return joinUnderApplicationPolicy(player, targetCommunity)
        CommunityJoinPolicy.INVITE_ONLY -> joinUnderInviteOnlyPolicy(player, targetCommunity)
    }

    return 0
}

private fun joinUnderOpenPolicy(player: ServerPlayerEntity, targetCommunity: Community): Int {
    targetCommunity.member[player.uuid] = MemberAccount(
        joinedTime = System.currentTimeMillis(),
        basicRoleType = MemberRoleType.MEMBER
    )
    player.sendMessage(Translator.tr("community.join.success", targetCommunity.regionNumberId))
    return 1
}

private fun joinUnderApplicationPolicy(player: ServerPlayerEntity, targetCommunity: Community): Int {
    if (targetCommunity.member.containsKey(player.uuid)) {
        player.sendMessage(Translator.tr("community.join.error.already_applied", targetCommunity.regionNumberId))
        return 0
    }
    targetCommunity.member[player.uuid] = MemberAccount(
        joinedTime = System.currentTimeMillis(),
        basicRoleType = MemberRoleType.APPLICANT
    )
    player.sendMessage(targetCommunity.getRegion()
        ?.let { Translator.tr("community.join.applied", it.name ,targetCommunity.regionNumberId) })
    return 1
}

private fun joinUnderInviteOnlyPolicy(player: ServerPlayerEntity, targetCommunity: Community): Int {
    player.sendMessage(Translator.tr("community.join.error.invite_only", targetCommunity.regionNumberId))
    return 0
}