package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.common.helper.checkPlayerMembershipJoin
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.entrypoint.screen.ConfirmMenu
import com.imyvm.community.entrypoint.screen.component.ConfirmTaskType
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import com.imyvm.economy.EconomyMod
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import java.util.*
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent

private fun getInvitationKey(inviteeUUID: UUID): Int {
    return inviteeUUID.hashCode()
}

fun notifyOfficials(community: Community, server: net.minecraft.server.MinecraftServer, message: Component, executor: ServerPlayer? = null) {
    for ((memberUUID, memberAccount) in community.member) {
        val isOfficial = memberAccount.basicRoleType == MemberRoleType.OWNER ||
                        memberAccount.basicRoleType == MemberRoleType.ADMIN
        
        if (isOfficial) {
            val officialPlayer = server.playerList.getPlayer(memberUUID)
            officialPlayer?.sendSystemMessage(message)
            memberAccount.mail.add(message)
        }
    }
}

fun notifyTargetPlayer(server: net.minecraft.server.MinecraftServer, targetUUID: UUID, message: Component, community: Community) {
    val targetPlayer = server.playerList.getPlayer(targetUUID)
    targetPlayer?.sendSystemMessage(message)
    
    val targetAccount = community.member[targetUUID]
    targetAccount?.mail?.add(message)
}

fun onJoinCommunity(player: ServerPlayer, targetCommunity: Community): Int {
    if (!checkPlayerMembershipJoin(player, targetCommunity)) return 0
    if (!checkMemberNumberManor(player, targetCommunity)) return 0
    if (!checkPlayerHasEnoughCurrency(player, targetCommunity)) return 0

    showJoinConfirmMenu(player, targetCommunity)
    return 1
}

fun onLeaveCommunity(player: ServerPlayer, targetCommunity: Community): Int {
    val permissionResult = CommunityPermissionPolicy.canQuitCommunity(player, targetCommunity)
    if (!permissionResult.isAllowed()) {
        permissionResult.sendSuccess(player)
        return 0
    }

    showLeaveConfirmMenu(player, targetCommunity)
    return 1
}

private fun showLeaveConfirmMenu(player: ServerPlayer, targetCommunity: Community) {
    val communityName = targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
    val cautions = listOf(
        Translator.tr("ui.confirm.leave.caution", communityName).string 
            ?: "Leave $communityName? You cannot undo this action."
    )
    
    CommunityMenuOpener.open(player) { syncId ->
        ConfirmMenu(
            syncId = syncId,
            playerExecutor = player,
            confirmTaskType = ConfirmTaskType.LEAVE_COMMUNITY,
            cautions = cautions,
            runBack = { it.closeContainer() },
            targetCommunity = targetCommunity
        )
    }
}

private fun checkPlayerHasEnoughCurrency(player: ServerPlayer, targetCommunity: Community): Boolean {
    val totalAssets = EconomyMod.data.getOrCreate(player).money
    val cost = if(targetCommunity.isManor()) PricingConfig.COMMUNITY_JOIN_COST_MANOR.value else PricingConfig.COMMUNITY_JOIN_COST_REALM.value

    if (totalAssets < cost) {
        player.sendSystemMessage(
            Translator.tr("community.join.error.insufficient_assets", cost / 100.0, totalAssets)
        )
        return false
    }
    
    return true
}

private fun showJoinConfirmMenu(player: ServerPlayer, targetCommunity: Community) {
    val communityName = targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
    val cost = ((if(targetCommunity.isManor()) PricingConfig.COMMUNITY_JOIN_COST_MANOR.value else PricingConfig.COMMUNITY_JOIN_COST_REALM.value)/100.00).toString()
    val cautions = listOf(
        Translator.tr("ui.confirm.join.caution", communityName, cost).string
            ?: "Join $communityName for $cost assets?"
    )
    
    CommunityMenuOpener.open(player) { syncId ->
        ConfirmMenu(
            syncId = syncId,
            playerExecutor = player,
            confirmTaskType = ConfirmTaskType.JOIN_COMMUNITY,
            cautions = cautions,
            runBack = { it.closeContainer() },
            targetCommunity = targetCommunity
        )
    }
}

fun onJoinCommunityDirectly(player: ServerPlayer, targetCommunity: Community): Int {
    return tryJoinByPolicy(player, targetCommunity)
}

fun checkMemberNumberManor(player: ServerPlayer,targetCommunity: Community): Boolean {
    if (CommunityConfig.IS_CHECKING_MANOR_MEMBER_SIZE.value) {
        if ((targetCommunity.status == CommunityStatus.ACTIVE_MANOR  || targetCommunity.status == CommunityStatus.PENDING_MANOR) &&
            targetCommunity.member.count {
                targetCommunity.getMemberRole(it.key) != MemberRoleType.APPLICANT &&
                        targetCommunity.getMemberRole(it.key) != MemberRoleType.REFUSED
            } >= CommunityConfig.MAX_MEMBER_MANOR.value) {
            player.sendSystemMessage(Translator.tr("community.join.error.full", targetCommunity.getRegion()?.name, CommunityConfig.MAX_MEMBER_MANOR.value))
            return false
        }
    }
    return true
}

fun tryJoinByPolicy(player: ServerPlayer, targetCommunity: Community): Int {
    when (targetCommunity.joinPolicy) {
        CommunityJoinPolicy.OPEN -> return joinUnderOpenPolicy(player, targetCommunity)
        CommunityJoinPolicy.APPLICATION -> return joinUnderApplicationPolicy(player, targetCommunity)
        CommunityJoinPolicy.INVITE_ONLY -> joinUnderInviteOnlyPolicy(player, targetCommunity)
    }

    return 0
}

private fun joinUnderOpenPolicy(player: ServerPlayer, targetCommunity: Community): Int {
    val cost = if(targetCommunity.isManor()) PricingConfig.COMMUNITY_JOIN_COST_MANOR.value else PricingConfig.COMMUNITY_JOIN_COST_REALM.value
    
    val playerData = EconomyMod.data.getOrCreate(player)
    if (playerData.money < cost) {
        player.sendSystemMessage(
            Translator.tr("community.join.error.insufficient_assets", cost / 100.0, playerData.money / 100.0)
        )
        return 0
    }
    
    playerData.money -= cost
    
    targetCommunity.member[player.uuid] = MemberAccount(
        joinedTime = System.currentTimeMillis(),
        basicRoleType = MemberRoleType.MEMBER
    )
    
    player.sendSystemMessage(Translator.tr("community.join.success", targetCommunity.regionNumberId))
    player.sendSystemMessage(Translator.tr("community.join.payment.deducted", cost / 100.0))
    
    val communityName = targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
    val notification = Translator.tr("community.notification.member_joined", player.name.string, communityName) 
        ?: net.minecraft.network.chat.Component.literal("${player.name.string} has joined $communityName")
    notifyOfficials(targetCommunity, player.level().server, notification, player)
    
    com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.autoGrantDefaultPermissions(
        player.uuid, player, targetCommunity
    )
    
    com.imyvm.community.infra.CommunityDatabase.save()
    
    com.imyvm.community.application.event.checkAndPromoteRecruitingRealm(targetCommunity)
    
    return 1
}

private fun joinUnderApplicationPolicy(player: ServerPlayer, targetCommunity: Community): Int {
    if (targetCommunity.member.containsKey(player.uuid)) {
        player.sendSystemMessage(Translator.tr("community.join.error.already_applied", targetCommunity.regionNumberId))
        return 0
    }
    
    val cost = if(targetCommunity.isManor()) PricingConfig.COMMUNITY_JOIN_COST_MANOR.value else PricingConfig.COMMUNITY_JOIN_COST_REALM.value
    
    val playerData = EconomyMod.data.getOrCreate(player)
    if (playerData.money < cost) {
        player.sendSystemMessage(
            Translator.tr("community.join.error.insufficient_assets", cost / 100.0, playerData.money / 100.0)
        )
        return 0
    }
    
    playerData.money -= cost
    
    targetCommunity.member[player.uuid] = MemberAccount(
        joinedTime = System.currentTimeMillis(),
        basicRoleType = MemberRoleType.APPLICANT
    )
    
    player.sendSystemMessage(targetCommunity.getRegion()
        ?.let { Translator.tr("community.join.applied", it.name ,targetCommunity.regionNumberId) } ?: Component.empty())
    player.sendSystemMessage(Translator.tr("community.join.payment.deducted", cost / 100.0))
    
    val communityName = targetCommunity.getRegion()?.name ?: "Community #${targetCommunity.regionNumberId}"
    val notification = Translator.tr("community.notification.application_received", player.name.string, communityName)
        ?: net.minecraft.network.chat.Component.literal("${player.name.string} has applied to join $communityName")
    notifyOfficials(targetCommunity, player.level().server, notification, player)
    
    com.imyvm.community.infra.CommunityDatabase.save()
    return 1
}

private fun joinUnderInviteOnlyPolicy(player: ServerPlayer, targetCommunity: Community): Int {
    player.sendSystemMessage(Translator.tr("community.join.error.invite_only", targetCommunity.regionNumberId))
    return 0
}

fun validateInvitationSender(inviter: ServerPlayer, community: Community): Boolean {
    val inviterRole = community.getMemberRole(inviter.uuid)
    if (inviterRole == null || inviterRole == MemberRoleType.APPLICANT || inviterRole == MemberRoleType.REFUSED) {
        inviter.sendSystemMessage(Translator.tr("community.invite.error.no_permission"))
        return false
    }
    
    val cost = if (community.isManor()) PricingConfig.COMMUNITY_JOIN_COST_MANOR.value else PricingConfig.COMMUNITY_JOIN_COST_REALM.value
    if (community.getTotalAssets() < cost) {
        inviter.sendSystemMessage(Translator.tr("community.invite.error.insufficient_assets", (cost / 100.0).toString()))
        return false
    }
    
    return true
}

fun validateInvitationTarget(inviter: ServerPlayer, target: ServerPlayer, community: Community): Boolean {
    if (!checkPlayerMembershipJoin(target, community)) {
        inviter.sendSystemMessage(Translator.tr("community.invite.error.target_ineligible", target.name.string))
        return false
    }
    
    if (!checkMemberNumberManor(inviter, community)) {
        return false
    }
    
    return true
}

fun sendInvitation(inviter: ServerPlayer, target: ServerPlayer, community: Community) {
    if (!checkMemberNumberManor(inviter, community)) return
    
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val timeoutMinutes = CommunityConfig.INVITATION_RESPONSE_TIMEOUT_MINUTES.value
    
    community.member[target.uuid] = MemberAccount(
        joinedTime = System.currentTimeMillis(),
        basicRoleType = MemberRoleType.APPLICANT,
        isInvited = true
    )
    
    val invitationKey = getInvitationKey(target.uuid)
    
    addPendingOperation(
        regionId = invitationKey,
        type = PendingOperationType.INVITATION,
        expireMinutes = timeoutMinutes,
        inviterUUID = inviter.uuid,
        inviteeUUID = target.uuid
    )
    
    com.imyvm.community.infra.CommunityDatabase.save()
    
    val acceptText = net.minecraft.network.chat.Component.literal("[")
        .append(Translator.tr("community.invite.button.accept") ?: net.minecraft.network.chat.Component.literal("Accept"))
        .append(net.minecraft.network.chat.Component.literal("]"))
        .withStyle { style ->
            style.withColor(net.minecraft.ChatFormatting.GREEN)
                .withClickEvent(ClickEvent.RunCommand("/_commun accept_invitation ${community.regionNumberId}"))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.invite.button.accept.hover") 
                        ?: net.minecraft.network.chat.Component.literal("Click to accept invitation")
                ))
        }
    
    val rejectText = net.minecraft.network.chat.Component.literal("[")
        .append(Translator.tr("community.invite.button.reject") ?: net.minecraft.network.chat.Component.literal("Reject"))
        .append(net.minecraft.network.chat.Component.literal("]"))
        .withStyle { style ->
            style.withColor(net.minecraft.ChatFormatting.RED)
                .withClickEvent(ClickEvent.RunCommand("/_commun reject_invitation ${community.regionNumberId}"))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.invite.button.reject.hover") 
                        ?: net.minecraft.network.chat.Component.literal("Click to reject invitation")
                ))
        }
    
    val invitationMessage = Translator.tr(
        "community.invite.received",
        inviter.name.string,
        communityName,
        timeoutMinutes
    )
    
    if (invitationMessage != null) {
        target.sendSystemMessage(
            invitationMessage.copy()
                .append(net.minecraft.network.chat.Component.literal(" "))
                .append(acceptText)
                .append(net.minecraft.network.chat.Component.literal(" "))
                .append(rejectText)
        )
    }
    
    inviter.sendSystemMessage(Translator.tr("community.invite.sent", target.name.string, communityName))

    val notification = Translator.tr(
        "community.notification.invitation_sent",
        target.name.string,
        inviter.name.string,
        communityName
    ) ?: net.minecraft.network.chat.Component.literal("${target.name.string} was invited to $communityName by ${inviter.name.string}")
    notifyOfficials(community, inviter.level().server, notification, inviter)

    val targetNotification = Translator.tr(
        "community.notification.target.invitation_received",
        communityName,
        inviter.name.string
    ) ?: net.minecraft.network.chat.Component.literal("You have been invited to $communityName by ${inviter.name.string}")
    notifyTargetPlayer(inviter.level().server, target.uuid, targetNotification, community)
}

fun onAcceptInvitation(player: ServerPlayer, community: Community) {
    val invitationKey = getInvitationKey(player.uuid)
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[invitationKey]
    val memberAccount = community.member[player.uuid]
    
    if (pendingOp == null || pendingOp.type != PendingOperationType.INVITATION || 
        memberAccount == null || !memberAccount.isInvited || memberAccount.basicRoleType != MemberRoleType.APPLICANT) {
        player.sendSystemMessage(Translator.tr("community.invite.error.no_invitation"))
        return
    }
    
    if (pendingOp.expireAt <= System.currentTimeMillis()) {
        player.sendSystemMessage(Translator.tr("community.invite.error.expired"))
        WorldGeoCommunityAddon.pendingOperations.remove(invitationKey)
        community.member.remove(player.uuid)
        com.imyvm.community.infra.CommunityDatabase.save()
        return
    }
    
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    player.sendSystemMessage(Translator.tr("community.invite.accepted", communityName))

    WorldGeoCommunityAddon.pendingOperations.remove(invitationKey)

    val notification = Translator.tr(
        "community.notification.invitation_accepted",
        player.name.string,
        communityName
    ) ?: net.minecraft.network.chat.Component.literal("${player.name.string} has accepted invitation to $communityName (awaiting audit)")
    notifyOfficials(community, player.level().server, notification, player)

    val targetNotification = Translator.tr(
        "community.notification.target.invitation_accepted_awaiting_audit",
        communityName
    ) ?: net.minecraft.network.chat.Component.literal("You have accepted invitation to $communityName. Awaiting admin approval.")
    notifyTargetPlayer(player.level().server, player.uuid, targetNotification, community)
    
    com.imyvm.community.infra.CommunityDatabase.save()
}

fun onRejectInvitation(player: ServerPlayer, community: Community) {
    val invitationKey = getInvitationKey(player.uuid)
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[invitationKey]
    val memberAccount = community.member[player.uuid]
    
    if (pendingOp == null || pendingOp.type != PendingOperationType.INVITATION || 
        memberAccount == null || !memberAccount.isInvited || memberAccount.basicRoleType != MemberRoleType.APPLICANT) {
        player.sendSystemMessage(Translator.tr("community.invite.error.no_invitation"))
        return
    }
    
    WorldGeoCommunityAddon.pendingOperations.remove(invitationKey)
    community.member.remove(player.uuid)
    
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    player.sendSystemMessage(Translator.tr("community.invite.rejected", communityName))
    
    val inviterUUID = pendingOp.inviterUUID
    if (inviterUUID != null) {
        val inviterPlayer = player.level().server.playerList.getPlayer(inviterUUID)
        inviterPlayer?.sendSystemMessage(Translator.tr("community.invite.rejected.inviter", player.name.string, communityName))
    }
    
    val notification = Translator.tr(
        "community.notification.invitation_rejected_by_invitee",
        player.name.string,
        communityName
    ) ?: net.minecraft.network.chat.Component.literal("${player.name.string} has rejected invitation to $communityName")
    notifyOfficials(community, player.level().server, notification, player)
    
    com.imyvm.community.infra.CommunityDatabase.save()
}