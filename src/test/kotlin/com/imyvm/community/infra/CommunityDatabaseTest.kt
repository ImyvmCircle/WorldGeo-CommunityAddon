package com.imyvm.community.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.nio.file.Files
import java.nio.file.Path

class CommunityDatabaseTest {
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
