package com.imyvm.community.infra

import com.imyvm.community.domain.Community
import com.imyvm.community.domain.MemberAccount
import com.imyvm.community.domain.Turnover
import com.imyvm.community.domain.community.CommunityJoinPolicy
import com.imyvm.community.domain.community.CommunityStatus
import com.imyvm.community.domain.community.Council
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.domain.community.council.CouncilVote
import com.imyvm.community.domain.community.council.ExecutionType
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
            }
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

                val community = Community(
                    regionNumberId = regionNumberId,
                    member = memberMap,
                    joinPolicy = joinPolicy,
                    status = status,
                    council = council,
                    announcements = announcements
                )
                communities.add(community)
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
                stream.writeInt(turnover.amount)
                stream.writeLong(turnover.timestamp)
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
            val isCouncilMember = stream.readBoolean()
            val governorship = stream.readInt()

            val mailSize = stream.readInt()
            val communityMail = ArrayList<Text>(mailSize)
            for (k in 0 until mailSize) {
                val mailString = stream.readUTF()
                communityMail.add(Text.of(mailString))
            }

            // Load turnover data
            val turnoverSize = stream.readInt()
            val turnoverList = ArrayList<Turnover>(turnoverSize)
            for (k in 0 until turnoverSize) {
                val amount = stream.readInt()
                val timestamp = stream.readLong()
                turnoverList.add(Turnover(amount, timestamp))
            }

            memberMap[uuid] = MemberAccount(
                joinedTime = joinedTime,
                basicRoleType = role,
                isCouncilMember = isCouncilMember,
                governorship = governorship,
                mail = communityMail,
                turnover = turnoverList
            )
        }
        return memberMap
    }

    private fun saveCommunityCouncil(stream: DataOutputStream, community: Community) {
        stream.writeBoolean(community.council.enabled)
        stream.writeInt(community.council.voteSet.size)
        
        for (vote in community.council.voteSet) {
            stream.writeInt(vote.executionType.ordinal)
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
            val executionType = ExecutionType.values()[stream.readInt()]
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
                executionType = executionType,
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
            
            // Save readBy set
            stream.writeInt(announcement.readBy.size)
            for (readerUUID in announcement.readBy) {
                stream.writeUTF(readerUUID.toString())
            }
        }
    }

    private fun loadCommunityAnnouncements(stream: DataInputStream): MutableList<com.imyvm.community.domain.Announcement> {
        val announcementsSize = stream.readInt()
        val announcements = mutableListOf<com.imyvm.community.domain.Announcement>()
        
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
            
            announcements.add(com.imyvm.community.domain.Announcement(
                id = id,
                content = content,
                authorUUID = authorUUID,
                timestamp = timestamp,
                isDeleted = isDeleted,
                readBy = readBy
            ))
        }
        
        return announcements
    }
}
