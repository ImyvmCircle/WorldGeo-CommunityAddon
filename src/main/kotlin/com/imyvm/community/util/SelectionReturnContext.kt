package com.imyvm.community.util

import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.server.MinecraftServer
import java.util.UUID

object SelectionReturnContext {

    sealed class Context {
        data class CreateScope(val regionNumberId: Int, val scopeName: String) : Context()
        data class ModifyScope(val regionNumberId: Int, val scopeName: String) : Context()
    }

    private val contexts = mutableMapOf<UUID, Context>()

    fun setCreateContext(playerUuid: UUID, regionNumberId: Int, scopeName: String) {
        contexts[playerUuid] = Context.CreateScope(regionNumberId, scopeName)
    }

    fun setModifyContext(playerUuid: UUID, regionNumberId: Int, scopeName: String) {
        contexts[playerUuid] = Context.ModifyScope(regionNumberId, scopeName)
    }

    fun getContext(playerUuid: UUID): Context? = contexts[playerUuid]

    fun clearContext(playerUuid: UUID) {
        contexts.remove(playerUuid)
    }

    fun cleanupStaleContexts(server: MinecraftServer) {
        val selectingPlayers = ImyvmWorldGeo.pointSelectingPlayers.keys
        val stale = contexts.keys.filter { it !in selectingPlayers }
        stale.forEach { contexts.remove(it) }
    }
}
