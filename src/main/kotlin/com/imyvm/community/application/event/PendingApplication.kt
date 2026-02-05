package com.imyvm.community.application.event

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.helper.refundNotCreated
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.PendingOperation
import com.imyvm.community.domain.PendingOperationType
import com.imyvm.community.domain.community.CommunityStatus
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.MinecraftServer

internal fun checkPendingOperations(server: MinecraftServer) {
    val now = System.currentTimeMillis()
    val iterator: MutableIterator<MutableMap.MutableEntry<Int, PendingOperation>> =
        WorldGeoCommunityAddon.pendingOperations.iterator()

    while (iterator.hasNext()) {
        val (regionId, operation) = iterator.next()
        if (operation.expireAt <= now) {
            handleExpiredOperation(regionId, operation, iterator, server)
        }
    }
}

private fun handleExpiredOperation(
    regionId: Int,
    operation: PendingOperation,
    iterator: MutableIterator<MutableMap.MutableEntry<Int, PendingOperation>>,
    server: MinecraftServer
) {
    val community = CommunityDatabase.communities.find { it.regionNumberId == regionId }
    if (community == null) {
        iterator.remove()
        return
    }

    when (operation.type) {
        PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT -> {
            promoteCommunityIfEligible(regionId, community)
            removeExpiredRealmRequest(regionId, community, server)
        }

        else -> {
            WorldGeoCommunityAddon.logger.info(
                "Unhandled expired operation type: ${operation.type} for community $regionId"
            )
        }
    }

    removePendingOperation(regionId, iterator, server, operation.type)
}

private fun promoteCommunityIfEligible(regionId: Int, community: Community) {
    val ownerEntry = community.member.entries.find { community.getMemberRole(it.key) == MemberRoleType.OWNER }
    if (ownerEntry != null &&
        community.member.count { community.getMemberRole(it.key) != MemberRoleType.APPLICANT } >= CommunityConfig.MIN_NUMBER_MEMBER_REALM.value &&
        community.status == CommunityStatus.PENDING_REALM
    ) {
        addAuditingRequestRealm(regionId, community)
        WorldGeoCommunityAddon.logger.info("Community $regionId promoted to auditing stage.")
    }
}

private fun removeExpiredRealmRequest(regionId: Int, community: Community, server: MinecraftServer) {
    val ownerEntry = community.member.entries.find { community.getMemberRole(it.key) == MemberRoleType.OWNER } ?: return
    val ownerPlayer = server.playerManager?.getPlayer(ownerEntry.key) ?: return

    if (community.status == CommunityStatus.RECRUITING_REALM) {
        community.status = CommunityStatus.REVOKED_REALM
        refundNotCreated(ownerPlayer, community)
        WorldGeoCommunityAddon.logger.info("Community $regionId recruitment expired and revoked.")
    }
}

private fun removePendingOperation(
    regionId: Int,
    iterator: MutableIterator<MutableMap.MutableEntry<Int, PendingOperation>>,
    server: MinecraftServer,
    operationType: PendingOperationType
) {
    iterator.remove()
    WorldGeoCommunityAddon.logger.info("Removed expired pending operation for community $regionId")
    val community = CommunityDatabase.communities.find { it.regionNumberId == regionId } ?: return
    val ownerUuid = community.member.entries.find { community.getMemberRole(it.key) == MemberRoleType.OWNER }?.key ?: return
    server.playerManager.getPlayer(ownerUuid)
        ?.sendMessage(Translator.tr("pending.expired", operationType), false)
}

private fun addAuditingRequestRealm(regionId: Int, community: Community) {
    WorldGeoCommunityAddon.pendingOperations[regionId] = PendingOperation(
        expireAt = System.currentTimeMillis() + CommunityConfig.AUDITING_EXPIRE_HOURS.value * 3600 * 1000,
        type = PendingOperationType.AUDITING_COMMUNITY_REQUEST
    )
    community.status = CommunityStatus.PENDING_REALM
    WorldGeoCommunityAddon.logger.info("Community request $regionId moved to auditing stage.")
}
