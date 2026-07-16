package com.imyvm.community.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import java.util.UUID

class CommunityDatabaseTest {

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
