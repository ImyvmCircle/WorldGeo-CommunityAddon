package com.imyvm.community.domain.model

import java.util.*

class PendingOperation(
    val expireAt: Long,
    val type: PendingOperationType,
    val inviterUUID: UUID? = null,
    val inviteeUUID: UUID? = null,
    val creationData: CreationConfirmationData? = null,
    val modificationData: ScopeModificationConfirmationData? = null,
    val teleportPointData: TeleportPointConfirmationData? = null
)

data class CreationConfirmationData(
    val communityName: String,
    val communityType: String,
    val shapeName: String,
    val regionNumberId: Int,
    val creatorUUID: UUID,
    val totalCost: Long
)

data class ScopeModificationConfirmationData(
    val regionNumberId: Int,
    val scopeName: String,
    val executorUUID: UUID,
    val cost: Long,
    val isScopeCreation: Boolean = false,
    val shapeName: String? = null
)

data class TeleportPointConfirmationData(
    val regionNumberId: Int,
    val scopeName: String,
    val executorUUID: UUID,
    val cost: Long,
    val reasonKey: String
)

enum class PendingOperationType(val value: Int) {
    CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT(0),
    DELETE_COMMUNITY(1),
    LEAVE_COMMUNITY(2),
    JOIN_COMMUNITY(3),
    CHANGE_ROLE(4),
    CHANGE_JOIN_POLICY(5),
    AUDITING_COMMUNITY_REQUEST(6),
    INVITATION(7),
    CREATE_COMMUNITY_CONFIRMATION(8),
    MODIFY_SCOPE_CONFIRMATION(9),
    TELEPORT_POINT_CONFIRMATION(10);
    
    companion object {
        fun fromValue(value: Int): PendingOperationType {
            return entries.find { it.value == value } 
                ?: throw IllegalArgumentException("Unknown PendingOperationType value: $value")
        }
    }
}
