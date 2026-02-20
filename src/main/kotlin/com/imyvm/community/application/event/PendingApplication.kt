package com.imyvm.community.application.event

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.helper.refundNotCreated
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.MinecraftServer
import java.util.*

internal fun checkPendingOperations(server: MinecraftServer) {
    val now = System.currentTimeMillis()
    val iterator: MutableIterator<MutableMap.MutableEntry<Int, PendingOperation>> =
        WorldGeoCommunityAddon.pendingOperations.iterator()

    while (iterator.hasNext()) {
        val (key, operation) = iterator.next()
        if (operation.expireAt <= now) {
            handleExpiredOperation(key, operation, iterator, server)
        }
    }
}

private fun handleExpiredOperation(
    key: Int,
    operation: PendingOperation,
    iterator: MutableIterator<MutableMap.MutableEntry<Int, PendingOperation>>,
    server: MinecraftServer
) {
    when (operation.type) {
        PendingOperationType.INVITATION -> {
            handleExpiredInvitation(operation, server)
            iterator.remove()
        }
        PendingOperationType.CREATE_COMMUNITY_CONFIRMATION -> {
            handleExpiredCreationConfirmation(key, operation, server)
            iterator.remove()
        }
        PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT -> {
            val community = CommunityDatabase.communities.find { it.regionNumberId == key }
            if (community != null) {
                promoteCommunityIfEligible(key, community)
                removeExpiredRealmRequest(key, community, server)
            } else {
                iterator.remove()
            }
            removePendingOperation(key, iterator, server, operation.type)
        }
        PendingOperationType.TELEPORT_POINT_CONFIRMATION -> {
            iterator.remove()
            operation.inviterUUID?.let { executorUUID ->
                server.playerManager.getPlayer(executorUUID)
                    ?.sendMessage(Translator.tr("community.teleport_point.confirmation.expired"))
            }
        }
        else -> {
            WorldGeoCommunityAddon.logger.info(
                "Unhandled expired operation type: ${operation.type} for key $key"
            )
            iterator.remove()
        }
    }
}

private fun handleExpiredInvitation(
    operation: PendingOperation,
    server: MinecraftServer
) {
    val inviteeUUID = operation.inviteeUUID ?: return
    val inviterUUID = operation.inviterUUID ?: return
    
    val community = CommunityDatabase.communities.find { 
        it.member[inviteeUUID]?.isInvited == true 
    }
    
    if (community != null) {
        community.member.remove(inviteeUUID)
        CommunityDatabase.save()
        
        val inviterPlayer = server.playerManager?.getPlayer(inviterUUID)
        val inviteePlayer = server.playerManager?.getPlayer(inviteeUUID)
        
        inviterPlayer?.sendMessage(
            Translator.tr(
                "community.invite.expired.inviter",
                inviteePlayer?.name?.string ?: "Unknown",
                community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            )
        )
        
        inviteePlayer?.sendMessage(
            Translator.tr(
                "community.invite.expired.invitee",
                community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            )
        )
    }
    
    WorldGeoCommunityAddon.logger.info("Expired invitation for invitee $inviteeUUID")
}

private fun handleExpiredCreationConfirmation(
    regionId: Int,
    operation: PendingOperation,
    server: MinecraftServer
) {
    val creationData = operation.creationData ?: return
    val creatorPlayer = server.playerManager?.getPlayer(creationData.creatorUUID)

    val region = com.imyvm.iwg.inter.api.RegionDataApi.getRegion(regionId)
    if (region != null && creatorPlayer != null) {
        try {
            com.imyvm.iwg.inter.api.PlayerInteractionApi.deleteRegion(creatorPlayer, region)
            WorldGeoCommunityAddon.logger.info("Deleted region $regionId for expired creation confirmation")
        } catch (e: Exception) {
            WorldGeoCommunityAddon.logger.error("Failed to delete region $regionId: ${e.message}")
        }
    }

    creatorPlayer?.sendMessage(
        Translator.tr(
            "community.create.confirmation.expired"
        )
    )
    
    WorldGeoCommunityAddon.logger.info("Expired creation confirmation for region $regionId by ${creationData.creatorUUID}")
}

private fun promoteCommunityIfEligible(regionId: Int, community: Community) {
    val ownerEntry = community.member.entries.find { community.getMemberRole(it.key) == MemberRoleType.OWNER }
    if (ownerEntry != null &&
        community.member.count { community.getMemberRole(it.key) != MemberRoleType.APPLICANT && community.getMemberRole(it.key) != MemberRoleType.REFUSED } >= CommunityConfig.MIN_NUMBER_MEMBER_REALM.value &&
        community.status == CommunityStatus.RECRUITING_REALM
    ) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionId)
        addAuditingRequestRealm(regionId, community, ownerEntry.key)
        WorldGeoCommunityAddon.logger.info("Community $regionId promoted to auditing stage.")
    }
}

fun checkAndPromoteRecruitingRealm(community: Community) {
    if (community.status == CommunityStatus.RECRUITING_REALM && community.regionNumberId != null) {
        val formalMemberCount = community.member.count { 
            community.getMemberRole(it.key) != MemberRoleType.APPLICANT && 
            community.getMemberRole(it.key) != MemberRoleType.REFUSED 
        }
        
        if (formalMemberCount >= CommunityConfig.MIN_NUMBER_MEMBER_REALM.value) {
            promoteCommunityIfEligible(community.regionNumberId, community)
        }
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

private fun addAuditingRequestRealm(regionId: Int, community: Community, ownerUUID: UUID) {
    addPendingOperation(
        regionId = regionId,
        type = PendingOperationType.AUDITING_COMMUNITY_REQUEST,
        expireHours = CommunityConfig.AUDITING_EXPIRE_HOURS.value
    )
    community.status = CommunityStatus.PENDING_REALM
    CommunityDatabase.save()
    
    val server = WorldGeoCommunityAddon.server
    val ownerPlayer = server?.playerManager?.getPlayer(ownerUUID)
    if (ownerPlayer != null) {
        com.imyvm.community.application.interaction.common.notifyOPsAndOwnerAboutCreationRequest(ownerPlayer, regionId)
    }
    
    WorldGeoCommunityAddon.logger.info("Community request $regionId moved to auditing stage.")
}

fun addPendingOperation(
    regionId: Int,
    type: PendingOperationType,
    expireHours: Int? = null,
    expireMinutes: Int? = null,
    inviterUUID: UUID? = null,
    inviteeUUID: UUID? = null,
    creationData: com.imyvm.community.domain.model.CreationConfirmationData? = null,
    modificationData: com.imyvm.community.domain.model.ScopeModificationConfirmationData? = null,
    teleportPointData: com.imyvm.community.domain.model.TeleportPointConfirmationData? = null
) {
    val expireTime = when {
        expireHours != null -> System.currentTimeMillis() + expireHours * 3600 * 1000L
        expireMinutes != null -> System.currentTimeMillis() + expireMinutes * 60 * 1000L
        else -> throw IllegalArgumentException("Must specify either expireHours or expireMinutes")
    }
    
    WorldGeoCommunityAddon.pendingOperations[regionId] = PendingOperation(
        expireAt = expireTime,
        type = type,
        inviterUUID = inviterUUID,
        inviteeUUID = inviteeUUID,
        creationData = creationData,
        modificationData = modificationData,
        teleportPointData = teleportPointData
    )
    WorldGeoCommunityAddon.logger.info("Added pending operation: type=$type, regionId=$regionId, expireAt=$expireTime")
}
