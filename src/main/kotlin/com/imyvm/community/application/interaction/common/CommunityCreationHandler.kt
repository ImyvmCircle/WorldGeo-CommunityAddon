package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.interaction.common.helper.checkPlayerMembershipCreation
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.MemberAccount
import com.imyvm.community.domain.PendingOperation
import com.imyvm.community.domain.PendingOperationType
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.domain.community.CommunityStatus
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.economy.EconomyMod
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.network.ServerPlayerEntity

fun onCreateCommunityRequest(
    player: ServerPlayerEntity,
    communityType: String,
    communityName: String,
    shapeName: String
): Int {
    if (!checkPlayerMembershipCreation(player, communityType)) return 0

    val region = PlayerInteractionApi.createAndGetRegion(player, communityName, shapeName)
    if (region == null) {
        player.sendMessage(Translator.tr("community.create.region.error"))
        return 0
    }

    if (chargeFromApplicator(player, communityType) == 0) {
        PlayerInteractionApi.deleteRegion(player, region)
        return 0
    }

    val regionNumberId = region.numberID

    initialApplication(player, communityName, communityType, regionNumberId)
    handleApplicationBranches(player, communityType, regionNumberId)
    return 1
}

private fun chargeFromApplicator(player: ServerPlayerEntity, communityType: String): Int {
    val accountThreshold = when (communityType.lowercase()) {
        "manor" -> CommunityConfig.PRICE_MANOR.value
        "realm" -> CommunityConfig.PRICE_REALM.value
        else -> return 0
    }

    val playerAccount = EconomyMod.data.getOrCreate(player)
    return if (playerAccount.money >= accountThreshold) {
        playerAccount.addMoney(-accountThreshold)
        player.sendMessage(Translator.tr("community.create.money.checked", accountThreshold / 100.0))
        1
    } else {
        player.sendMessage(Translator.tr("community.create.money.error", accountThreshold / 100.0))
        0
    }
}

private fun initialApplication(player: ServerPlayerEntity, name: String, communityType: String, regionNumberId: Int) {
    val community = Community(
        regionNumberId = regionNumberId,
        member = hashMapOf(player.uuid to MemberAccount(
            joinedTime = System.currentTimeMillis(),
            basicRoleType = MemberRoleType.OWNER
        )),
        joinPolicy = CommunityJoinPolicy.OPEN,
        status = if (communityType.equals("manor", ignoreCase = true)) {
            CommunityStatus.PENDING_MANOR
        } else {
            CommunityStatus.RECRUITING_REALM
        }
    )

    CommunityDatabase.addCommunity(community)
    player.sendMessage(Translator.tr("community.create.request.initial.success", name, community.regionNumberId))
}

private fun handleApplicationBranches(player: ServerPlayerEntity, communityType: String, regionNumberId: Int) {
    if (communityType.equals("manor", ignoreCase = true)) {
        player.sendMessage(Translator.tr("community.create.request.sent"))
        WorldGeoCommunityAddon.pendingOperations[regionNumberId] = PendingOperation(
            expireAt = System.currentTimeMillis() + CommunityConfig.AUDITING_EXPIRE_HOURS.value * 3600 * 1000,
            type = PendingOperationType.AUDITING_COMMUNITY_APPLICATION
        )
    } else if (communityType.equals("realm", ignoreCase = true)) {
        player.sendMessage(Translator.tr("community.create.request.recruitment", CommunityConfig.MIN_NUMBER_MEMBER_REALM.value))
        WorldGeoCommunityAddon.pendingOperations[regionNumberId] = PendingOperation(
            expireAt = System.currentTimeMillis() + CommunityConfig.APPLICATION_EXPIRE_HOURS.value * 3600 * 1000,
            type = PendingOperationType.CREATE_COMMUNITY_RECRUITMENT
        )
    }
}
