package com.imyvm.community.infra.account

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.account.AccountOperatorService
import com.imyvm.community.application.account.AccountTransactionService
import com.imyvm.community.domain.model.account.PlayerIdentity
import com.imyvm.community.infra.economy.EconomyWalletAdapter
import com.imyvm.community.infra.transaction.CommunityFactStore
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor

object AccountSubsystem {
    private val lock = Any()
    private val pendingIdentities = LinkedHashMap<UUID, String>()
    private val readyListeners = mutableListOf<(Runtime) -> Unit>()
    @Volatile private var runtime: Runtime? = null
    @Volatile private var starting = false

    fun start(server: MinecraftServer) {
        synchronized(lock) {
            if (runtime != null || starting) return
            starting = true
        }
        Thread({ initialize(server) }, "community-account-bootstrap").apply { isDaemon = true }.start()
    }

    fun stop() {
        val current = synchronized(lock) {
            starting = false
            runtime.also { runtime = null }
        } ?: return
        current.scheduler.shutdownNow()
        Thread({ current.writer.close() }, "community-account-shutdown").start()
    }

    fun captureIdentity(player: ServerPlayer) {
        val current = runtime
        if (current == null) {
            synchronized(lock) {
                if (pendingIdentities.size >= MAX_PENDING_IDENTITIES) {
                    pendingIdentities.remove(pendingIdentities.keys.first())
                }
                pendingIdentities[player.uuid] = player.gameProfile.name
            }
            return
        }
        saveIdentity(current, player.uuid, player.gameProfile.name)
    }

    fun runtimeOrNull(): Runtime? = runtime

    fun onReady(listener: (Runtime) -> Unit) {
        val current = synchronized(lock) {
            check(readyListeners.size < MAX_READY_LISTENERS) { "Too many account ready listeners" }
            readyListeners += listener
            runtime
        }
        if (current != null) current.server.execute { listener(current) }
    }

    private fun initialize(server: MinecraftServer) {
        val writer = CommunityDataWriter(WRITER_QUEUE_CAPACITY)
        var scheduler: ScheduledThreadPoolExecutor? = null
        try {
            val root = server.getWorldPath(LevelResource.ROOT).resolve("community-account")
            val identities = PlayerIdentityDirectory(root.resolve("identities"))
            val store = AccountTransactionStore(root.resolve("transactions"), writer)
            val sharedStore = CommunityFactStore(server.getWorldPath(LevelResource.ROOT).resolve("community-shared"), writer)
            scheduler = ScheduledThreadPoolExecutor(1) { task ->
                Thread(task, "community-account-retry").apply { isDaemon = true }
            }.apply { removeOnCancelPolicy = true }
            val audit = AccountAuditLog(root.resolve("account-audit.log"), writer)
            val service = AccountTransactionService(
                server,
                store,
                writer,
                identities,
                EconomyWalletAdapter(),
                scheduler
            )
            val operator = AccountOperatorService(
                server, store, writer, identities, EconomyWalletAdapter(), audit, service
            )
            val created = Runtime(server, writer, store, sharedStore, identities, service, operator, audit, scheduler)
            var accepted = true
            val listeners = synchronized(lock) {
                if (!starting) {
                    accepted = false
                    emptyList()
                } else {
                    runtime = created
                    starting = false
                    readyListeners.toList()
                }
            }
            if (!accepted) {
                scheduler.shutdownNow()
                writer.close()
                return
            }
            val queued = synchronized(lock) {
                pendingIdentities.toMap().also { pendingIdentities.clear() }
            }
            queued.forEach { (uuid, name) -> saveIdentity(created, uuid, name) }
            server.execute {
                server.playerList.players.forEach(::captureIdentity)
                service.recover()
                listeners.forEach { listener ->
                    try {
                        listener(created)
                    } catch (error: Throwable) {
                        WorldGeoCommunityAddon.logger.error("Account ready listener failed", error)
                    }
                }
                WorldGeoCommunityAddon.logger.info("Community account subsystem ready")
            }
        } catch (error: Throwable) {
            synchronized(lock) { starting = false }
            scheduler?.shutdownNow()
            writer.close()
            WorldGeoCommunityAddon.logger.error("Failed to initialize community account subsystem", error)
        }
    }

    private fun saveIdentity(current: Runtime, uuid: UUID, name: String) {
        current.writer.submit {
            current.identities.save(PlayerIdentity(uuid, name, System.currentTimeMillis()))
        }.whenComplete { _, error ->
            if (error != null) {
                WorldGeoCommunityAddon.logger.error("Failed to persist player identity for $uuid", error)
            } else {
                current.service.identityAvailable(uuid)
            }
        }
    }

    data class Runtime(
        val server: MinecraftServer,
        val writer: CommunityDataWriter,
        val store: AccountTransactionStore,
        val sharedStore: CommunityFactStore,
        val identities: PlayerIdentityDirectory,
        val service: AccountTransactionService,
        val operator: AccountOperatorService,
        val audit: AccountAuditLog,
        val scheduler: ScheduledThreadPoolExecutor
    )

    private const val WRITER_QUEUE_CAPACITY = 512
    private const val MAX_PENDING_IDENTITIES = 1024
    private const val MAX_READY_LISTENERS = 16
}
