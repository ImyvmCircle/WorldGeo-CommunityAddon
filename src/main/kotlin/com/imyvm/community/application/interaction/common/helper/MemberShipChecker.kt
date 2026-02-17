package com.imyvm.community.application.interaction.common.helper

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun checkPlayerMembershipPreCreation(player: ServerPlayerEntity): Boolean {
    if (player.hasPermissionLevel(2)) return true
    val joinedCommunities = CommunityDatabase.communities.filter {
        (it.status == CommunityStatus.ACTIVE_REALM || it.status == CommunityStatus.PENDING_REALM || it.status == CommunityStatus.RECRUITING_REALM
                || it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.PENDING_MANOR)
                && it.member.containsKey(player.uuid)
    }.toSet()
    if (joinedCommunities.size > 2) {
        player.sendMessage(Translator.tr("community.create.error.maximum_communities"))
        return false
    }
    return true
}

fun checkPlayerMembershipCreation(player: ServerPlayerEntity, communityType: String): Boolean {
    if (player.hasPermissionLevel(2)) return true
    val joinedCommunity = CommunityDatabase.communities.find {
        when (communityType.lowercase()) {
            "realm" -> (it.status == CommunityStatus.ACTIVE_REALM || it.status == CommunityStatus.PENDING_REALM || it.status == CommunityStatus.RECRUITING_REALM)
            "manor" -> (it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.PENDING_MANOR)
            else -> false
        } && it.member.containsKey(player.uuid)
    }
    if (joinedCommunity != null) {
        player.sendMessage(Translator.tr("community.create.error.already_in_community", communityType))
        return false
    }
    return true
}

fun checkPlayerMembershipJoin(player: ServerPlayerEntity, community: Community): Boolean {
    if (isJoinedTarget(player, community)) return false
    if (isJoinedRealmTargetingRealm(player, community)) return false
    if (isJoinedManorTargetingManor(player, community)) return false
    return true
}

private fun isJoinedTarget(player: ServerPlayerEntity, targetCommunity: Community): Boolean {
    if (targetCommunity.member.containsKey(player.uuid)){
        return if (targetCommunity.getMemberRole(player.uuid) == MemberRoleType.APPLICANT) {
            player.sendMessage(Translator.tr("community.join.error.already_applied", targetCommunity.regionNumberId))
            true
        } else if (targetCommunity.getMemberRole(player.uuid) == MemberRoleType.REFUSED) {
            player.sendMessage(Translator.tr("community.join.error.application_refused", targetCommunity.regionNumberId))
            true
        } else {
            player.sendMessage(Translator.tr("community.join.error.already_member", targetCommunity.regionNumberId))
            true
        }
    }
    return false
}

private fun isJoinedRealmTargetingRealm(player: ServerPlayerEntity, targetCommunity: Community): Boolean {
    if (targetCommunity.status == CommunityStatus.RECRUITING_REALM || targetCommunity.status == CommunityStatus.PENDING_REALM || targetCommunity.status == CommunityStatus.ACTIVE_REALM) {
        val joinedCommunity = CommunityDatabase.communities.find {
            (it.status == CommunityStatus.ACTIVE_REALM || it.status == CommunityStatus.PENDING_REALM || it.status == CommunityStatus.RECRUITING_REALM)
                    && (it.member.containsKey(player.uuid) && it.member[ player.uuid ]?.basicRoleType != MemberRoleType.REFUSED)
        }
        if (joinedCommunity != null) {
            player.sendMessage(Translator.tr("community.join.error.already_in_realm", joinedCommunity.regionNumberId))
            return true
        }
    }
    return false
}

private fun isJoinedManorTargetingManor(player: ServerPlayerEntity, targetCommunity: Community): Boolean {
    if (targetCommunity.status == CommunityStatus.ACTIVE_MANOR || targetCommunity.status == CommunityStatus.PENDING_MANOR) {
        val joinedCommunity = CommunityDatabase.communities.find {
            (it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.PENDING_MANOR)
                    && (it.member.containsKey(player.uuid) && it.member[ player.uuid ]?.basicRoleType != MemberRoleType.REFUSED)
        }
        if (joinedCommunity != null) {
            player.sendMessage(Translator.tr("community.join.error.already_in_manor", joinedCommunity.regionNumberId))
            return true
        }
    }
    return false
}