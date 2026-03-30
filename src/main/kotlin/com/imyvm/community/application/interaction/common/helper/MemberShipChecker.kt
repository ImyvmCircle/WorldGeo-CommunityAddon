package com.imyvm.community.application.interaction.common.helper

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer

fun checkPlayerMembershipPreCreation(player: ServerPlayer): Boolean {
    if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) return true
    val joinedCommunities = CommunityDatabase.communities.filter {
        (it.status == CommunityStatus.ACTIVE_REALM || it.status == CommunityStatus.PENDING_REALM || it.status == CommunityStatus.RECRUITING_REALM
                || it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.PENDING_MANOR
                || it.status == CommunityStatus.REVOKED_MANOR || it.status == CommunityStatus.REVOKED_REALM)
                && it.member[player.uuid]?.basicRoleType.let { role ->
                    role != null && role != MemberRoleType.APPLICANT && role != MemberRoleType.REFUSED
                }
    }.toSet()
    if (joinedCommunities.size > 2) {
        player.sendSystemMessage(Translator.tr("community.create.error.maximum_communities"))
        return false
    }
    return true
}

fun checkPlayerMembershipCreation(player: ServerPlayer, communityType: String): Boolean {
    if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) return true
    val joinedCommunity = CommunityDatabase.communities.find {
        when (communityType.lowercase()) {
            "realm" -> (it.status == CommunityStatus.ACTIVE_REALM || it.status == CommunityStatus.PENDING_REALM
                    || it.status == CommunityStatus.RECRUITING_REALM || it.status == CommunityStatus.REVOKED_REALM)
            "manor" -> (it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.PENDING_MANOR
                    || it.status == CommunityStatus.REVOKED_MANOR)
            else -> false
        } && it.member[player.uuid]?.basicRoleType.let { role ->
            role != null && role != MemberRoleType.APPLICANT && role != MemberRoleType.REFUSED
        }
    }
    if (joinedCommunity != null) {
        player.sendSystemMessage(Translator.tr("community.create.error.already_in_community", communityType))
        return false
    }
    return true
}

fun checkPlayerMembershipJoin(player: ServerPlayer, community: Community): Boolean {
    if (isJoinedTarget(player, community)) return false
    if (isJoinedRealmTargetingRealm(player, community)) return false
    if (isJoinedManorTargetingManor(player, community)) return false
    return true
}

private fun isJoinedTarget(player: ServerPlayer, targetCommunity: Community): Boolean {
    if (targetCommunity.member.containsKey(player.uuid)){
        return if (targetCommunity.getMemberRole(player.uuid) == MemberRoleType.APPLICANT) {
            player.sendSystemMessage(Translator.tr("community.join.error.already_applied", targetCommunity.regionNumberId))
            true
        } else if (targetCommunity.getMemberRole(player.uuid) == MemberRoleType.REFUSED) {
            player.sendSystemMessage(Translator.tr("community.join.error.application_refused", targetCommunity.regionNumberId))
            true
        } else {
            player.sendSystemMessage(Translator.tr("community.join.error.already_member", targetCommunity.regionNumberId))
            true
        }
    }
    return false
}

private fun isJoinedRealmTargetingRealm(player: ServerPlayer, targetCommunity: Community): Boolean {
    if (targetCommunity.status == CommunityStatus.RECRUITING_REALM || targetCommunity.status == CommunityStatus.PENDING_REALM || targetCommunity.status == CommunityStatus.ACTIVE_REALM) {
        val joinedCommunity = CommunityDatabase.communities.find {
            (it.status == CommunityStatus.ACTIVE_REALM || it.status == CommunityStatus.PENDING_REALM
                    || it.status == CommunityStatus.RECRUITING_REALM || it.status == CommunityStatus.REVOKED_REALM)
                    && it.member[player.uuid]?.basicRoleType.let { role ->
                        role != null && role != MemberRoleType.APPLICANT && role != MemberRoleType.REFUSED
                    }
        }
        if (joinedCommunity != null) {
            player.sendSystemMessage(Translator.tr("community.join.error.already_in_realm", joinedCommunity.regionNumberId))
            return true
        }
    }
    return false
}

private fun isJoinedManorTargetingManor(player: ServerPlayer, targetCommunity: Community): Boolean {
    if (targetCommunity.status == CommunityStatus.ACTIVE_MANOR || targetCommunity.status == CommunityStatus.PENDING_MANOR) {
        val joinedCommunity = CommunityDatabase.communities.find {
            (it.status == CommunityStatus.ACTIVE_MANOR || it.status == CommunityStatus.PENDING_MANOR
                    || it.status == CommunityStatus.REVOKED_MANOR)
                    && it.member[player.uuid]?.basicRoleType.let { role ->
                        role != null && role != MemberRoleType.APPLICANT && role != MemberRoleType.REFUSED
                    }
        }
        if (joinedCommunity != null) {
            player.sendSystemMessage(Translator.tr("community.join.error.already_in_manor", joinedCommunity.regionNumberId))
            return true
        }
    }
    return false
}