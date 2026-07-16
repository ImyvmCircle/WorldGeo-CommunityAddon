package com.imyvm.community.domain.model

import java.util.*

class PendingOperation(
    val expireAt: Long,
    val type: PendingOperationType,
    val inviterUUID: UUID? = null,
    val inviteeUUID: UUID? = null,
    val creationData: CreationConfirmationData? = null,
    val modificationData: ScopeModificationConfirmationData? = null,
    val teleportPointData: TeleportPointConfirmationData? = null,
    val settingData: SettingConfirmationData? = null,
    val renameData: RenameConfirmationData? = null,
    val transferData: ScopeTransferConfirmationData? = null,
    val treasuryGrantData: TreasuryGrantConfirmationData? = null
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
    val shapeName: String? = null,
    val softLimitSurcharge: Long = 0L,
    val isScopeDeletion: Boolean = false
)

data class TeleportPointConfirmationData(
    val regionNumberId: Int,
    val scopeName: String,
    val executorUUID: UUID,
    val cost: Long,
    val reasonKey: String
)

data class SettingConfirmationData(
    val regionNumberId: Int,
    val scopeName: String?,
    val executorUUID: UUID,
    val permissionKeyStr: String,
    val newValue: Boolean,
    val targetPlayerUUID: UUID?,
    val cost: Long,
    val isRuleSetting: Boolean = false
)

data class RenameConfirmationData(
    val regionNumberId: Int,
    val nameKey: String,
    val newName: String,
    val executorUUID: UUID,
    val cost: Long
)

data class ScopeTransferConfirmationData(
    val sourceRegionNumberId: Int,
    val scopeName: String,
    val executorUUID: UUID,
    val targetRegionNumberId: Int
)

data class TreasuryGrantConfirmationData(
    val sourceRegionNumberId: Int,
    val targetRegionNumberId: Int,
    val executorUUID: UUID,
    val amount: Long
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
    TELEPORT_POINT_CONFIRMATION(10),
    SETTING_CONFIRMATION(11),
    RENAME_CONFIRMATION(12),
    DELETE_SCOPE_CONFIRMATION(13),
    TRANSFER_SCOPE_CONFIRMATION(14),
    TREASURY_GRANT_CONFIRMATION(15);
    
    companion object {
        fun fromValue(value: Int): PendingOperationType {
            return entries.find { it.value == value } 
                ?: throw IllegalArgumentException("Unknown PendingOperationType value: $value")
        }
    }
}


fun pendingOperationKey(subjectId: Int, type: PendingOperationType): Long =
    (type.value.toLong() shl 32) or (subjectId.toLong() and 0xffffffffL)

fun pendingOperationSubjectId(key: Long): Int = (key and 0xffffffffL).toInt()


class PendingOperationStore(
    private val backing: MutableMap<Long, PendingOperation> = mutableMapOf()
) : MutableMap<Long, PendingOperation> by backing {
    operator fun get(subjectId: Int?): PendingOperation? {
        if (subjectId == null) return null
        return backing.entries.firstOrNull { pendingOperationSubjectId(it.key) == subjectId }?.value
    }

    fun remove(subjectId: Int?): PendingOperation? {
        if (subjectId == null) return null
        val key = backing.keys.firstOrNull { pendingOperationSubjectId(it) == subjectId } ?: return null
        return backing.remove(key)
    }

    fun containsKey(subjectId: Int?): Boolean {
        if (subjectId == null) return false
        return backing.keys.any { pendingOperationSubjectId(it) == subjectId }
    }
}
