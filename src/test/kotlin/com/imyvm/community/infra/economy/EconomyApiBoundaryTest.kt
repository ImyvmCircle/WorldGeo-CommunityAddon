package com.imyvm.community.infra.economy

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EconomyApiBoundaryTest {
    @Test
    fun productionUsesOnlyTheOfficialWalletAdapterBoundary() {
        val sourceRoot = Path.of("src/main/kotlin")
        val kotlinFiles = Files.walk(sourceRoot).use { paths ->
            paths.filter { it.extension == "kt" }.toList()
        }
        assertFalse(kotlinFiles.any { Files.readString(it).contains("EconomyMod.data") })
        val officialApiUsers = kotlinFiles.filter {
            Files.readString(it).contains("com.imyvm.economy.api")
        }
        assertTrue(officialApiUsers.single().endsWith("EconomyWalletAdapter.kt"))
    }

    @Test
    fun runtimeDependenciesDeclareRequiredMods() {
        val metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"))
        assertTrue(metadata.contains("\"hoki\": \">=1.1.6\""))
        assertTrue(metadata.contains("\"imyvm_economy\": \">=26.2-1.2.7\""))
        assertTrue(metadata.contains("\"imyvmworldgeo\": \">=26.2-1.5.6\""))
    }
}
