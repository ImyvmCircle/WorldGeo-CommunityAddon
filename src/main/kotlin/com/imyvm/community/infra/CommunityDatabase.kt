package com.imyvm.community.infra

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.*
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.AdminPrivileges
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.*

object CommunityDatabase {

    private const val DATABASE_FILENAME = "iwg_community.db"
    lateinit var communities: MutableList<Community>

    @Throws(IOException::class)
    fun save() {
        val file = this.getDatabasePath()
        DataOutputStream(file.toFile().outputStream()).use { stream ->
            stream.writeInt(communities.size)
            for (community in communities) {
                saveCommunityRegionNumberId(stream,community)
                saveCommunityMember(stream,community)
                stream.writeInt(community.joinPolicy.value)
                stream.writeInt(community.status.value)
                saveCommunityAnnouncements(stream, community)
                saveCommunityExpenditures(stream, community)
                saveCommunityMessages(stream, community)
                stream.writeLong(community.creationCost)
            }

            savePendingOperations(stream)
            saveNameChangeCooldownsSection(stream)
            saveLikesSection(stream)
            saveCommunityIncomeSection(stream)
        }
    }

    @Throws(IOException::class)
    fun load() {
        val file = this.getDatabasePath()
        if (!file.toFile().exists()) {
            communities = mutableListOf()
            return
        }

        DataInputStream(file.toFile().inputStream()).use { stream ->
            val size = stream.readInt()
            communities = ArrayList(size)
            for (i in 0 until size) {
                val regionNumberId = loadCommunityRegionNumberId(stream)
                val memberCount = stream.readInt()
                val memberMap = loadMemberMap(stream, memberCount)
                val joinPolicy = CommunityJoinPolicy.fromValue(stream.readInt())
                val status = CommunityStatus.fromValue(stream.readInt())
                val announcements = loadCommunityAnnouncements(stream)
                val expenditures = loadCommunityExpenditures(stream)
                val messages = loadCommunityMessages(stream)
                
                val creationCost = if (stream.available() > 0) {
                    try {
                        stream.readLong()
                    } catch (e: Exception) {
                        0L
                    }
                } else {
                    0L
                }

                val community = Community(
                    regionNumberId = regionNumberId,
                    member = memberMap,
                    joinPolicy = joinPolicy,
                    status = status,
                    announcements = announcements,
                    expenditures = expenditures,
                    messages = messages,
                    creationCost = creationCost
                )
                communities.add(community)
            }
            
            if (stream.available() > 0) {
                loadPendingOperations(stream)
            }
            if (stream.available() > 0) {
                try {
                    loadNameChangeCooldownsSection(stream)
                } catch (e: Exception) {
                    com.imyvm.community.WorldGeoCommunityAddon.logger.error("Failed to load name change cooldowns: ${e.message}")
                }
            }
            if (stream.available() > 0) {
                try {
                    loadLikesSection(stream)
                } catch (e: Exception) {
                    com.imyvm.community.WorldGeoCommunityAddon.logger.error("Failed to load likes data: ${e.message}")
                }
            }
            if (stream.available() > 0) {
                try {
                    loadCommunityIncomeSection(stream)
                } catch (e: Exception) {
                    com.imyvm.community.WorldGeoCommunityAddon.logger.error("Failed to load community income data: ${e.message}")
                }
            }
        }
    }

    fun addCommunity(community: Community) {
        communities.add(community)
    }

    fun removeCommunity(targetCommunity: Community) {
        communities.remove(targetCommunity)
    }

    fun getCommunityById(regionId: Int): Community? {
        return communities.find { it.regionNumberId == regionId }
    }

    private fun getDatabasePath(): Path {
        return FabricLoader.getInstance().gameDir
            .resolve("world")
            .resolve(DATABASE_FILENAME)
    }

    private fun saveCommunityRegionNumberId(stream: DataOutputStream, community: Community){
        if (community.regionNumberId == null) {
            stream.writeBoolean(false)
        } else {
            stream.writeBoolean(true)
            stream.writeInt(community.regionNumberId)
        }
    }

    private fun saveCommunityMember(stream: DataOutputStream, community: Community){
        stream.writeInt(community.member.size)
        for ((uuid, memberAccount) in community.member) {
            stream.writeUTF(uuid.toString())

            stream.writeLong(memberAccount.joinedTime)
            stream.writeInt(memberAccount.basicRoleType.value)

            stream.writeInt(memberAccount.mail.size)
            for (mailItem in memberAccount.mail) {
                stream.writeUTF(mailItem.string)
            }

            writeTurnoverList(stream, memberAccount.turnover)
            
            stream.writeBoolean(memberAccount.isInvited)
            stream.writeBoolean(memberAccount.chatHistoryEnabled)

            val privileges = memberAccount.adminPrivileges
            stream.writeBoolean(privileges != null)
            if (privileges != null) {
                val enabled = privileges.getEnabled()
                stream.writeInt(enabled.size)
                for (privilege in enabled) {
                    stream.writeInt(privilege.ordinal)
                }
            }
        }
    }

    private fun loadCommunityRegionNumberId(stream: DataInputStream): Int? {
        return if (stream.readBoolean()) {
            stream.readInt()
        } else {
            null
        }
    }

    private fun loadMemberMap(stream: DataInputStream, memberCount: Int): HashMap<UUID, MemberAccount> {
        val memberMap = HashMap<UUID, MemberAccount>(memberCount)
        for (j in 0 until memberCount) {
            val uuid = UUID.fromString(stream.readUTF())

            val joinedTime = stream.readLong()
            val role = MemberRoleType.fromValue(stream.readInt())

            val mailSize = stream.readInt()
            val communityMail = ArrayList<Component>(mailSize)
            for (k in 0 until mailSize) {
                val mailString = stream.readUTF()
                communityMail.add(Component.literal(mailString))
            }

            val turnoverList = readTurnoverList(stream)
            
            val isInvited = try {
                stream.readBoolean()
            } catch (e: Exception) {
                false
            }

            val chatHistoryEnabled = try {
                stream.readBoolean()
            } catch (e: Exception) {
                true
            }

            val adminPrivileges = try {
                if (stream.readBoolean()) {
                    val count = stream.readInt()
                    val set = mutableSetOf<AdminPrivilege>()
                    for (k in 0 until count) {
                        val ordinal = stream.readInt()
                        if (ordinal < AdminPrivilege.entries.size) set.add(AdminPrivilege.entries[ordinal])
                    }
                    AdminPrivileges(set.toMutableSet())
                } else null
            } catch (e: Exception) {
                null
            }

            memberMap[uuid] = MemberAccount(
                joinedTime = joinedTime,
                basicRoleType = role,
                adminPrivileges = adminPrivileges,
                mail = communityMail,
                turnover = turnoverList,
                isInvited = isInvited,
                chatHistoryEnabled = chatHistoryEnabled
            )
        }
        return memberMap
    }

    private fun saveCommunityAnnouncements(stream: DataOutputStream, community: Community) {
        stream.writeInt(community.announcements.size)
        
        for (announcement in community.announcements) {
            stream.writeUTF(announcement.id.toString())
            stream.writeUTF(announcement.content.string)
            stream.writeUTF(announcement.authorUUID.toString())
            stream.writeLong(announcement.timestamp)
            stream.writeBoolean(announcement.isDeleted)

            stream.writeInt(announcement.readBy.size)
            for (readerUUID in announcement.readBy) {
                stream.writeUTF(readerUUID.toString())
            }
        }
    }

    private fun loadCommunityAnnouncements(stream: DataInputStream): MutableList<Announcement> {
        val announcementsSize = stream.readInt()
        val announcements = mutableListOf<Announcement>()
        
        for (i in 0 until announcementsSize) {
            val id = UUID.fromString(stream.readUTF())
            val contentString = stream.readUTF()
            val content = com.imyvm.community.util.TextParser.parse(contentString)
            val authorUUID = UUID.fromString(stream.readUTF())
            val timestamp = stream.readLong()
            val isDeleted = stream.readBoolean()

            val readBySize = stream.readInt()
            val readBy = mutableSetOf<UUID>()
            for (j in 0 until readBySize) {
                readBy.add(UUID.fromString(stream.readUTF()))
            }
            
            announcements.add(
                Announcement(
                    id = id,
                    content = content,
                    authorUUID = authorUUID,
                    timestamp = timestamp,
                    isDeleted = isDeleted,
                    readBy = readBy
                )
            )
        }
        
        return announcements
    }


    private fun saveCommunityExpenditures(stream: DataOutputStream, community: Community) {
        writeTurnoverList(stream, community.expenditures)
    }

    private fun loadCommunityExpenditures(stream: DataInputStream): ArrayList<Turnover> {
        return try {
            readTurnoverList(stream)
        } catch (e: Exception) {
            ArrayList()
        }
    }

    private fun saveCommunityMessages(stream: DataOutputStream, community: Community) {
        stream.writeInt(community.messages.size)
        for (message in community.messages) {
            stream.writeUTF(message.id.toString())
            stream.writeInt(message.type.value)
            stream.writeUTF(message.content.string)
            stream.writeUTF(message.senderUUID.toString())
            stream.writeLong(message.timestamp)
            stream.writeBoolean(message.isDeleted)
            
            stream.writeInt(message.readBy.size)
            for (uuid in message.readBy) {
                stream.writeUTF(uuid.toString())
            }
            
            stream.writeBoolean(message.recipientUUID != null)
            if (message.recipientUUID != null) {
                stream.writeUTF(message.recipientUUID.toString())
            }
        }
    }

    private fun loadCommunityMessages(stream: DataInputStream): MutableList<CommunityMessage> {
        val messages = try {
            val size = stream.readInt()
            val list = mutableListOf<CommunityMessage>()
            for (i in 0 until size) {
                val id = UUID.fromString(stream.readUTF())
                val type = MessageType.entries.find { it.value == stream.readInt() } ?: MessageType.CHAT
                val content = Component.literal(stream.readUTF())
                val senderUUID = UUID.fromString(stream.readUTF())
                val timestamp = stream.readLong()
                val isDeleted = stream.readBoolean()
                
                val readBySize = stream.readInt()
                val readBy = mutableSetOf<UUID>()
                for (j in 0 until readBySize) {
                    readBy.add(UUID.fromString(stream.readUTF()))
                }
                
                val hasRecipient = stream.readBoolean()
                val recipientUUID = if (hasRecipient) {
                    UUID.fromString(stream.readUTF())
                } else {
                    null
                }
                
                list.add(CommunityMessage(
                    id = id,
                    type = type,
                    content = content,
                    senderUUID = senderUUID,
                    timestamp = timestamp,
                    isDeleted = isDeleted,
                    readBy = readBy,
                    recipientUUID = recipientUUID
                ))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
        return messages
    }
    
    private fun savePendingOperations(stream: DataOutputStream) {
        val ops = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations
        stream.writeInt(ops.size)
        for ((regionId, operation) in ops) {
            stream.writeInt(regionId)
            stream.writeLong(operation.expireAt)
            stream.writeInt(operation.type.value)

            val hasInviter = operation.inviterUUID != null
            stream.writeBoolean(hasInviter)
            if (hasInviter) {
                stream.writeUTF(operation.inviterUUID.toString())
            }

            val hasInvitee = operation.inviteeUUID != null
            stream.writeBoolean(hasInvitee)
            if (hasInvitee) {
                stream.writeUTF(operation.inviteeUUID.toString())
            }

            val hasCreationData = operation.creationData != null
            stream.writeBoolean(hasCreationData)
            if (hasCreationData) {
                val data = operation.creationData!!
                stream.writeUTF(data.communityName)
                stream.writeUTF(data.communityType)
                stream.writeUTF(data.shapeName)
                stream.writeInt(data.regionNumberId)
                stream.writeUTF(data.creatorUUID.toString())
                stream.writeLong(data.totalCost)
            }

        }
    }
    
    private fun loadPendingOperations(stream: DataInputStream) {
        try {
            val size = stream.readInt()
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.clear()
            
            for (i in 0 until size) {
                val regionId = stream.readInt()
                val expireAt = stream.readLong()
                val type = com.imyvm.community.domain.model.PendingOperationType.fromValue(stream.readInt())

                val hasInviter = stream.readBoolean()
                val inviterUUID = if (hasInviter) {
                    UUID.fromString(stream.readUTF())
                } else {
                    null
                }

                val hasInvitee = stream.readBoolean()
                val inviteeUUID = if (hasInvitee) {
                    UUID.fromString(stream.readUTF())
                } else {
                    null
                }

                val hasCreationData = stream.readBoolean()
                val creationData = if (hasCreationData) {
                    val communityName = stream.readUTF()
                    val communityType = stream.readUTF()
                    val shapeName = stream.readUTF()
                    val creationRegionId = stream.readInt()
                    val creatorUUID = UUID.fromString(stream.readUTF())
                    val totalCost = stream.readLong()
                    com.imyvm.community.domain.model.CreationConfirmationData(
                        communityName = communityName,
                        communityType = communityType,
                        shapeName = shapeName,
                        regionNumberId = creationRegionId,
                        creatorUUID = creatorUUID,
                        totalCost = totalCost
                    )
                } else {
                    null
                }

                val operation = com.imyvm.community.domain.model.PendingOperation(
                    expireAt = expireAt,
                    type = type,
                    inviterUUID = inviterUUID,
                    inviteeUUID = inviteeUUID,
                    creationData = creationData
                )
                com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[regionId] = operation
            }
        } catch (e: Exception) {
            com.imyvm.community.WorldGeoCommunityAddon.logger.error("Failed to load pending operations: ${e.message}")
        }
    }

    private fun saveNameChangeCooldownsSection(stream: DataOutputStream) {
        val communitiesWithCooldowns = communities.filter { it.nameChangeCooldowns.isNotEmpty() && it.regionNumberId != null }
        stream.writeInt(communitiesWithCooldowns.size)
        for (community in communitiesWithCooldowns) {
            stream.writeInt(community.regionNumberId!!)
            stream.writeInt(community.nameChangeCooldowns.size)
            for ((key, timestamp) in community.nameChangeCooldowns) {
                stream.writeUTF(key)
                stream.writeLong(timestamp)
            }
        }
    }

    private fun loadNameChangeCooldownsSection(stream: DataInputStream) {
        val entryCount = stream.readInt()
        for (i in 0 until entryCount) {
            val regionId = stream.readInt()
            val mapSize = stream.readInt()
            val cooldowns = HashMap<String, Long>(mapSize)
            for (j in 0 until mapSize) {
                val key = stream.readUTF()
                val timestamp = stream.readLong()
                cooldowns[key] = timestamp
            }
            getCommunityById(regionId)?.nameChangeCooldowns = cooldowns
        }
    }

    private fun saveLikesSection(stream: DataOutputStream) {
        val communitiesWithLikes = communities.filter { (it.likeCount > 0 || it.lastLikedBy.isNotEmpty()) && it.regionNumberId != null }
        stream.writeInt(communitiesWithLikes.size)
        for (community in communitiesWithLikes) {
            stream.writeInt(community.regionNumberId!!)
            stream.writeInt(community.likeCount)
            stream.writeInt(community.lastLikedBy.size)
            for ((uuid, timestamp) in community.lastLikedBy) {
                stream.writeUTF(uuid.toString())
                stream.writeLong(timestamp)
            }
        }
    }

    private fun loadLikesSection(stream: DataInputStream) {
        val entryCount = stream.readInt()
        for (i in 0 until entryCount) {
            val regionId = stream.readInt()
            val likeCount = stream.readInt()
            val mapSize = stream.readInt()
            val lastLikedBy = HashMap<UUID, Long>(mapSize)
            for (j in 0 until mapSize) {
                val uuid = UUID.fromString(stream.readUTF())
                val timestamp = stream.readLong()
                lastLikedBy[uuid] = timestamp
            }
            getCommunityById(regionId)?.let {
                it.likeCount = likeCount
                it.lastLikedBy = lastLikedBy
            }
        }
    }

    private fun saveCommunityIncomeSection(stream: DataOutputStream) {
        val communitiesWithIncome = communities.filter { it.communityIncome.isNotEmpty() && it.regionNumberId != null }
        stream.writeInt(communitiesWithIncome.size)
        for (community in communitiesWithIncome) {
            stream.writeInt(community.regionNumberId!!)
            writeTurnoverList(stream, community.communityIncome)
        }
    }

    private fun loadCommunityIncomeSection(stream: DataInputStream) {
        val entryCount = stream.readInt()
        for (i in 0 until entryCount) {
            val regionId = stream.readInt()
            val income = readTurnoverList(stream)
            getCommunityById(regionId)?.communityIncome = income
        }
    }

    private fun writeTurnoverList(stream: DataOutputStream, list: List<Turnover>) {
        stream.writeInt(-1)
        stream.writeInt(list.size)
        for (t in list) {
            stream.writeLong(t.amount)
            stream.writeLong(t.timestamp)
            stream.writeInt(t.source.value)
            stream.writeUTF(t.descriptionKey ?: "")
            stream.writeInt(t.descriptionArgs.size)
            for (arg in t.descriptionArgs) {
                stream.writeUTF(arg)
            }
        }
    }

    private fun readTurnoverList(stream: DataInputStream): ArrayList<Turnover> {
        val firstInt = stream.readInt()
        return if (firstInt == -1) {
            val size = stream.readInt()
            val list = ArrayList<Turnover>(size)
            for (i in 0 until size) {
                val amount = stream.readLong()
                val timestamp = stream.readLong()
                val source = TurnoverSource.fromValue(stream.readInt())
                val rawDescKey = stream.readUTF()
                val descKey = if (rawDescKey.isEmpty()) null else rawDescKey
                val argCount = stream.readInt()
                val args = (0 until argCount).map { stream.readUTF() }
                list.add(Turnover(amount, timestamp, source, descKey, args))
            }
            list
        } else {
            val list = ArrayList<Turnover>(firstInt)
            for (i in 0 until firstInt) {
                val amount = stream.readLong()
                val timestamp = stream.readLong()
                list.add(Turnover(amount, timestamp))
            }
            list
        }
    }
}
