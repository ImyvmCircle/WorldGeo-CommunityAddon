package com.imyvm.community.infra

import com.imyvm.community.application.communication.migrateLegacyCommunicationsToShards
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.CreationConfirmationData
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.domain.model.RenameConfirmationData
import com.imyvm.community.domain.model.ScopeModificationConfirmationData
import com.imyvm.community.domain.model.ScopeTransferConfirmationData
import com.imyvm.community.domain.model.SettingConfirmationData
import com.imyvm.community.domain.model.TeleportPointConfirmationData
import com.imyvm.community.domain.model.TreasuryGrantConfirmationData
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.pendingOperationKey
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.*
import com.imyvm.community.domain.model.title.CommunityTitleSlot
import com.imyvm.community.domain.model.title.CommunityTitleState
import com.imyvm.community.domain.model.fiscal.CommunityFiscalObservation
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicySwitch
import com.imyvm.community.domain.model.fiscal.CommunityFiscalState
import com.imyvm.community.domain.model.development.CommunityDevelopmentBreakdown
import com.imyvm.community.domain.model.development.CommunityDevelopmentInputs
import com.imyvm.community.domain.model.development.CommunityDevelopmentState
import com.imyvm.community.domain.model.development.CommunityLandPriceSnapshot
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.AdminPrivileges
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.zip.CRC32

object CommunityDatabase {

    private const val DATABASE_FILENAME = "iwg_community.db"
    private const val DATABASE_VERSION_MARKER = -3
    private const val DATABASE_VERSION = 4
    private const val PENDING_SECTION_VERSION_MARKER = -2
    private const val PENDING_SECTION_VERSION = 3
    private const val SECTION_FRAME_MARKER = -4
    private const val SECTION_PENDING_OPERATIONS = 1
    private const val SECTION_NAME_CHANGE_COOLDOWNS = 2
    private const val SECTION_LIKES = 3
    private const val SECTION_COMMUNITY_INCOME = 4
    private const val SECTION_V4_STATE = 5
    private const val SECTION_COMMUNITY_BUILDING = 6
    private const val SECTION_COMMUNITY_BUILDING_CATALOG = 7
    private const val SECTION_COMMUNITY_TITLES = 8
    private const val SECTION_COMMUNITY_FISCAL = 9
    private const val SECTION_COMMUNITY_DEVELOPMENT = 10
    private const val MAX_SECTION_BYTES = 16 * 1024 * 1024
    private const val MAX_COMMUNITY_BYTES = 16 * 1024 * 1024
    private const val MAX_COMMUNITIES = 100_000
    private const val BUILDING_RECORD_VERSION = 3
    private const val MAX_COLLECTION_ENTRIES = 1_000_000
    private var legacyDatabaseLoaded = false
    private var legacyBackupCreated = false
    private val legacyLoadWarnings = mutableListOf<String>()
    lateinit var communities: MutableList<Community>

    @Throws(IOException::class)
    fun save() {
        val file = this.getDatabasePath()
        backupLegacyDatabaseBeforeSave(file)
        val parent = file.parent
        if (parent != null) Files.createDirectories(parent)
        val tempFile = Files.createTempFile(parent, "${DATABASE_FILENAME}.", ".tmp")
        try {
            DataOutputStream(Files.newOutputStream(tempFile)).use { stream ->
                stream.writeInt(DATABASE_VERSION_MARKER)
                stream.writeInt(DATABASE_VERSION)
                stream.writeInt(communities.size)
                for (community in communities) {
                    writeCommunityRecord(stream, community)
                }

                writeSection(stream, SECTION_PENDING_OPERATIONS) { savePendingOperations(it) }
                writeSection(stream, SECTION_NAME_CHANGE_COOLDOWNS) { saveNameChangeCooldownsSection(it) }
                writeSection(stream, SECTION_LIKES) { saveLikesSection(it) }
                writeSection(stream, SECTION_COMMUNITY_INCOME) { saveCommunityIncomeSection(it) }
                writeSection(stream, SECTION_V4_STATE) { saveV4StateSection(it) }
                writeSection(stream, SECTION_COMMUNITY_BUILDING) { saveCommunityBuildingSection(it) }
                writeSection(stream, SECTION_COMMUNITY_BUILDING_CATALOG) { saveCommunityBuildingCatalogSection(it) }
                writeSection(stream, SECTION_COMMUNITY_TITLES) { saveCommunityTitlesSection(it) }
                writeSection(stream, SECTION_COMMUNITY_FISCAL) { saveCommunityFiscalSection(it) }
                writeSection(stream, SECTION_COMMUNITY_DEVELOPMENT) { saveCommunityDevelopmentSection(it) }
            }
            replaceDatabaseFile(tempFile, file)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun replaceDatabaseFile(tempFile: Path, targetFile: Path) {
        try {
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeCommunityRecord(stream: DataOutputStream, community: Community) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { communityStream -> saveCommunityBody(communityStream, community) }
        val payload = buffer.toByteArray()
        stream.writeInt(payload.size)
        stream.write(payload)
    }

    private fun saveCommunityBody(stream: DataOutputStream, community: Community) {
        saveCommunityRegionNumberId(stream, community)
        saveCommunityMember(stream, community)
        stream.writeInt(community.joinPolicy.value)
        stream.writeInt(community.status.value)
        saveCommunityAnnouncements(stream, community)
        saveCommunityExpenditures(stream, community)
        saveCommunityMessages(stream, community)
        stream.writeLong(community.creationCost)
        saveMemberPendingRefunds(stream, community)
        saveMemberJoinFees(stream, community)
    }

    private fun writeSection(
        stream: DataOutputStream,
        tag: Int,
        writer: (DataOutputStream) -> Unit
    ) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { sectionStream -> writer(sectionStream) }
        val payload = buffer.toByteArray()
        stream.writeInt(SECTION_FRAME_MARKER)
        stream.writeInt(tag)
        stream.writeInt(payload.size)
        stream.write(payload)
    }

    private fun loadTrailingSections(stream: DataInputStream) {
        val firstInt = stream.readInt()
        if (firstInt == SECTION_FRAME_MARKER) {
            loadFramedSections(stream)
        } else {
            loadLegacyTrailingSections(stream, firstInt)
        }
    }

    private fun loadFramedSections(stream: DataInputStream) {
        while (true) {
            val tag = stream.readInt()
            val length = stream.readInt()
            require(length in 0..MAX_SECTION_BYTES) { "Invalid community database section length: $length" }
            val payload = stream.readNBytes(length)
            require(payload.size == length) { "Truncated community database section: $tag" }
            DataInputStream(ByteArrayInputStream(payload)).use { sectionStream ->
                if (loadFramedSection(tag, sectionStream)) {
                    require(sectionStream.available() == 0) { "Unread bytes in community database section: $tag" }
                }
            }
            if (stream.available() <= 0) return
            val marker = stream.readInt()
            require(marker == SECTION_FRAME_MARKER) { "Unexpected community database section marker: $marker" }
        }
    }

    private fun loadFramedSection(tag: Int, stream: DataInputStream): Boolean {
        return when (tag) {
            SECTION_PENDING_OPERATIONS -> {
                loadPendingOperations(stream, strict = true)
                true
            }
            SECTION_NAME_CHANGE_COOLDOWNS -> {
                loadNameChangeCooldownsSection(stream)
                true
            }
            SECTION_LIKES -> {
                loadLikesSection(stream)
                true
            }
            SECTION_COMMUNITY_INCOME -> {
                loadCommunityIncomeSection(stream)
                true
            }
            SECTION_COMMUNITY_BUILDING -> {
                loadCommunityBuildingSection(stream)
                true
            }
            SECTION_COMMUNITY_BUILDING_CATALOG -> {
                loadCommunityBuildingCatalogSection(stream)
                true
            }
            SECTION_V4_STATE -> {
                loadV4StateSection(stream)
                true
            }
            SECTION_COMMUNITY_TITLES -> {
                loadCommunityTitlesSection(stream)
                true
            }
            SECTION_COMMUNITY_FISCAL -> {
                loadCommunityFiscalSection(stream)
                true
            }
            SECTION_COMMUNITY_DEVELOPMENT -> {
                loadCommunityDevelopmentSection(stream)
                true
            }
            else -> {
                com.imyvm.community.WorldGeoCommunityAddon.logger.warn("Skipped unknown community database section: $tag")
                false
            }
        }
    }

    private fun loadLegacyTrailingSections(stream: DataInputStream, firstPendingInt: Int) {
        loadPendingOperations(stream, firstPendingInt)
        if (stream.available() > 0) {
            try {
                loadNameChangeCooldownsSection(stream)
            } catch (e: Exception) {
                recordLegacyLoadWarning("name change cooldowns", e)
            }
        }
        if (stream.available() > 0) {
            try {
                loadLikesSection(stream)
            } catch (e: Exception) {
                recordLegacyLoadWarning("likes data", e)
            }
        }
        if (stream.available() > 0) {
            try {
                loadCommunityIncomeSection(stream)
            } catch (e: Exception) {
                recordLegacyLoadWarning("community income data", e)
            }
        }
        if (stream.available() > 0) {
            try {
                loadV4StateSection(stream)
            } catch (e: Exception) {
                recordLegacyLoadWarning("v4 state data", e)
            }
        }
        if (stream.available() > 0) {
            try {
                loadCommunityBuildingSection(stream)
            } catch (e: Exception) {
                recordLegacyLoadWarning("community building data", e)
            }
        }
        if (stream.available() > 0) {
            try {
                loadCommunityBuildingCatalogSection(stream)
            } catch (e: Exception) {
                recordLegacyLoadWarning("community building catalog data", e)
            }
        }
    }

    @Throws(IOException::class)
    fun load() {
        loadDatabaseFile(this.getDatabasePath())
    }

    @Throws(IOException::class)
    fun load(server: MinecraftServer) {
        loadDatabaseFile(this.getDatabasePath(server))
    }

    private fun loadDatabaseFile(file: Path) {
        val previousCommunities = if (this::communities.isInitialized) communities else null
        val previousPendingOperations = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.toMap()
        legacyDatabaseLoaded = false
        legacyBackupCreated = false
        legacyLoadWarnings.clear()
        try {
            if (!file.toFile().exists()) {
                communities = mutableListOf()
                com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.clear()
                return
            }

            val payload = Files.readAllBytes(file)
            DataInputStream(ByteArrayInputStream(payload)).use { header ->
                if (header.readInt() != DATABASE_VERSION_MARKER) {
                    legacyDatabaseLoaded = true
                    backupLegacyDatabaseBeforeLoad(file)
                    val decoded = LegacyCommunityDatabaseDecoder.decode(payload)
                    communities = decoded.communities
                    rebuildMissingTreasuryAggregates()
                    migrateLegacyCommunicationsToShards(communities)
                    com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.clear()
                    com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.putAll(decoded.pendingOperations)
                    return
                }
            }

            DataInputStream(file.toFile().inputStream()).use { stream ->
                val firstInt = stream.readInt()
                val databaseVersion = if (firstInt == DATABASE_VERSION_MARKER) stream.readInt() else 1
                require(databaseVersion in 1..DATABASE_VERSION) { "Unsupported community database version: $databaseVersion" }
                legacyDatabaseLoaded = databaseVersion < DATABASE_VERSION
                if (legacyDatabaseLoaded) backupLegacyDatabaseBeforeLoad(file)
                val size = requireCount(
                    if (databaseVersion == 1) firstInt else stream.readInt(),
                    "community",
                    MAX_COMMUNITIES
                )
                val loadedCommunities = ArrayList<Community>(size)
                for (i in 0 until size) {
                    loadedCommunities.add(loadCommunityRecord(stream, databaseVersion))
                }
                communities = loadedCommunities
                com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.clear()

                if (stream.available() > 0) {
                    loadTrailingSections(stream)
                }
                rebuildMissingTreasuryAggregates()
                migrateLegacyCommunicationsToShards(communities)
            }
        } catch (e: Exception) {
            if (previousCommunities != null) communities = previousCommunities
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.clear()
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.putAll(previousPendingOperations)
            throw e
        }
    }

    private fun loadCommunityRecord(stream: DataInputStream, databaseVersion: Int): Community {
        if (databaseVersion < 3) return loadCommunityBody(stream, databaseVersion)

        val length = stream.readInt()
        require(length in 0..MAX_COMMUNITY_BYTES) { "Invalid community record length: $length" }
        val payload = stream.readNBytes(length)
        require(payload.size == length) { "Truncated community record" }
        DataInputStream(ByteArrayInputStream(payload)).use { communityStream ->
            val community = loadCommunityBody(communityStream, databaseVersion)
            require(communityStream.available() == 0) { "Unread bytes in community record" }
            return community
        }
    }

    private fun loadCommunityBody(stream: DataInputStream, databaseVersion: Int): Community {
        val regionNumberId = loadCommunityRegionNumberId(stream)
        val memberCount = stream.readInt()
        val memberMap = loadMemberMap(stream, memberCount)
        val joinPolicy = CommunityJoinPolicy.fromValue(stream.readInt())
        val status = CommunityStatus.fromValue(stream.readInt())
        val announcements = loadCommunityAnnouncements(stream)
        val expenditures = loadCommunityExpenditures(stream, strict = databaseVersion >= 2)
        val messages = loadCommunityMessages(stream, strict = databaseVersion >= 2)
        val creationCost = stream.readLong()
        if (databaseVersion >= 3) loadMemberPendingRefunds(stream, memberMap)
        if (databaseVersion >= 4) loadMemberJoinFees(stream, memberMap)

        return Community(
            regionNumberId = regionNumberId,
            member = memberMap,
            joinPolicy = joinPolicy,
            status = status,
            announcements = announcements,
            expenditures = expenditures,
            messages = messages,
            creationCost = creationCost
        )
    }

    fun addCommunity(community: Community) {
        communities.add(community)
    }

    fun removeCommunity(targetCommunity: Community) {
        communities.remove(targetCommunity)
    }

    fun backupDatabaseAfterLoadFailure(): Path? {
        return backupDatabaseFile(getDatabasePath(), "corrupt", System.currentTimeMillis())
    }

    fun getLegacyLoadWarnings(): List<String> = legacyLoadWarnings.toList()

    private fun backupLegacyDatabaseBeforeLoad(databaseFile: Path): Path? {
        val backupFile = backupDatabaseFile(databaseFile, "legacy", System.currentTimeMillis())
        legacyBackupCreated = backupFile != null
        return backupFile
    }

    private fun backupLegacyDatabaseBeforeSave(databaseFile: Path): Path? {
        if (!legacyDatabaseLoaded || legacyBackupCreated) return null
        val backupFile = backupDatabaseFile(databaseFile, "legacy", System.currentTimeMillis())
        legacyBackupCreated = backupFile != null
        return backupFile
    }

    private fun backupDatabaseFile(databaseFile: Path, label: String, timestamp: Long): Path? {
        if (!Files.exists(databaseFile)) return null
        val backupBase = databaseFile.resolveSibling("${databaseFile.fileName}.$label.$timestamp")
        var backupFile = backupBase
        var suffix = 1
        while (Files.exists(backupFile)) {
            backupFile = databaseFile.resolveSibling("${backupBase.fileName}.$suffix")
            suffix++
        }
        return Files.copy(databaseFile, backupFile)
    }

    private fun recordLegacyLoadWarning(section: String, error: Exception) {
        val message = "Failed to load legacy $section: ${error.message ?: error::class.java.simpleName}"
        legacyLoadWarnings.add(message)
        com.imyvm.community.WorldGeoCommunityAddon.logger.error(message, error)
    }

    private fun readCount(stream: DataInputStream, label: String, max: Int = MAX_COLLECTION_ENTRIES): Int {
        return requireCount(stream.readInt(), label, max)
    }

    private fun requireCount(count: Int, label: String, max: Int = MAX_COLLECTION_ENTRIES): Int {
        require(count in 0..max) { "Invalid $label count: $count" }
        return count
    }

    fun getCommunityById(regionId: Int): Community? {
        return communities.find { it.regionNumberId == regionId }
    }

    private fun getDatabasePath(): Path {
        return getDatabasePath(com.imyvm.community.WorldGeoCommunityAddon.server)
    }

    private fun getDatabasePath(server: MinecraftServer?): Path {
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT).resolve(DATABASE_FILENAME)
        }
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

            stream.writeInt(0)

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

    private fun saveMemberPendingRefunds(stream: DataOutputStream, community: Community) {
        val pendingRefunds = community.member.filterValues { it.pendingRefund > 0L }
        stream.writeInt(pendingRefunds.size)
        for ((uuid, memberAccount) in pendingRefunds) {
            stream.writeUTF(uuid.toString())
            stream.writeLong(memberAccount.pendingRefund)
        }
    }

    private fun loadMemberPendingRefunds(stream: DataInputStream, memberMap: HashMap<UUID, MemberAccount>) {
        try {
            val refundCount = readCount(stream, "pending refund")
            for (i in 0 until refundCount) {
                val uuid = UUID.fromString(stream.readUTF())
                val amount = stream.readLong()
                if (amount > 0L) memberMap[uuid]?.pendingRefund = amount
            }
        } catch (e: Exception) {
            return
        }
    }

    private fun saveMemberJoinFees(stream: DataOutputStream, community: Community) {
        val joinFees = community.member.filterValues { it.joinFeePaid > 0L }
        stream.writeInt(joinFees.size)
        for ((uuid, memberAccount) in joinFees) {
            stream.writeUTF(uuid.toString())
            stream.writeLong(memberAccount.joinFeePaid)
        }
    }

    private fun loadMemberJoinFees(stream: DataInputStream, memberMap: HashMap<UUID, MemberAccount>) {
        try {
            val feeCount = readCount(stream, "member join fee")
            for (i in 0 until feeCount) {
                val uuid = UUID.fromString(stream.readUTF())
                val amount = stream.readLong()
                if (amount > 0L) memberMap[uuid]?.joinFeePaid = amount
            }
        } catch (e: Exception) {
            return
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
        requireCount(memberCount, "member")
        val memberMap = HashMap<UUID, MemberAccount>(memberCount)
        for (j in 0 until memberCount) {
            val uuid = UUID.fromString(stream.readUTF())

            val joinedTime = stream.readLong()
            val role = MemberRoleType.fromValue(stream.readInt())

            val mailSize = readCount(stream, "member mail")
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
                    val count = readCount(stream, "admin privilege")
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
        stream.writeInt(0)
    }

    private fun loadCommunityAnnouncements(stream: DataInputStream): MutableList<Announcement> {
        val announcementsSize = readCount(stream, "announcement")
        val announcements = mutableListOf<Announcement>()
        
        for (i in 0 until announcementsSize) {
            val id = UUID.fromString(stream.readUTF())
            val contentString = stream.readUTF()
            val content = com.imyvm.community.util.TextParser.parse(contentString)
            val authorUUID = UUID.fromString(stream.readUTF())
            val timestamp = stream.readLong()
            val isDeleted = stream.readBoolean()

            val readBySize = readCount(stream, "announcement read")
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

    private fun loadCommunityExpenditures(stream: DataInputStream, strict: Boolean): ArrayList<Turnover> {
        return try {
            readTurnoverList(stream)
        } catch (e: Exception) {
            if (strict) throw e
            ArrayList()
        }
    }

    private fun saveCommunityMessages(stream: DataOutputStream, community: Community) {
        stream.writeInt(0)
    }

    private fun loadCommunityMessages(stream: DataInputStream, strict: Boolean): MutableList<CommunityMessage> {
        val messages = try {
            val size = readCount(stream, "message")
            val list = mutableListOf<CommunityMessage>()
            for (i in 0 until size) {
                val id = UUID.fromString(stream.readUTF())
                val type = MessageType.entries.find { it.value == stream.readInt() } ?: MessageType.CHAT
                val content = Component.literal(stream.readUTF())
                val senderUUID = UUID.fromString(stream.readUTF())
                val timestamp = stream.readLong()
                val isDeleted = stream.readBoolean()
                
                val readBySize = readCount(stream, "message read")
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
            if (strict) throw e
            mutableListOf()
        }
        return messages
    }
    
    private fun savePendingOperations(stream: DataOutputStream) {
        val ops = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations
        stream.writeInt(PENDING_SECTION_VERSION_MARKER)
        stream.writeInt(PENDING_SECTION_VERSION)
        stream.writeInt(ops.size)
        for ((operationKey, operation) in ops) {
            stream.writeLong(operationKey)
            stream.writeLong(operation.expireAt)
            stream.writeInt(operation.type.value)
            writeNullableUUID(stream, operation.inviterUUID)
            writeNullableUUID(stream, operation.inviteeUUID)
            writeCreationData(stream, operation.creationData)
            writeScopeModificationData(stream, operation.modificationData)
            writeTeleportPointData(stream, operation.teleportPointData)
            writeSettingData(stream, operation.settingData)
            writeRenameData(stream, operation.renameData)
            writeScopeTransferData(stream, operation.transferData)
            writeTreasuryGrantData(stream, operation.treasuryGrantData)
        }
    }

    private fun loadPendingOperations(
        stream: DataInputStream,
        firstStoredInt: Int? = null,
        strict: Boolean = false
    ) {
        try {
            val firstInt = firstStoredInt ?: stream.readInt()
            val version = if (firstInt == PENDING_SECTION_VERSION_MARKER) stream.readInt() else 1
            require(version in 1..PENDING_SECTION_VERSION) { "Unsupported pending operation section version: $version" }
            val size = requireCount(
                if (version == 1) firstInt else stream.readInt(),
                "pending operation"
            )
            val loadedOperations = mutableMapOf<Long, PendingOperation>()

            for (i in 0 until size) {
                val storedKey = if (version >= 3) stream.readLong() else stream.readInt().toLong()
                val expireAt = stream.readLong()
                val type = com.imyvm.community.domain.model.PendingOperationType.fromValue(stream.readInt())
                val operationKey = if (version >= 3) storedKey else pendingOperationKey(storedKey.toInt(), type)
                val inviterUUID = readNullableUUID(stream)
                val inviteeUUID = readNullableUUID(stream)
                val creationData = readCreationData(stream)
                val modificationData = if (version >= 2) readScopeModificationData(stream) else null
                val teleportPointData = if (version >= 2) readTeleportPointData(stream) else null
                val settingData = if (version >= 2) readSettingData(stream) else null
                val renameData = if (version >= 2) readRenameData(stream) else null
                val transferData = if (version >= 2) readScopeTransferData(stream) else null
                val treasuryGrantData = if (version >= 2) readTreasuryGrantData(stream) else null

                val operation = PendingOperation(
                    expireAt = expireAt,
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
                loadedOperations[operationKey] = operation
            }
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.clear()
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.putAll(loadedOperations)
        } catch (e: Exception) {
            com.imyvm.community.WorldGeoCommunityAddon.logger.error("Failed to load pending operations: ${e.message}")
            if (strict) throw e
        }
    }

    private fun writeNullableUUID(stream: DataOutputStream, uuid: UUID?) {
        stream.writeBoolean(uuid != null)
        if (uuid != null) stream.writeUTF(uuid.toString())
    }

    private fun readNullableUUID(stream: DataInputStream): UUID? {
        return if (stream.readBoolean()) UUID.fromString(stream.readUTF()) else null
    }

    private fun writeNullableString(stream: DataOutputStream, value: String?) {
        stream.writeBoolean(value != null)
        if (value != null) stream.writeUTF(value)
    }

    private fun readNullableString(stream: DataInputStream): String? {
        return if (stream.readBoolean()) stream.readUTF() else null
    }

    private fun writeCreationData(stream: DataOutputStream, data: CreationConfirmationData?) {
        stream.writeBoolean(data != null)
        if (data == null) return
        stream.writeUTF(data.communityName)
        stream.writeUTF(data.communityType)
        stream.writeUTF(data.shapeName)
        stream.writeInt(data.regionNumberId)
        stream.writeUTF(data.creatorUUID.toString())
        stream.writeLong(data.totalCost)
    }

    private fun readCreationData(stream: DataInputStream): CreationConfirmationData? {
        if (!stream.readBoolean()) return null
        return CreationConfirmationData(
            communityName = stream.readUTF(),
            communityType = stream.readUTF(),
            shapeName = stream.readUTF(),
            regionNumberId = stream.readInt(),
            creatorUUID = UUID.fromString(stream.readUTF()),
            totalCost = stream.readLong()
        )
    }

    private fun writeScopeModificationData(stream: DataOutputStream, data: ScopeModificationConfirmationData?) {
        stream.writeBoolean(data != null)
        if (data == null) return
        stream.writeInt(data.regionNumberId)
        stream.writeUTF(data.scopeName)
        stream.writeUTF(data.executorUUID.toString())
        stream.writeLong(data.cost)
        stream.writeBoolean(data.isScopeCreation)
        writeNullableString(stream, data.shapeName)
        stream.writeLong(data.softLimitSurcharge)
        stream.writeBoolean(data.isScopeDeletion)
    }

    private fun readScopeModificationData(stream: DataInputStream): ScopeModificationConfirmationData? {
        if (!stream.readBoolean()) return null
        return ScopeModificationConfirmationData(
            regionNumberId = stream.readInt(),
            scopeName = stream.readUTF(),
            executorUUID = UUID.fromString(stream.readUTF()),
            cost = stream.readLong(),
            isScopeCreation = stream.readBoolean(),
            shapeName = readNullableString(stream),
            softLimitSurcharge = stream.readLong(),
            isScopeDeletion = stream.readBoolean()
        )
    }

    private fun writeTeleportPointData(stream: DataOutputStream, data: TeleportPointConfirmationData?) {
        stream.writeBoolean(data != null)
        if (data == null) return
        stream.writeInt(data.regionNumberId)
        stream.writeUTF(data.scopeName)
        stream.writeUTF(data.executorUUID.toString())
        stream.writeLong(data.cost)
        stream.writeUTF(data.reasonKey)
    }

    private fun readTeleportPointData(stream: DataInputStream): TeleportPointConfirmationData? {
        if (!stream.readBoolean()) return null
        return TeleportPointConfirmationData(
            regionNumberId = stream.readInt(),
            scopeName = stream.readUTF(),
            executorUUID = UUID.fromString(stream.readUTF()),
            cost = stream.readLong(),
            reasonKey = stream.readUTF()
        )
    }

    private fun writeSettingData(stream: DataOutputStream, data: SettingConfirmationData?) {
        stream.writeBoolean(data != null)
        if (data == null) return
        stream.writeInt(data.regionNumberId)
        writeNullableString(stream, data.scopeName)
        stream.writeUTF(data.executorUUID.toString())
        stream.writeUTF(data.permissionKeyStr)
        stream.writeBoolean(data.newValue)
        writeNullableUUID(stream, data.targetPlayerUUID)
        stream.writeLong(data.cost)
        stream.writeBoolean(data.isRuleSetting)
    }

    private fun readSettingData(stream: DataInputStream): SettingConfirmationData? {
        if (!stream.readBoolean()) return null
        return SettingConfirmationData(
            regionNumberId = stream.readInt(),
            scopeName = readNullableString(stream),
            executorUUID = UUID.fromString(stream.readUTF()),
            permissionKeyStr = stream.readUTF(),
            newValue = stream.readBoolean(),
            targetPlayerUUID = readNullableUUID(stream),
            cost = stream.readLong(),
            isRuleSetting = stream.readBoolean()
        )
    }

    private fun writeRenameData(stream: DataOutputStream, data: RenameConfirmationData?) {
        stream.writeBoolean(data != null)
        if (data == null) return
        stream.writeInt(data.regionNumberId)
        stream.writeUTF(data.nameKey)
        stream.writeUTF(data.newName)
        stream.writeUTF(data.executorUUID.toString())
        stream.writeLong(data.cost)
    }

    private fun readRenameData(stream: DataInputStream): RenameConfirmationData? {
        if (!stream.readBoolean()) return null
        return RenameConfirmationData(
            regionNumberId = stream.readInt(),
            nameKey = stream.readUTF(),
            newName = stream.readUTF(),
            executorUUID = UUID.fromString(stream.readUTF()),
            cost = stream.readLong()
        )
    }

    private fun writeScopeTransferData(stream: DataOutputStream, data: ScopeTransferConfirmationData?) {
        stream.writeBoolean(data != null)
        if (data == null) return
        stream.writeInt(data.sourceRegionNumberId)
        stream.writeUTF(data.scopeName)
        stream.writeUTF(data.executorUUID.toString())
        stream.writeInt(data.targetRegionNumberId)
    }

    private fun readScopeTransferData(stream: DataInputStream): ScopeTransferConfirmationData? {
        if (!stream.readBoolean()) return null
        return ScopeTransferConfirmationData(
            sourceRegionNumberId = stream.readInt(),
            scopeName = stream.readUTF(),
            executorUUID = UUID.fromString(stream.readUTF()),
            targetRegionNumberId = stream.readInt()
        )
    }

    private fun writeTreasuryGrantData(stream: DataOutputStream, data: TreasuryGrantConfirmationData?) {
        stream.writeBoolean(data != null)
        if (data == null) return
        stream.writeInt(data.sourceRegionNumberId)
        stream.writeInt(data.targetRegionNumberId)
        stream.writeUTF(data.executorUUID.toString())
        stream.writeLong(data.amount)
    }

    private fun readTreasuryGrantData(stream: DataInputStream): TreasuryGrantConfirmationData? {
        if (!stream.readBoolean()) return null
        return TreasuryGrantConfirmationData(
            sourceRegionNumberId = stream.readInt(),
            targetRegionNumberId = stream.readInt(),
            executorUUID = UUID.fromString(stream.readUTF()),
            amount = stream.readLong()
        )
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
        val entryCount = readCount(stream, "name cooldown community")
        for (i in 0 until entryCount) {
            val regionId = stream.readInt()
            val mapSize = readCount(stream, "name cooldown")
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
        val entryCount = readCount(stream, "likes community")
        for (i in 0 until entryCount) {
            val regionId = stream.readInt()
            val likeCount = requireCount(stream.readInt(), "like")
            val mapSize = readCount(stream, "likes player")
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
        val entryCount = readCount(stream, "community income")
        for (i in 0 until entryCount) {
            val regionId = stream.readInt()
            val income = readTurnoverList(stream)
            getCommunityById(regionId)?.communityIncome = income
        }
    }


    private fun saveCommunityBuildingSection(stream: DataOutputStream) {
        val targets = communities.filter { it.regionNumberId != null && (it.buildingState.stylePackage.isNotEmpty() || it.buildingState.capacityUnits != 12 || it.buildingState.processedHourPeriodIds.isNotEmpty() || it.buildingState.processedWeekPeriodIds.isNotEmpty() || it.buildingState.playerWeekLedgers.isNotEmpty() || it.buildingState.communityWeekLedgers.isNotEmpty() || it.buildingState.pendingPayouts.isNotEmpty()) }
        stream.writeInt(targets.size)
        for (community in targets) writeCommunityBuildingRecord(stream, community)
    }

    private fun writeCommunityBuildingRecord(stream: DataOutputStream, community: Community) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { record ->
            val state = community.buildingState
            record.writeInt(BUILDING_RECORD_VERSION)
            record.writeInt(community.regionNumberId!!)
            record.writeInt(state.capacityUnits)
            record.writeInt(state.stylePackage.size)
            for (entry in state.stylePackage) {
                record.writeUTF(entry.baseBlockId)
                record.writeInt(entry.unitCost)
                record.writeLong(entry.rewardPerBlock)
                record.writeInt(entry.linkedBlockIds.size)
                for (linked in entry.linkedBlockIds) record.writeUTF(linked)
                record.writeLong(entry.templateVersion)
                record.writeUTF(entry.selectionCheckpoint)
                record.writeBoolean(entry.active)
            }
            record.writeInt(state.processedHourPeriodIds.size)
            for (id in state.processedHourPeriodIds) record.writeUTF(id)
            record.writeInt(state.processedWeekPeriodIds.size)
            for (id in state.processedWeekPeriodIds) record.writeUTF(id)
            record.writeInt(state.playerWeekLedgers.size)
            for ((uuid, ledger) in state.playerWeekLedgers) {
                record.writeUTF(uuid.toString())
                record.writeUTF(ledger.weekPeriodId)
                record.writeLong(ledger.settledAmount)
                record.writeLong(ledger.baseCapAmount)
                record.writeLong(ledger.extraCapAmount)
            }
            record.writeInt(state.communityWeekLedgers.size)
            for (ledger in state.communityWeekLedgers) {
                record.writeUTF(ledger.weekPeriodId)
                record.writeLong(ledger.settledAmount)
            }
            record.writeInt(state.pendingPayouts.size)
            for (payout in state.pendingPayouts) {
                record.writeUTF(payout.playerUuid.toString())
                record.writeLong(payout.amount)
                record.writeUTF(payout.hourPeriodId)
                record.writeUTF(payout.weekPeriodId)
                record.writeLong(payout.blockCount)
                record.writeLong(payout.createdAt)
            }
        }
        val payload = buffer.toByteArray()
        require(payload.size <= MAX_COMMUNITY_BYTES) { "Community building record is too large" }
        stream.writeInt(payload.size)
        stream.writeInt(checksum(payload))
        stream.write(payload)
    }

    private fun loadCommunityBuildingSection(stream: DataInputStream) {
        val recordCount = readCount(stream, "community building")
        for (i in 0 until recordCount) {
            val payloadSize = readCount(stream, "community building record bytes")
            if (payloadSize > MAX_COMMUNITY_BYTES) throw IOException("community building record is too large")
            val expectedChecksum = stream.readInt()
            val payload = ByteArray(payloadSize)
            stream.readFully(payload)
            if (checksum(payload) != expectedChecksum) continue
            runCatching { loadCommunityBuildingRecord(payload) }
        }
    }

    private fun loadCommunityBuildingRecord(payload: ByteArray) {
        DataInputStream(ByteArrayInputStream(payload)).use { record ->
            val version = record.readInt()
            if (version !in 1..BUILDING_RECORD_VERSION) return
            val regionId = record.readInt()
            val capacityUnits = record.readInt()
            val styleSize = readCount(record, "community building style")
            val stylePackage = mutableListOf<CommunityBuildingEntry>()
            for (j in 0 until styleSize) {
                val baseBlockId = record.readUTF()
                val unitCost = record.readInt()
                val rewardPerBlock = record.readLong()
                val linkedSize = readCount(record, "community building linked")
                val linked = MutableList(linkedSize) { record.readUTF() }
                val templateVersion = record.readLong()
                val selectionCheckpoint = record.readUTF()
                val active = record.readBoolean()
                stylePackage.add(CommunityBuildingEntry(baseBlockId, unitCost, rewardPerBlock, linked.toMutableList(), templateVersion, selectionCheckpoint, active))
            }
            val hourSize = readCount(record, "community building processed hour")
            val processedHours = MutableList(hourSize) { record.readUTF() }
            val weekSize = readCount(record, "community building processed week")
            val processedWeeks = MutableList(weekSize) { record.readUTF() }
            val ledgerSize = readCount(record, "community building ledger")
            val ledgers = HashMap<UUID, CommunityBuildingWeekLedger>(ledgerSize)
            for (j in 0 until ledgerSize) {
                val uuid = UUID.fromString(record.readUTF())
                val weekId = record.readUTF()
                val settledAmount = record.readLong()
                val baseCapAmount = if (version >= 2) record.readLong() else settledAmount
                val extraCapAmount = if (version >= 2) record.readLong() else 0L
                ledgers[uuid] = CommunityBuildingWeekLedger(weekId, settledAmount, baseCapAmount, extraCapAmount)
            }
            val communityWeekLedgers = mutableListOf<CommunityBuildingCommunityWeekLedger>()
            if (version >= 3) {
                val communityLedgerSize = readCount(record, "community building community week ledger")
                for (j in 0 until communityLedgerSize) {
                    communityWeekLedgers.add(CommunityBuildingCommunityWeekLedger(record.readUTF(), record.readLong()))
                }
            }
            val payoutSize = readCount(record, "community building payout")
            val payouts = mutableListOf<CommunityBuildingPendingPayout>()
            for (j in 0 until payoutSize) {
                payouts.add(
                    CommunityBuildingPendingPayout(
                        playerUuid = UUID.fromString(record.readUTF()),
                        amount = record.readLong(),
                        hourPeriodId = record.readUTF(),
                        weekPeriodId = record.readUTF(),
                        blockCount = record.readLong(),
                        createdAt = record.readLong()
                    )
                )
            }
            val community = getCommunityById(regionId) ?: return
            community.buildingState = CommunityBuildingState(
                capacityUnits = capacityUnits,
                stylePackage = stylePackage,
                processedHourPeriodIds = processedHours,
                processedWeekPeriodIds = processedWeeks,
                playerWeekLedgers = ledgers,
                communityWeekLedgers = communityWeekLedgers,
                pendingPayouts = payouts
            )
        }
    }



    private fun saveCommunityFiscalSection(stream: DataOutputStream) {
        val targets = communities.filter { community ->
            community.regionNumberId != null &&
                (community.fiscalState.activePolicy != CommunityFiscalPolicy.NEOLIBERALISM || community.fiscalState.pendingPolicy != null || community.fiscalState.memberObservations.isNotEmpty() || community.fiscalState.settledWeekKeys.isNotEmpty())
        }
        stream.writeInt(targets.size)
        for (community in targets) {
            val state = community.fiscalState
            stream.writeInt(community.regionNumberId!!)
            stream.writeUTF(state.activePolicy.name)
            stream.writeBoolean(state.pendingPolicy != null)
            state.pendingPolicy?.let { pending ->
                stream.writeUTF(pending.policy.name)
                stream.writeUTF(pending.effectiveWeekKey)
                stream.writeUTF(pending.cooldownUntilWeekKey)
                stream.writeLong(pending.switchedAtMillis)
            }
            stream.writeInt(state.memberObservations.size)
            for ((uuid, observation) in state.memberObservations) {
                stream.writeUTF(uuid.toString())
                stream.writeUTF(observation.weekKey)
                stream.writeLong(observation.firstBalance)
                stream.writeLong(observation.firstObservedAtMillis)
                stream.writeLong(observation.lastBalance)
                stream.writeLong(observation.lastObservedAtMillis)
            }
            stream.writeInt(state.settledWeekKeys.size)
            for (weekKey in state.settledWeekKeys) stream.writeUTF(weekKey)
        }
    }

    private fun loadCommunityFiscalSection(stream: DataInputStream) {
        val communityCount = readCount(stream, "community fiscal")
        for (i in 0 until communityCount) {
            val regionId = stream.readInt()
            val activePolicy = CommunityFiscalPolicy.valueOf(stream.readUTF())
            val pending = if (stream.readBoolean()) {
                CommunityFiscalPolicySwitch(CommunityFiscalPolicy.valueOf(stream.readUTF()), stream.readUTF(), stream.readUTF(), stream.readLong())
            } else null
            val observationCount = readCount(stream, "community fiscal observation")
            val observations = HashMap<UUID, CommunityFiscalObservation>(observationCount)
            for (j in 0 until observationCount) {
                observations[UUID.fromString(stream.readUTF())] = CommunityFiscalObservation(stream.readUTF(), stream.readLong(), stream.readLong(), stream.readLong(), stream.readLong())
            }
            val settledCount = readCount(stream, "community fiscal settled week")
            val settled = mutableSetOf<String>()
            for (j in 0 until settledCount) settled.add(stream.readUTF())
            getCommunityById(regionId)?.fiscalState = CommunityFiscalState(activePolicy, pending, observations, settled)
        }
    }


    private fun saveCommunityDevelopmentSection(stream: DataOutputStream) {
        val targets = communities.filter { community ->
            community.regionNumberId != null &&
                (community.developmentState.weekKey.isNotEmpty() || community.developmentState.updatedAtMillis != 0L || community.developmentState.development != 0.0 || community.developmentState.landPrice != null)
        }
        stream.writeInt(targets.size)
        for (community in targets) {
            val state = community.developmentState
            val inputs = state.inputs
            val breakdown = state.breakdown
            stream.writeInt(community.regionNumberId!!)
            stream.writeUTF(state.weekKey)
            stream.writeLong(state.updatedAtMillis)
            stream.writeDouble(state.development)
            stream.writeInt(inputs.memberCount)
            stream.writeInt(inputs.weekActiveMemberCount)
            stream.writeLong(inputs.totalTheoreticalBuildingIncome)
            stream.writeLong(inputs.weekTheoreticalBuildingIncome)
            stream.writeLong(inputs.totalHabitationMillis)
            stream.writeLong(inputs.averageHabitationMillis)
            stream.writeDouble(breakdown.building)
            stream.writeDouble(breakdown.population)
            stream.writeDouble(breakdown.habitation)
            stream.writeDouble(breakdown.habitationModifier)
            stream.writeBoolean(state.landPrice != null)
            state.landPrice?.let { price ->
                stream.writeLong(price.area)
                stream.writeLong(price.total25HabitationMillis)
                stream.writeLong(price.theoreticalBuildingIncome)
                stream.writeLong(price.activePrice)
                stream.writeLong(price.buildingPrice)
                stream.writeLong(price.totalPrice)
            }
        }
    }

    private fun loadCommunityDevelopmentSection(stream: DataInputStream) {
        val communityCount = readCount(stream, "community development")
        for (i in 0 until communityCount) {
            val regionId = stream.readInt()
            val weekKey = stream.readUTF()
            val updatedAtMillis = stream.readLong()
            val development = stream.readDouble()
            val inputs = CommunityDevelopmentInputs(stream.readInt(), stream.readInt(), stream.readLong(), stream.readLong(), stream.readLong(), stream.readLong())
            val breakdown = CommunityDevelopmentBreakdown(stream.readDouble(), stream.readDouble(), stream.readDouble(), stream.readDouble())
            val landPrice = if (stream.readBoolean()) {
                CommunityLandPriceSnapshot(stream.readLong(), stream.readLong(), stream.readLong(), stream.readLong(), stream.readLong(), stream.readLong())
            } else null
            getCommunityById(regionId)?.developmentState = CommunityDevelopmentState(weekKey, updatedAtMillis, development, inputs, breakdown, landPrice)
        }
    }

    private fun saveCommunityTitlesSection(stream: DataOutputStream) {
        val targets = communities.filter { community ->
            community.regionNumberId != null &&
                (community.titleState.foremanSlots.any { it.index != 0 || it.holderUuid != null || it.cooldownUntilMillis != 0L } || community.titleState.selectedDisplay.isNotEmpty())
        }
        stream.writeInt(targets.size)
        for (community in targets) {
            val state = community.titleState.normalized()
            stream.writeInt(community.regionNumberId!!)
            stream.writeInt(state.foremanSlots.size)
            for (slot in state.foremanSlots) {
                stream.writeInt(slot.index)
                writeNullableUUID(stream, slot.holderUuid)
                stream.writeLong(slot.cooldownUntilMillis)
            }
            stream.writeInt(state.selectedDisplay.size)
            for (uuid in state.selectedDisplay) stream.writeUTF(uuid.toString())
        }
    }

    private fun loadCommunityTitlesSection(stream: DataInputStream) {
        val communityCount = readCount(stream, "community title")
        for (i in 0 until communityCount) {
            val regionId = stream.readInt()
            val slotCount = readCount(stream, "community title slot")
            val slots = mutableListOf<CommunityTitleSlot>()
            for (j in 0 until slotCount) {
                slots.add(CommunityTitleSlot(stream.readInt(), readNullableUUID(stream), stream.readLong()))
            }
            val selectedCount = readCount(stream, "community title selected")
            val selected = mutableSetOf<UUID>()
            for (j in 0 until selectedCount) selected.add(UUID.fromString(stream.readUTF()))
            getCommunityById(regionId)?.titleState = CommunityTitleState(slots, selected).normalized()
        }
    }

    private fun checksum(payload: ByteArray): Int = CRC32().apply { update(payload) }.value.toInt()


    private fun saveCommunityBuildingCatalogSection(stream: DataOutputStream) {
        val entries = CommunityBuildingService.selectablePoolState.sortedBy { it.baseBlockId }
        stream.writeInt(entries.size)
        for (entry in entries) {
            stream.writeUTF(entry.baseBlockId)
            stream.writeInt(entry.unitCost)
            stream.writeLong(entry.rewardPerBlock)
            stream.writeInt(entry.linkedBlockIds.size)
            for (linked in entry.linkedBlockIds) stream.writeUTF(linked)
            stream.writeLong(entry.templateVersion)
        }
    }

    private fun loadCommunityBuildingCatalogSection(stream: DataInputStream) {
        val entryCount = readCount(stream, "community building catalog")
        CommunityBuildingService.selectablePoolState.clear()
        for (i in 0 until entryCount) {
            val baseBlockId = stream.readUTF()
            val unitCost = stream.readInt()
            val rewardPerBlock = stream.readLong()
            val linkedSize = readCount(stream, "community building catalog linked")
            val linked = MutableList(linkedSize) { stream.readUTF() }
            val templateVersion = stream.readLong()
            CommunityBuildingService.selectablePoolState.add(CommunityBuildingCatalogEntry(baseBlockId, unitCost, rewardPerBlock, linked.toMutableList(), templateVersion))
        }
    }

    private fun saveV4StateSection(stream: DataOutputStream) {
        val targets = communities.filter {
            it.regionNumberId != null &&
                (it.treasuryBalance != 0L || it.memberContributionTotals.isNotEmpty() || it.treasuryReferences.isNotEmpty())
        }
        stream.writeInt(targets.size)
        for (community in targets) {
            stream.writeInt(community.regionNumberId!!)
            stream.writeLong(community.treasuryBalance)
            stream.writeInt(community.memberContributionTotals.size)
            for ((uuid, amount) in community.memberContributionTotals) {
                stream.writeUTF(uuid.toString())
                stream.writeLong(amount)
            }
            stream.writeInt(community.treasuryReferences.size)
            for ((reference, record) in community.treasuryReferences) {
                stream.writeUTF(reference)
                stream.writeLong(record.amount)
                stream.writeUTF(record.direction.name)
                stream.writeUTF(record.source)
                stream.writeUTF(record.operationType)
                stream.writeUTF(record.objectReference)
            }
        }
    }

    private fun loadV4StateSection(stream: DataInputStream) {
        val count = readCount(stream, "v4 state community")
        for (i in 0 until count) {
            val regionId = stream.readInt()
            val community = getCommunityById(regionId) ?: continue
            community.treasuryBalance = stream.readLong()
            val contributionCount = readCount(stream, "member contribution")
            val contributions = HashMap<UUID, Long>(contributionCount)
            for (j in 0 until contributionCount) {
                contributions[UUID.fromString(stream.readUTF())] = stream.readLong()
            }
            community.memberContributionTotals = contributions
            val referenceCount = readCount(stream, "treasury reference")
            val references = HashMap<String, com.imyvm.community.domain.model.TreasuryReferenceRecord>(referenceCount)
            for (j in 0 until referenceCount) {
                val reference = stream.readUTF()
                references[reference] = com.imyvm.community.domain.model.TreasuryReferenceRecord(
                    externalReference = reference,
                    amount = stream.readLong(),
                    direction = com.imyvm.community.domain.model.transaction.ResourceDirection.valueOf(stream.readUTF()),
                    source = stream.readUTF(),
                    operationType = stream.readUTF(),
                    objectReference = stream.readUTF()
                )
            }
            community.treasuryReferences = references
        }
    }

    private fun rebuildMissingTreasuryAggregates() {
        communities.forEach { community ->
            if (community.treasuryReferences.isEmpty() && community.treasuryBalance == 0L) {
                community.rebuildTreasuryAggregatesFromLegacy()
            }
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
            val size = readCount(stream, "turnover")
            val list = ArrayList<Turnover>(size)
            for (i in 0 until size) {
                val amount = stream.readLong()
                val timestamp = stream.readLong()
                val source = TurnoverSource.fromValue(stream.readInt())
                val rawDescKey = stream.readUTF()
                val descKey = if (rawDescKey.isEmpty()) null else rawDescKey
                val argCount = readCount(stream, "turnover description argument")
                val args = (0 until argCount).map { stream.readUTF() }
                list.add(Turnover(amount, timestamp, source, descKey, args))
            }
            list
        } else {
            val size = requireCount(firstInt, "legacy turnover")
            val list = ArrayList<Turnover>(size)
            for (i in 0 until size) {
                val amount = stream.readLong()
                val timestamp = stream.readLong()
                list.add(Turnover(amount, timestamp))
            }
            list
        }
    }
}
