package com.imyvm.community.infra

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.CreationConfirmationData
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.pendingOperationKey
import com.imyvm.community.domain.model.community.Announcement
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityMessage
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.community.MessageType
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.AdminPrivileges
import com.imyvm.community.util.TextParser
import net.minecraft.network.chat.Component
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.UUID

internal data class DecodedLegacyCommunityDatabase(
    val communities: MutableList<Community>,
    val pendingOperations: Map<Long, PendingOperation>
)

internal object LegacyCommunityDatabaseDecoder {
    fun decode(payload: ByteArray): DecodedLegacyCommunityDatabase {
        val candidates = LegacyLayout.entries.mapNotNull { layout ->
            runCatching { decode(payload, layout) }.getOrNull()
        }
        require(candidates.size == 1) {
            "Legacy community database matched ${candidates.size} published layouts"
        }
        return candidates.single()
    }

    private fun decode(payload: ByteArray, layout: LegacyLayout): DecodedLegacyCommunityDatabase =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val communityCount = readCount(input, "community", MAX_COMMUNITIES)
            val communities = MutableList(communityCount) {
                if (layout == LegacyLayout.ROLE_ONLY_0_2_3) readRoleOnlyCommunity(input)
                else readAccountCommunity(input, layout.versionedTurnover)
            }
            val pending = when (layout) {
                LegacyLayout.ROLE_ONLY_0_2_3 -> emptyMap()
                LegacyLayout.ACCOUNT_RAW_0_3_1 -> readPendingOperations(input)
                LegacyLayout.ACCOUNT_RAW_1_0_0 -> {
                    val result = readPendingOperations(input)
                    readCooldowns(input, communities)
                    result
                }
                LegacyLayout.ACCOUNT_VERSIONED_1_0_1 -> {
                    val result = readPendingOperations(input)
                    readCooldowns(input, communities)
                    readLikes(input, communities)
                    readIncome(input, communities)
                    result
                }
            }
            require(input.available() == 0) { "Unread legacy community database bytes" }
            DecodedLegacyCommunityDatabase(communities, pending)
        }

    private fun readRoleOnlyCommunity(input: DataInputStream): Community {
        val regionId = readRegionId(input)
        input.readLong()
        val memberCount = readCount(input, "member")
        val members = HashMap<UUID, MemberAccount>(memberCount)
        repeat(memberCount) {
            val uuid = readUuid(input)
            val role = readRole(input)
            require(members.put(uuid, MemberAccount(0L, role)) == null) { "Duplicate member UUID" }
        }
        return Community(
            regionNumberId = regionId,
            member = members,
            joinPolicy = CommunityJoinPolicy.fromValue(input.readInt()),
            status = CommunityStatus.fromValue(input.readInt())
        )
    }

    private fun readAccountCommunity(input: DataInputStream, versionedTurnover: Boolean): Community {
        val regionId = readRegionId(input)
        val memberCount = readCount(input, "member")
        val members = HashMap<UUID, MemberAccount>(memberCount)
        repeat(memberCount) {
            val uuid = readUuid(input)
            val joinedTime = input.readLong()
            val role = readRole(input)
            val mailCount = readCount(input, "member mail")
            val mail = ArrayList<Component>(mailCount)
            repeat(mailCount) { mail.add(Component.literal(readString(input))) }
            val turnover = readTurnoverList(input, versionedTurnover)
            val invited = input.readBoolean()
            val chatHistory = input.readBoolean()
            val privileges = if (input.readBoolean()) {
                val count = readCount(input, "admin privilege", AdminPrivilege.entries.size)
                val enabled = mutableSetOf<AdminPrivilege>()
                repeat(count) {
                    val ordinal = input.readInt()
                    require(ordinal in AdminPrivilege.entries.indices) { "Invalid admin privilege" }
                    require(enabled.add(AdminPrivilege.entries[ordinal])) { "Duplicate admin privilege" }
                }
                AdminPrivileges(enabled)
            } else null
            require(members.put(uuid, MemberAccount(
                joinedTime, role, privileges, mail, turnover, invited, chatHistory
            )) == null) { "Duplicate member UUID" }
        }
        return Community(
            regionNumberId = regionId,
            member = members,
            joinPolicy = CommunityJoinPolicy.fromValue(input.readInt()),
            status = CommunityStatus.fromValue(input.readInt()),
            announcements = readAnnouncements(input),
            expenditures = readTurnoverList(input, versionedTurnover),
            messages = readMessages(input),
            creationCost = input.readLong()
        )
    }

    private fun readAnnouncements(input: DataInputStream): MutableList<Announcement> {
        val result = mutableListOf<Announcement>()
        repeat(readCount(input, "announcement")) {
            val id = readUuid(input)
            val content = TextParser.parse(readString(input))
            val author = readUuid(input)
            val timestamp = input.readLong()
            val deleted = input.readBoolean()
            val readBy = mutableSetOf<UUID>()
            repeat(readCount(input, "announcement reader")) {
                require(readBy.add(readUuid(input))) { "Duplicate announcement reader" }
            }
            result.add(Announcement(id, content, author, timestamp, deleted, readBy))
        }
        return result
    }

    private fun readMessages(input: DataInputStream): MutableList<CommunityMessage> {
        val result = mutableListOf<CommunityMessage>()
        repeat(readCount(input, "message")) {
            val id = readUuid(input)
            val typeValue = input.readInt()
            val type = MessageType.entries.singleOrNull { it.value == typeValue }
                ?: error("Invalid legacy message type")
            val content = Component.literal(readString(input))
            val sender = readUuid(input)
            val timestamp = input.readLong()
            val deleted = input.readBoolean()
            val readBy = mutableSetOf<UUID>()
            repeat(readCount(input, "message reader")) {
                require(readBy.add(readUuid(input))) { "Duplicate message reader" }
            }
            val recipient = if (input.readBoolean()) readUuid(input) else null
            result.add(CommunityMessage(id, type, content, sender, timestamp, deleted, readBy, recipient))
        }
        return result
    }

    private fun readPendingOperations(input: DataInputStream): Map<Long, PendingOperation> {
        val result = mutableMapOf<Long, PendingOperation>()
        repeat(readCount(input, "pending operation")) {
            val subjectId = input.readInt()
            val expireAt = input.readLong()
            val type = PendingOperationType.fromValue(input.readInt())
            val inviter = if (input.readBoolean()) readUuid(input) else null
            val invitee = if (input.readBoolean()) readUuid(input) else null
            val creation = if (input.readBoolean()) CreationConfirmationData(
                readString(input), readString(input), readString(input), input.readInt(), readUuid(input), input.readLong()
            ) else null
            val key = pendingOperationKey(subjectId, type)
            require(result.put(key, PendingOperation(expireAt, type, inviter, invitee, creation)) == null) {
                "Duplicate pending operation"
            }
        }
        return result
    }

    private fun readCooldowns(input: DataInputStream, communities: List<Community>) {
        repeat(readCount(input, "cooldown community")) {
            val community = requireCommunity(communities, input.readInt())
            val values = HashMap<String, Long>()
            repeat(readCount(input, "name cooldown")) {
                require(values.put(readString(input), input.readLong()) == null) { "Duplicate cooldown key" }
            }
            community.nameChangeCooldowns = values
        }
    }

    private fun readLikes(input: DataInputStream, communities: List<Community>) {
        repeat(readCount(input, "likes community")) {
            val community = requireCommunity(communities, input.readInt())
            community.likeCount = readCount(input, "like")
            val values = HashMap<UUID, Long>()
            repeat(readCount(input, "likes player")) {
                require(values.put(readUuid(input), input.readLong()) == null) { "Duplicate liker UUID" }
            }
            community.lastLikedBy = values
        }
    }

    private fun readIncome(input: DataInputStream, communities: List<Community>) {
        repeat(readCount(input, "income community")) {
            requireCommunity(communities, input.readInt()).communityIncome = readTurnoverList(input, true)
        }
    }

    private fun readTurnoverList(input: DataInputStream, versioned: Boolean): ArrayList<Turnover> {
        val first = input.readInt()
        val size = if (versioned) {
            require(first == TURNOVER_VERSION_MARKER) { "Missing turnover version marker" }
            readCount(input, "turnover")
        } else {
            requireCount(first, "turnover")
        }
        return ArrayList<Turnover>(size).also { result ->
            repeat(size) {
                val amount = input.readLong()
                val timestamp = input.readLong()
                if (versioned) {
                    val sourceValue = input.readInt()
                    val source = TurnoverSource.entries.singleOrNull { entry -> entry.value == sourceValue }
                        ?: error("Invalid turnover source")
                    val descriptionKey = readString(input).ifEmpty { null }
                    val args = List(readCount(input, "turnover description argument")) { readString(input) }
                    result.add(Turnover(amount, timestamp, source, descriptionKey, args))
                } else {
                    result.add(Turnover(amount, timestamp))
                }
            }
        }
    }

    private fun readRegionId(input: DataInputStream): Int? = if (input.readBoolean()) {
        input.readInt().also { require(it > 0) { "Invalid legacy region ID" } }
    } else null

    private fun readRole(input: DataInputStream): MemberRoleType = MemberRoleType.fromValue(input.readInt())

    private fun readUuid(input: DataInputStream): UUID = UUID.fromString(readString(input))

    private fun readString(input: DataInputStream): String {
        val value = input.readUTF()
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_STRING_BYTES) { "Legacy string is too long" }
        return value
    }

    private fun readCount(input: DataInputStream, label: String, max: Int = MAX_COLLECTION_ENTRIES): Int =
        requireCount(input.readInt(), label, max)

    private fun requireCount(value: Int, label: String, max: Int = MAX_COLLECTION_ENTRIES): Int {
        require(value in 0..max) { "Invalid legacy $label count: $value" }
        return value
    }

    private fun requireCommunity(communities: List<Community>, regionId: Int): Community =
        communities.singleOrNull { it.regionNumberId == regionId }
            ?: error("Unknown or duplicate legacy region ID: $regionId")

    private enum class LegacyLayout(val versionedTurnover: Boolean) {
        ROLE_ONLY_0_2_3(false),
        ACCOUNT_RAW_0_3_1(false),
        ACCOUNT_RAW_1_0_0(false),
        ACCOUNT_VERSIONED_1_0_1(true)
    }

    private const val TURNOVER_VERSION_MARKER = -1
    private const val MAX_COMMUNITIES = 100_000
    private const val MAX_COLLECTION_ENTRIES = 1_000_000
    private const val MAX_STRING_BYTES = 64 * 1024
}
