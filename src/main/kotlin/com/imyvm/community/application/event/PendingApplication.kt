package com.imyvm.community.application.event

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.helper.refundNotCreated
import com.imyvm.community.application.interaction.common.deleteCreationRegion
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.pendingOperationKey
import com.imyvm.community.domain.model.pendingOperationSubjectId
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.MinecraftServer
import java.util.*

fun getPendingOperation(subjectId: Int?, type: PendingOperationType): PendingOperation? {
    if (subjectId == null) return null
    return WorldGeoCommunityAddon.pendingOperations[pendingOperationKey(subjectId, type)]
}

fun hasPendingOperation(subjectId: Int?, type: PendingOperationType): Boolean =
    getPendingOperation(subjectId, type) != null

fun getPendingOperationByKey(operationKey: Long): PendingOperation? {
    return WorldGeoCommunityAddon.pendingOperations[operationKey]
}

fun removePendingOperationByKey(operationKey: Long): PendingOperation? {
    return WorldGeoCommunityAddon.pendingOperations.remove(operationKey)
}


fun removePendingOperation(subjectId: Int?, type: PendingOperationType): PendingOperation? {
    if (subjectId == null) return null
    return WorldGeoCommunityAddon.pendingOperations.remove(pendingOperationKey(subjectId, type))
}

fun restorePendingOperation(subjectId: Int?, type: PendingOperationType, operation: PendingOperation) {
    if (subjectId == null) return
    WorldGeoCommunityAddon.pendingOperations[pendingOperationKey(subjectId, type)] = operation
}

fun removePendingOperationsForSubject(subjectId: Int?) {
    if (subjectId == null) return
    WorldGeoCommunityAddon.pendingOperations.keys
        .filter { pendingOperationSubjectId(it) == subjectId }
        .forEach { WorldGeoCommunityAddon.pendingOperations.remove(it) }
}

internal fun checkPendingOperations(server: MinecraftServer) {
    val now = System.currentTimeMillis()
    val iterator: MutableIterator<MutableMap.MutableEntry<Long, PendingOperation>> =
        WorldGeoCommunityAddon.pendingOperations.iterator()

    while (iterator.hasNext()) {
        val (key, operation) = iterator.next()
        if (operation.expireAt <= now) {
            handleExpiredOperation(pendingOperationSubjectId(key), operation, iterator, server)
        }
    }
}

private fun handleExpiredOperation(
    key: Int,
    operation: PendingOperation,
    iterator: MutableIterator<MutableMap.MutableEntry<Long, PendingOperation>>,
    server: MinecraftServer
) {
    when (operation.type) {
        PendingOperationType.INVITATION -> {
            handleExpiredInvitation(operation, server)
            iterator.remove()
        }
        PendingOperationType.CREATE_COMMUNITY_CONFIRMATION -> {
            if (handleExpiredCreationConfirmation(key, operation, server)) {
                iterator.remove()
            }
        }
        PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT -> {
            val community = CommunityDatabase.communities.find { it.regionNumberId == key }
            if (community != null) {
                promoteCommunityIfEligible(key, community)
                removeExpiredRealmRequest(key, community, server)
                removePendingOperation(key, iterator, server, operation.type)
                CommunityDatabase.save()
            } else {
                iterator.remove()
                CommunityDatabase.save()
            }
        }
        PendingOperationType.TELEPORT_POINT_CONFIRMATION -> {
            iterator.remove()
            operation.inviterUUID?.let { executorUUID ->
                server.playerList.getPlayer(executorUUID)
                    ?.sendSystemMessage(Translator.tr("community.teleport_point.confirmation.expired"))
            }
        }
        PendingOperationType.SETTING_CONFIRMATION -> {
            iterator.remove()
            operation.inviterUUID?.let { executorUUID ->
                server.playerList.getPlayer(executorUUID)
                    ?.sendSystemMessage(Translator.tr("community.setting.confirmation.expired"))
            }
        }
        PendingOperationType.DELETE_SCOPE_CONFIRMATION -> {
            iterator.remove()
            operation.modificationData?.executorUUID?.let { executorUUID ->
                server.playerList.getPlayer(executorUUID)
                    ?.sendSystemMessage(Translator.tr("community.scope_delete.confirmation.expired"))
            }
        }
        PendingOperationType.TRANSFER_SCOPE_CONFIRMATION -> {
            iterator.remove()
            operation.transferData?.executorUUID?.let { executorUUID ->
                server.playerList.getPlayer(executorUUID)
                    ?.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.expired"))
            }
        }
        PendingOperationType.TREASURY_GRANT_CONFIRMATION -> {
            iterator.remove()
            operation.treasuryGrantData?.executorUUID?.let { executorUUID ->
                server.playerList.getPlayer(executorUUID)
                    ?.sendSystemMessage(Translator.tr("community.treasury_grant.confirmation.expired"))
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
        
        val inviterPlayer = server.playerList.getPlayer(inviterUUID)
        val inviteePlayer = server.playerList.getPlayer(inviteeUUID)
        
        inviterPlayer?.sendSystemMessage(
            Translator.tr(
                "community.invite.expired.inviter",
                inviteePlayer?.name?.string ?: "Unknown",
                community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            )
        )
        
        inviteePlayer?.sendSystemMessage(
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
): Boolean {
    val creationData = operation.creationData ?: return true
    val creatorPlayer = server.playerList.getPlayer(creationData.creatorUUID)
    val cleaned = deleteCreationRegion(regionId, creatorPlayer)

    if (!cleaned) {
        WorldGeoCommunityAddon.logger.error("Expired creation confirmation for region $regionId could not be cleaned; pending retained")
        return false
    }

    creatorPlayer?.sendSystemMessage(
        Translator.tr(
            "community.create.confirmation.expired"
        )
    )

    WorldGeoCommunityAddon.logger.info("Expired creation confirmation for region $regionId by ${creationData.creatorUUID}")
    return true
}

private fun promoteCommunityIfEligible(regionId: Int, community: Community) {
    val ownerEntry = community.member.entries.find { community.getMemberRole(it.key) == MemberRoleType.OWNER }
    if (ownerEntry != null &&
        community.member.count { community.getMemberRole(it.key) != MemberRoleType.APPLICANT && community.getMemberRole(it.key) != MemberRoleType.REFUSED } >= CommunityConfig.MIN_NUMBER_MEMBER_REALM.value &&
        community.status == CommunityStatus.RECRUITING_REALM
    ) {
        removePendingOperation(regionId, PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT)
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
    val ownerPlayer = server.playerList.getPlayer(ownerEntry.key)

    if (community.status == CommunityStatus.RECRUITING_REALM) {
        community.status = CommunityStatus.REVOKED_REALM
        refundNotCreated(ownerPlayer, community, ownerEntry.key)
        WorldGeoCommunityAddon.logger.info("Community $regionId recruitment expired and revoked.")
    }
}

private fun removePendingOperation(
    regionId: Int,
    iterator: MutableIterator<MutableMap.MutableEntry<Long, PendingOperation>>,
    server: MinecraftServer,
    operationType: PendingOperationType
) {
    iterator.remove()
    WorldGeoCommunityAddon.logger.info("Removed expired pending operation for community $regionId")
    val community = CommunityDatabase.communities.find { it.regionNumberId == regionId } ?: return
    val ownerUuid = community.member.entries.find { community.getMemberRole(it.key) == MemberRoleType.OWNER }?.key ?: return
    server.playerList.getPlayer(ownerUuid)
        ?.sendSystemMessage(Translator.tr("pending.expired", operationType), false)
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
    val ownerPlayer = server?.playerList?.getPlayer(ownerUUID)
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
    teleportPointData: com.imyvm.community.domain.model.TeleportPointConfirmationData? = null,
    settingData: com.imyvm.community.domain.model.SettingConfirmationData? = null,
    renameData: com.imyvm.community.domain.model.RenameConfirmationData? = null,
    transferData: com.imyvm.community.domain.model.ScopeTransferConfirmationData? = null,
    treasuryGrantData: com.imyvm.community.domain.model.TreasuryGrantConfirmationData? = null
) {
    val now = System.currentTimeMillis()
    val expireTime = when {
        expireHours != null -> now + expireHours * 3600 * 1000L
        expireMinutes != null -> now + expireMinutes * 60 * 1000L
        else -> throw IllegalArgumentException("Must specify either expireHours or expireMinutes")
    }
    val key = pendingOperationKey(regionId, type)
    addPendingOperationByKey(
        operationKey = key,
        type = type,
        expireHours = expireHours,
        expireMinutes = expireMinutes,
        inviterUUID = inviterUUID,
        inviteeUUID = inviteeUUID,
        creationData = creationData,
        modificationData = modificationData,
        teleportPointData = teleportPointData,
        settingData = settingData,
        renameData = renameData,
        transferData = transferData,
        treasuryGrantData = treasuryGrantData
    )
    WorldGeoCommunityAddon.logger.info("Added pending operation: type=$type, regionId=$regionId, expireAt=$expireTime")
}

fun addPendingOperationByKey(
    operationKey: Long,
    type: PendingOperationType,
    expireHours: Int? = null,
    expireMinutes: Int? = null,
    inviterUUID: UUID? = null,
    inviteeUUID: UUID? = null,
    creationData: com.imyvm.community.domain.model.CreationConfirmationData? = null,
    modificationData: com.imyvm.community.domain.model.ScopeModificationConfirmationData? = null,
    teleportPointData: com.imyvm.community.domain.model.TeleportPointConfirmationData? = null,
    settingData: com.imyvm.community.domain.model.SettingConfirmationData? = null,
    renameData: com.imyvm.community.domain.model.RenameConfirmationData? = null,
    transferData: com.imyvm.community.domain.model.ScopeTransferConfirmationData? = null,
    treasuryGrantData: com.imyvm.community.domain.model.TreasuryGrantConfirmationData? = null
) {
    val now = System.currentTimeMillis()
    val expireTime = when {
        expireHours != null -> now + expireHours * 3600 * 1000L
        expireMinutes != null -> now + expireMinutes * 60 * 1000L
        else -> throw IllegalArgumentException("Must specify either expireHours or expireMinutes")
    }
    val existing = WorldGeoCommunityAddon.pendingOperations[operationKey]
    if (existing != null && existing.expireAt > now) {
        throw IllegalStateException("Pending operation already exists for operationKey=$operationKey, type=${existing.type}")
    }

    WorldGeoCommunityAddon.pendingOperations[operationKey] = PendingOperation(
        expireAt = expireTime,
        type = type,
        inviterUUID = inviterUUID,
        inviteeUUID = inviteeUUID,
        creationData = creationData,
        modificationData = modificationData,
        teleportPointData = teleportPointData,
        settingData = settingData,
        renameData = renameData,
        transferData = transferData,
        treasuryGrantData = treasuryGrantData
    )
    try {
        if (WorldGeoCommunityAddon.server != null) CommunityDatabase.save()
    } catch (e: Exception) {
        if (existing == null) {
            WorldGeoCommunityAddon.pendingOperations.remove(operationKey)
        } else {
            WorldGeoCommunityAddon.pendingOperations[operationKey] = existing
        }
        throw e
    }
    WorldGeoCommunityAddon.logger.info("Added pending operation: type=$type, operationKey=$operationKey, expireAt=$expireTime")
}
