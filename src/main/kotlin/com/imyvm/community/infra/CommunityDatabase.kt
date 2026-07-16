package com.imyvm.community.infra

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
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.AdminPrivileges
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*

object CommunityDatabase {

    private const val DATABASE_FILENAME = "iwg_community.db"
    private const val DATABASE_VERSION_MARKER = -3
    private const val DATABASE_VERSION = 3
    private const val PENDING_SECTION_VERSION_MARKER = -2
    private const val PENDING_SECTION_VERSION = 3
    private const val SECTION_FRAME_MARKER = -4
    private const val SECTION_PENDING_OPERATIONS = 1
    private const val SECTION_NAME_CHANGE_COOLDOWNS = 2
    private const val SECTION_LIKES = 3
    private const val SECTION_COMMUNITY_INCOME = 4
    private const val MAX_SECTION_BYTES = 16 * 1024 * 1024
    private const val MAX_COMMUNITY_BYTES = 16 * 1024 * 1024
    private var legacyDatabaseLoaded = false
    private var legacyBackupCreated = false
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

    @Throws(IOException::class)
    fun load() {
        val file = this.getDatabasePath()
        legacyDatabaseLoaded = false
        legacyBackupCreated = false
        if (!file.toFile().exists()) {
            communities = mutableListOf()
            return
        }

        DataInputStream(file.toFile().inputStream()).use { stream ->
            val firstInt = stream.readInt()
            val databaseVersion = if (firstInt == DATABASE_VERSION_MARKER) stream.readInt() else 1
            require(databaseVersion in 1..DATABASE_VERSION) { "Unsupported community database version: $databaseVersion" }
            legacyDatabaseLoaded = databaseVersion < DATABASE_VERSION
            val size = if (databaseVersion == 1) firstInt else stream.readInt()
            communities = ArrayList(size)
            for (i in 0 until size) {
                communities.add(loadCommunityRecord(stream, databaseVersion))
            }

            if (stream.available() > 0) {
                loadTrailingSections(stream)
            }
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
        val creationCost = if (databaseVersion >= 2) stream.readLong() else 0L

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

    private fun backupLegacyDatabaseBeforeSave(databaseFile: Path): Path? {
        if (!legacyDatabaseLoaded || legacyBackupCreated) return null
        val backupFile = backupDatabaseFile(databaseFile, "legacy", System.currentTimeMillis())
        legacyBackupCreated = backupFile != null
        return backupFile
    }

    private fun backupDatabaseFile(databaseFile: Path, label: String, timestamp: Long): Path? {
        if (!Files.exists(databaseFile)) return null
        val backupFile = databaseFile.resolveSibling("${databaseFile.fileName}.$label.$timestamp")
        return Files.copy(databaseFile, backupFile)
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

    private fun loadCommunityExpenditures(stream: DataInputStream, strict: Boolean): ArrayList<Turnover> {
        return try {
            readTurnoverList(stream)
        } catch (e: Exception) {
            if (strict) throw e
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

    private fun loadCommunityMessages(stream: DataInputStream, strict: Boolean): MutableList<CommunityMessage> {
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
            val size = if (version == 1) firstInt else stream.readInt()
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.clear()

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
                com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[operationKey] = operation
            }
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
