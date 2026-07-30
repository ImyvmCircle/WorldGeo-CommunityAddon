package com.imyvm.community.entrypoint.command

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyCommandContractTest {
    @Test
    fun commandTreeContainsRequiredOpOperations() {
        val source = Files.readString(Path.of("src/main/kotlin/com/imyvm/community/entrypoint/command/CommandRegister.kt"))
        listOf("money", "test", "credit", "debit", "issues", "issue", "action", "reconcile")
            .forEach { assertTrue(source.contains("literal(\"$it\")")) }
        assertTrue(source.contains("LEVEL_GAMEMASTERS"))
    }

    @Test
    fun languageFilesAreValidAndHaveMatchingMoneyKeys() {
        fun keys(file: String) = JsonParser.parseString(Files.readString(Path.of(file))).asJsonObject.keySet()
            .filter { it.startsWith("command.community.money.") }.toSet()
        val english = keys("src/main/resources/assets/community/lang/en_us.json")
        val chinese = keys("src/main/resources/assets/community/lang/zh_cn.json")
        assertEquals(english, chinese)
        assertTrue(english.isNotEmpty())
    }
}
