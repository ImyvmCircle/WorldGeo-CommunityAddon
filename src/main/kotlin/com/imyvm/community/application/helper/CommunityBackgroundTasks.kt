package com.imyvm.community.application.helper

import java.util.concurrent.CompletableFuture
import com.imyvm.community.WorldGeoCommunityAddon
import java.util.concurrent.atomic.AtomicInteger

object CommunityBackgroundTasks {
    private const val QUEUE_CAPACITY = 8
    private val queuedTasks = AtomicInteger()

    fun <T> supply(task: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val server = WorldGeoCommunityAddon.server
            ?: return CompletableFuture.failedFuture(IllegalStateException("Minecraft server is unavailable"))
        if (queuedTasks.incrementAndGet() > QUEUE_CAPACITY) {
            queuedTasks.decrementAndGet()
            return CompletableFuture.failedFuture(IllegalStateException("Community task queue is full"))
        }
        server.execute {
            try {
                future.complete(task())
            } catch (error: Throwable) {
                future.completeExceptionally(error)
            } finally {
                queuedTasks.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            }
        }
        return future
    }

    fun stop() {
        queuedTasks.set(0)
    }
}
