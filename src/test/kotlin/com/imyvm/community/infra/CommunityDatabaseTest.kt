package com.imyvm.community.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.community.Announcement
import com.imyvm.community.domain.model.community.CommunityMessage
import com.imyvm.community.domain.model.community.MessageType
import com.imyvm.community.domain.model.community.CommunityBuildingWeekLedger
import com.imyvm.community.domain.model.community.CommunityBuildingCommunityWeekLedger
import com.imyvm.community.domain.model.community.CommunityBuildingState
import com.imyvm.community.domain.model.community.CommunityBuildingEntry
import net.minecraft.network.chat.Component
import java.util.UUID

class CommunityDatabaseTest {


    @Test
    fun communityBuildingSectionPreservesFrozenTemplateFields() {
        val ownerUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val builderUUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val community = Community(
            regionNumberId = 77,
            member = hashMapOf(ownerUUID to MemberAccount(1L, MemberRoleType.OWNER)),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.ACTIVE_REALM,
            buildingState = CommunityBuildingState(
                capacityUnits = 20,
                stylePackage = mutableListOf(
                    CommunityBuildingEntry(
                        "minecraft:oak_planks",
                        2,
                        150L,
                        mutableListOf("minecraft:oak_stairs"),
                        9L,
                        "2026-08-01T12",
                        false
                    )
                ),
                processedHourPeriodIds = mutableListOf("hour-1"),
                processedWeekPeriodIds = mutableListOf("week-1"),
                playerWeekLedgers = hashMapOf(builderUUID to CommunityBuildingWeekLedger("week-1", 500L, 300L, 200L)),
                communityWeekLedgers = mutableListOf(CommunityBuildingCommunityWeekLedger("week-1", 700L))
            )
        )
        CommunityDatabase.communities = mutableListOf(community)
        val bytes = ByteArrayOutputStream()
        val saveMethod = CommunityDatabase.javaClass.getDeclaredMethod("saveCommunityBuildingSection", DataOutputStream::class.java)
        saveMethod.isAccessible = true
        saveMethod.invoke(CommunityDatabase, DataOutputStream(bytes))

        CommunityDatabase.communities = mutableListOf(
            Community(
                regionNumberId = 77,
                member = hashMapOf(ownerUUID to MemberAccount(1L, MemberRoleType.OWNER)),
                joinPolicy = CommunityJoinPolicy.OPEN,
                status = CommunityStatus.ACTIVE_REALM
            )
        )
        val loadMethod = CommunityDatabase.javaClass.getDeclaredMethod("loadCommunityBuildingSection", DataInputStream::class.java)
        loadMethod.isAccessible = true
        loadMethod.invoke(CommunityDatabase, DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

        val loaded = CommunityDatabase.communities.single().buildingState
        val entry = loaded.stylePackage.single()
        assertEquals(20, loaded.capacityUnits)
        assertEquals("minecraft:oak_planks", entry.baseBlockId)
        assertEquals(listOf("minecraft:oak_stairs"), entry.linkedBlockIds)
        assertEquals(9L, entry.templateVersion)
        assertEquals("2026-08-01T12", entry.selectionCheckpoint)
        assertFalse(entry.active)
        assertEquals(500L, loaded.playerWeekLedgers[builderUUID]?.settledAmount)
        assertEquals(300L, loaded.playerWeekLedgers[builderUUID]?.baseCapAmount)
        assertEquals(200L, loaded.playerWeekLedgers[builderUUID]?.extraCapAmount)
        assertEquals(700L, loaded.communityWeekLedgers.single().settledAmount)
    }

    @Test
    fun writeSectionPrefixesPayloadWithTagAndLength() {
        val bytes = ByteArrayOutputStream()
        val stream = DataOutputStream(bytes)
        val method = CommunityDatabase.javaClass.getDeclaredMethod(
            "writeSection",
            DataOutputStream::class.java,
            Int::class.javaPrimitiveType,
            Function1::class.java
        )
        method.isAccessible = true
        val writer: (DataOutputStream) -> Unit = { sectionStream ->
            sectionStream.writeUTF("payload")
        }
        method.invoke(CommunityDatabase, stream, 99, writer)

        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(-4, input.readInt())
        assertEquals(99, input.readInt())
        val length = input.readInt()
        val payload = input.readNBytes(length)
        assertEquals("payload", DataInputStream(ByteArrayInputStream(payload)).readUTF())
        assertEquals(0, input.available())
    }

    @Test
    fun writeCommunityRecordPrefixesPayloadWithLength() {
        val bytes = ByteArrayOutputStream()
        val stream = DataOutputStream(bytes)
        val method = CommunityDatabase.javaClass.getDeclaredMethod(
            "writeCommunityRecord",
            DataOutputStream::class.java,
            Community::class.java
        )
        method.isAccessible = true
        val ownerUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val community = Community(
            regionNumberId = 123,
            member = hashMapOf(ownerUUID to MemberAccount(
                joinedTime = 456L,
                basicRoleType = MemberRoleType.OWNER
            )),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.RECRUITING_REALM,
            creationCost = 789L
        )
        method.invoke(CommunityDatabase, stream, community)

        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        val length = input.readInt()
        val payload = input.readNBytes(length)
        assertEquals(0, input.available())

        val loadMethod = CommunityDatabase.javaClass.getDeclaredMethod(
            "loadCommunityBody",
            DataInputStream::class.java,
            Int::class.javaPrimitiveType
        )
        loadMethod.isAccessible = true
        val loaded = loadMethod.invoke(
            CommunityDatabase,
            DataInputStream(ByteArrayInputStream(payload)),
            3
        ) as Community

        assertEquals(123, loaded.regionNumberId)
        assertEquals(789L, loaded.creationCost)
        assertEquals(MemberRoleType.OWNER, loaded.member[ownerUUID]?.basicRoleType)
    }

    @Test
    fun writeCommunityRecordPreservesPendingRefunds() {
        val bytes = ByteArrayOutputStream()
        val stream = DataOutputStream(bytes)
        val ownerUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val memberUUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val community = Community(
            regionNumberId = 123,
            member = hashMapOf(
                ownerUUID to MemberAccount(
                    joinedTime = 456L,
                    basicRoleType = MemberRoleType.OWNER
                ),
                memberUUID to MemberAccount(
                    joinedTime = 789L,
                    basicRoleType = MemberRoleType.REFUSED,
                    pendingRefund = 321L
                )
            ),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.RECRUITING_REALM
        )
        val writeMethod = CommunityDatabase.javaClass.getDeclaredMethod(
            "writeCommunityRecord",
            DataOutputStream::class.java,
            Community::class.java
        )
        writeMethod.isAccessible = true
        writeMethod.invoke(CommunityDatabase, stream, community)

        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        val loadMethod = CommunityDatabase.javaClass.getDeclaredMethod(
            "loadCommunityRecord",
            DataInputStream::class.java,
            Int::class.javaPrimitiveType
        )
        loadMethod.isAccessible = true
        val loaded = loadMethod.invoke(CommunityDatabase, input, 4) as Community

        assertEquals(321L, loaded.member[memberUUID]?.pendingRefund)
        assertEquals(0L, loaded.member[ownerUUID]?.pendingRefund)
    }

    @Test
    fun writeCommunityRecordPreservesFrozenJoinFees() {
        val bytes = ByteArrayOutputStream()
        val memberUUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val community = Community(
            regionNumberId = 123,
            member = hashMapOf(memberUUID to MemberAccount(
                joinedTime = 789L,
                basicRoleType = MemberRoleType.APPLICANT,
                joinFeePaid = 321L
            )),
            joinPolicy = CommunityJoinPolicy.APPLICATION,
            status = CommunityStatus.RECRUITING_REALM
        )
        val writeMethod = CommunityDatabase.javaClass.getDeclaredMethod(
            "writeCommunityRecord",
            DataOutputStream::class.java,
            Community::class.java
        ).also { it.isAccessible = true }
        writeMethod.invoke(CommunityDatabase, DataOutputStream(bytes), community)

        val loadMethod = CommunityDatabase.javaClass.getDeclaredMethod(
            "loadCommunityRecord",
            DataInputStream::class.java,
            Int::class.javaPrimitiveType
        ).also { it.isAccessible = true }
        val loaded = loadMethod.invoke(
            CommunityDatabase,
            DataInputStream(ByteArrayInputStream(bytes.toByteArray())),
            4
        ) as Community

        assertEquals(321L, loaded.member[memberUUID]?.joinFeePaid)
    }

    @Test
    fun versionThreeCommunityDefaultsFrozenJoinFeeToZero() {
        val bytes = ByteArrayOutputStream()
        val memberUUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val community = Community(
            regionNumberId = 123,
            member = hashMapOf(memberUUID to MemberAccount(
                joinedTime = 789L,
                basicRoleType = MemberRoleType.APPLICANT
            )),
            joinPolicy = CommunityJoinPolicy.APPLICATION,
            status = CommunityStatus.RECRUITING_REALM
        )
        val saveMethod = CommunityDatabase.javaClass.getDeclaredMethod(
            "saveCommunityBody",
            DataOutputStream::class.java,
            Community::class.java
        ).also { it.isAccessible = true }
        saveMethod.invoke(CommunityDatabase, DataOutputStream(bytes), community)
        val versionThreePayload = bytes.toByteArray().dropLast(Integer.BYTES).toByteArray()

        val loadMethod = CommunityDatabase.javaClass.getDeclaredMethod(
            "loadCommunityBody",
            DataInputStream::class.java,
            Int::class.javaPrimitiveType
        ).also { it.isAccessible = true }
        val loaded = loadMethod.invoke(
            CommunityDatabase,
            DataInputStream(ByteArrayInputStream(versionThreePayload)),
            3
        ) as Community

        assertEquals(0L, loaded.member[memberUUID]?.joinFeePaid)
    }


    @Test
    fun communicationsSectionPreservesCommunicationHistory() {
        val ownerUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val mailTime = 1_000L
        val community = Community(
            regionNumberId = 123,
            member = hashMapOf(ownerUUID to MemberAccount(
                joinedTime = 456L,
                basicRoleType = MemberRoleType.OWNER,
                mail = arrayListOf(Component.literal("mail")),
                mailCreatedAt = arrayListOf(mailTime)
            )),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.RECRUITING_REALM,
            announcements = mutableListOf(Announcement(UUID.randomUUID(), Component.literal("announcement"), ownerUUID, 2_000L)),
            messages = mutableListOf(CommunityMessage(UUID.randomUUID(), MessageType.CHAT, Component.literal("chat"), ownerUUID, 3_000L))
        )
        CommunityDatabase.communities = mutableListOf(community)
        val bytes = ByteArrayOutputStream()
        CommunityDatabase.javaClass.getDeclaredMethod("saveCommunicationsSection", DataOutputStream::class.java)
            .also { it.isAccessible = true }
            .invoke(CommunityDatabase, DataOutputStream(bytes))

        val loaded = Community(
            regionNumberId = 123,
            member = hashMapOf(ownerUUID to MemberAccount(456L, MemberRoleType.OWNER)),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.RECRUITING_REALM,
            announcements = mutableListOf(),
            messages = mutableListOf()
        )
        CommunityDatabase.communities = mutableListOf(loaded)
        CommunityDatabase.javaClass.getDeclaredMethod("loadCommunicationsSection", DataInputStream::class.java)
            .also { it.isAccessible = true }
            .invoke(CommunityDatabase, DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

        assertEquals("mail", loaded.member.getValue(ownerUUID).mail.single().string)
        assertEquals(mailTime, loaded.member.getValue(ownerUUID).mailCreatedAt.single())
        assertEquals("announcement", loaded.announcements.single().content.string)
        assertEquals("chat", loaded.messages.single().content.string)
    }

    @Test
    fun pruneExpiredCommunicationsKeepsOneYearBound() {
        val ownerUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val now = 365L * 24 * 60 * 60 * 1000 + 10L
        val community = Community(
            regionNumberId = 123,
            member = hashMapOf(ownerUUID to MemberAccount(
                456L,
                MemberRoleType.OWNER,
                mail = arrayListOf(Component.literal("old"), Component.literal("new")),
                mailCreatedAt = arrayListOf(0L, 10L)
            )),
            joinPolicy = CommunityJoinPolicy.OPEN,
            status = CommunityStatus.RECRUITING_REALM,
            announcements = mutableListOf(
                Announcement(UUID.randomUUID(), Component.literal("old"), ownerUUID, 0L),
                Announcement(UUID.randomUUID(), Component.literal("new"), ownerUUID, 10L)
            ),
            messages = mutableListOf(
                CommunityMessage(UUID.randomUUID(), MessageType.CHAT, Component.literal("old"), ownerUUID, 0L),
                CommunityMessage(UUID.randomUUID(), MessageType.CHAT, Component.literal("new"), ownerUUID, 10L)
            )
        )
        CommunityDatabase.communities = mutableListOf(community)
        CommunityDatabase.javaClass.getDeclaredMethod("pruneExpiredCommunications", Long::class.javaPrimitiveType)
            .also { it.isAccessible = true }
            .invoke(CommunityDatabase, now)

        assertEquals(listOf("new"), community.member.getValue(ownerUUID).mail.map { it.string })
        assertEquals(listOf(10L), community.member.getValue(ownerUUID).mailCreatedAt)
        assertEquals(listOf("new"), community.announcements.map { it.content.string })
        assertEquals(listOf("new"), community.messages.map { it.content.string })
    }

    @Test
    fun loadCommunityRecordRejectsTruncatedPayload() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            stream.writeInt(8)
            stream.writeInt(1)
        }
        val method = CommunityDatabase.javaClass.getDeclaredMethod(
            "loadCommunityRecord",
            DataInputStream::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        assertFailsWith<java.lang.reflect.InvocationTargetException> {
            method.invoke(CommunityDatabase, DataInputStream(ByteArrayInputStream(bytes.toByteArray())), 3)
        }
    }


    @Test
    fun backupDatabaseFileCopiesCorruptDatabase() {
        val dir = Files.createTempDirectory("community-db-backup-test")
        try {
            val database = dir.resolve("iwg_community.db")
            Files.writeString(database, "corrupt")
            val method = CommunityDatabase.javaClass.getDeclaredMethod(
                "backupDatabaseFile",
                Path::class.java,
                String::class.java,
                Long::class.javaPrimitiveType
            )
            method.isAccessible = true

            val backup = method.invoke(CommunityDatabase, database, "corrupt", 1234L) as Path

            assertEquals("iwg_community.db.corrupt.1234", backup.fileName.toString())
            assertEquals("corrupt", Files.readString(backup))
        } finally {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }


    @Test
    fun backupDatabaseFileCreatesSuffixWhenBackupNameExists() {
        val dir = Files.createTempDirectory("community-db-backup-suffix-test")
        try {
            val database = dir.resolve("iwg_community.db")
            Files.writeString(database, "new-copy")
            Files.writeString(dir.resolve("iwg_community.db.corrupt.55"), "old-copy")
            val method = CommunityDatabase.javaClass.getDeclaredMethod(
                "backupDatabaseFile",
                Path::class.java,
                String::class.java,
                Long::class.javaPrimitiveType
            )
            method.isAccessible = true

            val backup = method.invoke(CommunityDatabase, database, "corrupt", 55L) as Path

            assertEquals("iwg_community.db.corrupt.55.1", backup.fileName.toString())
            assertEquals("new-copy", Files.readString(backup))
            assertEquals("old-copy", Files.readString(dir.resolve("iwg_community.db.corrupt.55")))
        } finally {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }


    @Test
    fun backupLegacyDatabaseBeforeSaveCopiesOnlyOnce() {
        val dir = Files.createTempDirectory("community-db-legacy-backup-test")
        try {
            val database = dir.resolve("iwg_community.db")
            Files.writeString(database, "legacy")
            val loadedField = CommunityDatabase.javaClass.getDeclaredField("legacyDatabaseLoaded")
            val createdField = CommunityDatabase.javaClass.getDeclaredField("legacyBackupCreated")
            loadedField.isAccessible = true
            createdField.isAccessible = true
            loadedField.setBoolean(CommunityDatabase, true)
            createdField.setBoolean(CommunityDatabase, false)

            val method = CommunityDatabase.javaClass.getDeclaredMethod(
                "backupLegacyDatabaseBeforeSave",
                Path::class.java
            )
            method.isAccessible = true

            val firstBackup = method.invoke(CommunityDatabase, database) as Path
            val secondBackup = method.invoke(CommunityDatabase, database)

            assertEquals(true, firstBackup.fileName.toString().startsWith("iwg_community.db.legacy."))
            assertEquals("legacy", Files.readString(firstBackup))
            assertEquals(null, secondBackup)
        } finally {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }


    @Test
    fun backupLegacyDatabaseBeforeLoadCopiesRawDatabaseAndSkipsSecondLegacyBackup() {
        val dir = Files.createTempDirectory("community-db-legacy-load-backup-test")
        try {
            val database = dir.resolve("iwg_community.db")
            Files.writeString(database, "legacy-load")
            val createdField = CommunityDatabase.javaClass.getDeclaredField("legacyBackupCreated")
            createdField.isAccessible = true
            createdField.setBoolean(CommunityDatabase, false)

            val loadBackupMethod = CommunityDatabase.javaClass.getDeclaredMethod(
                "backupLegacyDatabaseBeforeLoad",
                Path::class.java
            )
            loadBackupMethod.isAccessible = true
            val saveBackupMethod = CommunityDatabase.javaClass.getDeclaredMethod(
                "backupLegacyDatabaseBeforeSave",
                Path::class.java
            )
            saveBackupMethod.isAccessible = true

            val firstBackup = loadBackupMethod.invoke(CommunityDatabase, database) as Path
            val secondBackup = saveBackupMethod.invoke(CommunityDatabase, database)

            assertEquals(true, firstBackup.fileName.toString().startsWith("iwg_community.db.legacy."))
            assertEquals("legacy-load", Files.readString(firstBackup))
            assertEquals(null, secondBackup)
        } finally {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun loadDatabaseFileWithoutTrailingSectionsClearsPendingOperations() {
        val dir = Files.createTempDirectory("community-db-load-empty-test")
        try {
            val database = dir.resolve("iwg_community.db")
            DataOutputStream(Files.newOutputStream(database)).use { stream ->
                stream.writeInt(-3)
                stream.writeInt(3)
                stream.writeInt(0)
            }
            WorldGeoCommunityAddon.pendingOperations.clear()
            WorldGeoCommunityAddon.pendingOperations[77L] = PendingOperation(
                expireAt = 123L,
                type = PendingOperationType.INVITATION
            )
            CommunityDatabase.communities = mutableListOf(Community(
                regionNumberId = 7,
                member = hashMapOf(),
                joinPolicy = CommunityJoinPolicy.OPEN,
                status = CommunityStatus.RECRUITING_REALM
            ))
            val method = CommunityDatabase.javaClass.getDeclaredMethod("loadDatabaseFile", Path::class.java)
            method.isAccessible = true

            method.invoke(CommunityDatabase, database)

            assertEquals(0, CommunityDatabase.communities.size)
            assertTrue(WorldGeoCommunityAddon.pendingOperations.isEmpty())
        } finally {
            WorldGeoCommunityAddon.pendingOperations.clear()
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun loadDatabaseFileRestoresPreviousStateWhenRecordReadFails() {
        val dir = Files.createTempDirectory("community-db-load-rollback-test")
        try {
            val database = dir.resolve("iwg_community.db")
            DataOutputStream(Files.newOutputStream(database)).use { stream ->
                stream.writeInt(-3)
                stream.writeInt(3)
                stream.writeInt(1)
                stream.writeInt(16)
                stream.writeInt(1)
            }
            val previousCommunity = Community(
                regionNumberId = 88,
                member = hashMapOf(),
                joinPolicy = CommunityJoinPolicy.OPEN,
                status = CommunityStatus.RECRUITING_REALM
            )
            CommunityDatabase.communities = mutableListOf(previousCommunity)
            WorldGeoCommunityAddon.pendingOperations.clear()
            WorldGeoCommunityAddon.pendingOperations[77L] = PendingOperation(
                expireAt = 123L,
                type = PendingOperationType.INVITATION
            )
            val method = CommunityDatabase.javaClass.getDeclaredMethod("loadDatabaseFile", Path::class.java)
            method.isAccessible = true

            assertFailsWith<java.lang.reflect.InvocationTargetException> {
                method.invoke(CommunityDatabase, database)
            }

            assertEquals(listOf(previousCommunity), CommunityDatabase.communities)
            assertEquals(setOf(77L), WorldGeoCommunityAddon.pendingOperations.keys)
        } finally {
            WorldGeoCommunityAddon.pendingOperations.clear()
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }


    @Test
    fun loadPendingOperationsDoesNotPublishPartialStateOnStrictFailure() {
        WorldGeoCommunityAddon.pendingOperations.clear()
        WorldGeoCommunityAddon.pendingOperations[77L] = PendingOperation(
            expireAt = 123L,
            type = PendingOperationType.INVITATION
        )
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            stream.writeInt(-2)
            stream.writeInt(3)
            stream.writeInt(1)
        }
        val method = CommunityDatabase.javaClass.getDeclaredMethod(
            "loadPendingOperations",
            DataInputStream::class.java,
            Int::class.javaObjectType,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true

        try {
            assertFailsWith<java.lang.reflect.InvocationTargetException> {
                method.invoke(
                    CommunityDatabase,
                    DataInputStream(ByteArrayInputStream(bytes.toByteArray())),
                    null,
                    true
                )
            }
            assertEquals(setOf(77L), WorldGeoCommunityAddon.pendingOperations.keys)
        } finally {
            WorldGeoCommunityAddon.pendingOperations.clear()
        }
    }


    @Test
    fun loadLegacyTrailingSectionsRecordsSectionFailures() {
        CommunityDatabase.communities = mutableListOf()
        val warningsField = CommunityDatabase.javaClass.getDeclaredField("legacyLoadWarnings")
        warningsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (warningsField.get(CommunityDatabase) as MutableList<String>).clear()

        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            stream.writeInt(1)
            stream.writeInt(123)
            stream.writeInt(-1)
        }
        val method = CommunityDatabase.javaClass.getDeclaredMethod(
            "loadLegacyTrailingSections",
            DataInputStream::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(CommunityDatabase, DataInputStream(ByteArrayInputStream(bytes.toByteArray())), 0)

        val warnings = CommunityDatabase.getLegacyLoadWarnings()
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().contains("name change cooldowns"))
    }

    @Test
    fun readTurnoverListRejectsInvalidLegacyCount() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            stream.writeInt(-99)
        }
        val method = CommunityDatabase.javaClass.getDeclaredMethod(
            "readTurnoverList",
            DataInputStream::class.java
        )
        method.isAccessible = true

        assertFailsWith<java.lang.reflect.InvocationTargetException> {
            method.invoke(CommunityDatabase, DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        }
    }


    @Test
    fun replaceDatabaseFilePublishesCompleteTempFile() {
        val dir = Files.createTempDirectory("community-db-test")
        try {
            val target = dir.resolve("iwg_community.db")
            val temp = dir.resolve("iwg_community.db.tmp")
            Files.writeString(target, "old")
            Files.writeString(temp, "new")

            val method = CommunityDatabase.javaClass.getDeclaredMethod(
                "replaceDatabaseFile",
                Path::class.java,
                Path::class.java
            )
            method.isAccessible = true
            method.invoke(CommunityDatabase, temp, target)

            assertEquals("new", Files.readString(target))
            assertFalse(Files.exists(temp))
        } finally {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }
}
