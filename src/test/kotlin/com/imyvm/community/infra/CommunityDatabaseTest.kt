package com.imyvm.community.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path

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
