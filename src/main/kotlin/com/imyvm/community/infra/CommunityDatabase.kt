package com.imyvm.community.infra

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.community.*
import com.imyvm.community.domain.model.community.council.CouncilVote
import com.imyvm.community.domain.policy.permission.AdministrationPermission
import com.imyvm.community.domain.policy.permission.AdministrationPermissions
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.text.Text
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
                saveCommunityCouncil(stream, community)
                saveCommunityAnnouncements(stream, community)
                saveCommunityAdministrationPermissions(stream, community)
                saveCommunityExpenditures(stream, community)
                saveCommunityMessages(stream, community)
                stream.writeLong(community.creationCost)
            }

            savePendingOperations(stream)
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
                val council = loadCommunityCouncil(stream)
                val announcements = loadCommunityAnnouncements(stream)
                val administrationPermissions = loadCommunityAdministrationPermissions(stream)
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
                    council = council,
                    announcements = announcements,
                    administrationPermissions = administrationPermissions,
                    expenditures = expenditures,
                    messages = messages,
                    creationCost = creationCost
                )
                communities.add(community)
            }
            
            if (stream.available() > 0) {
                loadPendingOperations(stream)
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
            stream.writeBoolean(memberAccount.isCouncilMember)
            stream.writeInt(memberAccount.governorship)

            stream.writeInt(memberAccount.mail.size)
            for (mailItem in memberAccount.mail) {
                stream.writeUTF(mailItem.string)
            }

            stream.writeInt(memberAccount.turnover.size)
            for (turnover in memberAccount.turnover) {
                stream.writeLong(turnover.amount)
                stream.writeLong(turnover.timestamp)
            }
            
            stream.writeBoolean(memberAccount.isInvited)
            stream.writeBoolean(memberAccount.chatHistoryEnabled)
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
            val isCouncilMember = stream.readBoolean()
            val governorship = stream.readInt()

            val mailSize = stream.readInt()
            val communityMail = ArrayList<Text>(mailSize)
            for (k in 0 until mailSize) {
                val mailString = stream.readUTF()
                communityMail.add(Text.of(mailString))
            }

            val turnoverSize = stream.readInt()
            val turnoverList = ArrayList<Turnover>(turnoverSize)
            for (k in 0 until turnoverSize) {
                val amount = stream.readLong()
                val timestamp = stream.readLong()
                turnoverList.add(Turnover(amount, timestamp))
            }
            
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

            memberMap[uuid] = MemberAccount(
                joinedTime = joinedTime,
                basicRoleType = role,
                isCouncilMember = isCouncilMember,
                governorship = governorship,
                mail = communityMail,
                turnover = turnoverList,
                isInvited = isInvited,
                chatHistoryEnabled = chatHistoryEnabled
            )
        }
        return memberMap
    }

    private fun saveCommunityCouncil(stream: DataOutputStream, community: Community) {
        stream.writeBoolean(community.council.enabled)
        stream.writeInt(community.council.voteSet.size)
        
        for (vote in community.council.voteSet) {
            if (vote.permission == null) {
                stream.writeBoolean(false)
            } else {
                stream.writeBoolean(true)
                stream.writeInt(vote.permission.ordinal)
            }
            stream.writeLong(vote.proposeTime)

            if (vote.proposerUUID == null) {
                stream.writeBoolean(false)
            } else {
                stream.writeBoolean(true)
                stream.writeUTF(vote.proposerUUID.toString())
            }

            stream.writeInt(vote.yeaVotes.size)
            for (yea in vote.yeaVotes) {
                stream.writeUTF(yea.toString())
            }

            stream.writeInt(vote.nayVotes.size)
            for (nay in vote.nayVotes) {
                stream.writeUTF(nay.toString())
            }

            if (vote.isEnacted == null) {
                stream.writeBoolean(false)
                stream.writeBoolean(false)
            } else {
                stream.writeBoolean(true)
                stream.writeBoolean(vote.isEnacted!!)
            }
        }
    }

    private fun loadCommunityCouncil(stream: DataInputStream): Council {
        val enabled = stream.readBoolean()
        val voteSetSize = stream.readInt()
        val voteSet = mutableSetOf<CouncilVote>()
        
        for (i in 0 until voteSetSize) {
            val permission = if (stream.readBoolean()) {
                AdministrationPermission.entries[stream.readInt()]
            } else {
                null
            }
            val proposeTime = stream.readLong()

            val proposerUUID = if (stream.readBoolean()) {
                UUID.fromString(stream.readUTF())
            } else {
                null
            }

            val yeaSize = stream.readInt()
            val yeaVotes = mutableListOf<UUID>()
            for (j in 0 until yeaSize) {
                yeaVotes.add(UUID.fromString(stream.readUTF()))
            }

            val naySize = stream.readInt()
            val nayVotes = mutableListOf<UUID>()
            for (j in 0 until naySize) {
                nayVotes.add(UUID.fromString(stream.readUTF()))
            }

            val hasEnacted = stream.readBoolean()
            val isEnacted = if (hasEnacted) {
                stream.readBoolean()
            } else {
                stream.readBoolean()
                null
            }
            
            voteSet.add(CouncilVote(
                permission = permission,
                proposeTime = proposeTime,
                proposerUUID = proposerUUID,
                yeaVotes = yeaVotes,
                nayVotes = nayVotes,
                isEnacted = isEnacted
            ))
        }
        
        return Council(
            enabled = enabled,
            voteSet = voteSet
        )
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

    private fun saveCommunityAdministrationPermissions(stream: DataOutputStream, community: Community) {
        val adminPermissions = community.administrationPermissions.getEnabledForAdmin()
        stream.writeInt(adminPermissions.size)
        for (permission in adminPermissions) {
            stream.writeInt(permission.ordinal)
        }

        val councilPermissions = community.administrationPermissions.getEnabledForCouncil()
        stream.writeInt(councilPermissions.size)
        for (permission in councilPermissions) {
            stream.writeInt(permission.ordinal)
        }
    }

    private fun loadCommunityAdministrationPermissions(stream: DataInputStream): AdministrationPermissions {
        val adminPermissionsSize = stream.readInt()
        val adminPermissions = mutableSetOf<AdministrationPermission>()
        for (i in 0 until adminPermissionsSize) {
            adminPermissions.add(AdministrationPermission.entries[stream.readInt()])
        }

        val councilPermissionsSize = stream.readInt()
        val councilPermissions = mutableSetOf<AdministrationPermission>()
        for (i in 0 until councilPermissionsSize) {
            councilPermissions.add(AdministrationPermission.entries[stream.readInt()])
        }

        return AdministrationPermissions(adminPermissions, councilPermissions)
    }

    private fun saveCommunityExpenditures(stream: DataOutputStream, community: Community) {
        stream.writeInt(community.expenditures.size)
        for (expenditure in community.expenditures) {
            stream.writeLong(expenditure.amount)
            stream.writeLong(expenditure.timestamp)
        }
    }

    private fun loadCommunityExpenditures(stream: DataInputStream): ArrayList<Turnover> {
        val expenditures = try {
            val size = stream.readInt()
            val list = ArrayList<Turnover>(size)
            for (i in 0 until size) {
                val amount = stream.readLong()
                val timestamp = stream.readLong()
                list.add(Turnover(amount, timestamp))
            }
            list
        } catch (e: Exception) {
            ArrayList()
        }
        return expenditures
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
                val content = Text.of(stream.readUTF())
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
}
