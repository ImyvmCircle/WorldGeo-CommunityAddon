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
import com.imyvm.community.WorldGeoCommunityAddon
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

fun validateInvitationSender(inviter: ServerPlayerEntity, community: Community): Boolean {
    val inviterRole = community.getMemberRole(inviter.uuid)
    if (inviterRole == null || inviterRole == MemberRoleType.APPLICANT || inviterRole == MemberRoleType.REFUSED) {
        inviter.sendMessage(Translator.tr("community.invite.error.no_permission"))
        return false
    }
    
    val cost = if (community.isManor()) CommunityConfig.COMMUNITY_JOIN_COST_MANOR.value else CommunityConfig.COMMUNITY_JOIN_COST_REALM.value
    if (community.getTotalAssets() < cost) {
        inviter.sendMessage(Translator.tr("community.invite.error.insufficient_assets", cost / 100.0))
        return false
    }
    
    return true
}

fun validateInvitationTarget(inviter: ServerPlayerEntity, target: ServerPlayerEntity, community: Community): Boolean {
    if (!checkPlayerMembershipJoin(target, community)) {
        inviter.sendMessage(Translator.tr("community.invite.error.target_ineligible", target.name.string))
        return false
    }
    
    if (!checkMemberNumberManor(inviter, community)) {
        return false
    }
    
    return true
}

fun sendInvitation(inviter: ServerPlayerEntity, target: ServerPlayerEntity, community: Community) {
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val timeoutMinutes = com.imyvm.community.infra.CommunityConfig.INVITATION_RESPONSE_TIMEOUT_MINUTES.value
    
    community.member[target.uuid] = MemberAccount(
        joinedTime = System.currentTimeMillis(),
        basicRoleType = MemberRoleType.APPLICANT,
        isInvited = true
    )
    
    val expireAt = System.currentTimeMillis() + timeoutMinutes * 60 * 1000L
    WorldGeoCommunityAddon.pendingInvitations[target.uuid] = 
        com.imyvm.community.domain.CommunityInvitation(
            inviterUUID = inviter.uuid,
            inviteeUUID = target.uuid,
            communityRegionId = community.regionNumberId!!,
            expireAt = expireAt
        )
    
    com.imyvm.community.infra.CommunityDatabase.save()
    
    val acceptText = net.minecraft.text.Text.literal("[")
        .append(Translator.tr("community.invite.button.accept") ?: net.minecraft.text.Text.literal("Accept"))
        .append(net.minecraft.text.Text.literal("]"))
        .styled { style ->
            style.withColor(net.minecraft.util.Formatting.GREEN)
                .withClickEvent(net.minecraft.text.ClickEvent(
                    net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                    "/community accept_invitation ${community.regionNumberId}"
                ))
                .withHoverEvent(net.minecraft.text.HoverEvent(
                    net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                    Translator.tr("community.invite.button.accept.hover") 
                        ?: net.minecraft.text.Text.literal("Click to accept invitation")
                ))
        }
    
    val rejectText = net.minecraft.text.Text.literal("[")
        .append(Translator.tr("community.invite.button.reject") ?: net.minecraft.text.Text.literal("Reject"))
        .append(net.minecraft.text.Text.literal("]"))
        .styled { style ->
            style.withColor(net.minecraft.util.Formatting.RED)
                .withClickEvent(net.minecraft.text.ClickEvent(
                    net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                    "/community reject_invitation ${community.regionNumberId}"
                ))
                .withHoverEvent(net.minecraft.text.HoverEvent(
                    net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                    Translator.tr("community.invite.button.reject.hover") 
                        ?: net.minecraft.text.Text.literal("Click to reject invitation")
                ))
        }
    
    val invitationMessage = Translator.tr(
        "community.invite.received",
        inviter.name.string,
        communityName,
        timeoutMinutes
    )
    
    if (invitationMessage != null) {
        target.sendMessage(
            invitationMessage.copy()
                .append(net.minecraft.text.Text.literal(" "))
                .append(acceptText)
                .append(net.minecraft.text.Text.literal(" "))
                .append(rejectText)
        )
    }
    
    inviter.sendMessage(Translator.tr("community.invite.sent", target.name.string, communityName))
}

fun onAcceptInvitation(player: ServerPlayerEntity, community: Community) {
    val invitation = WorldGeoCommunityAddon.pendingInvitations[player.uuid]
    val memberAccount = community.member[player.uuid]
    
    if (invitation == null || memberAccount == null || !memberAccount.isInvited || memberAccount.basicRoleType != MemberRoleType.APPLICANT) {
        player.sendMessage(Translator.tr("community.invite.error.no_invitation"))
        return
    }
    
    if (invitation.expireAt <= System.currentTimeMillis()) {
        player.sendMessage(Translator.tr("community.invite.error.expired"))
        WorldGeoCommunityAddon.pendingInvitations.remove(player.uuid)
        community.member.remove(player.uuid)
        com.imyvm.community.infra.CommunityDatabase.save()
        return
    }
    
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    player.sendMessage(Translator.tr("community.invite.accepted", communityName))
    
    com.imyvm.community.infra.CommunityDatabase.save()
}

fun onRejectInvitation(player: ServerPlayerEntity, community: Community) {
    val invitation = WorldGeoCommunityAddon.pendingInvitations[player.uuid]
    val memberAccount = community.member[player.uuid]
    
    if (invitation == null || memberAccount == null || !memberAccount.isInvited || memberAccount.basicRoleType != MemberRoleType.APPLICANT) {
        player.sendMessage(Translator.tr("community.invite.error.no_invitation"))
        return
    }
    
    WorldGeoCommunityAddon.pendingInvitations.remove(player.uuid)
    community.member.remove(player.uuid)
    
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    player.sendMessage(Translator.tr("community.invite.rejected", communityName))
    
    val inviterPlayer = player.server.playerManager?.getPlayer(invitation.inviterUUID)
    inviterPlayer?.sendMessage(Translator.tr("community.invite.rejected.inviter", player.name.string, communityName))
    
    com.imyvm.community.infra.CommunityDatabase.save()
}