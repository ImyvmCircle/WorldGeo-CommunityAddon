package com.imyvm.community.infra.account

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class CommunityDataWriter(queueCapacity: Int) : AutoCloseable {
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queueCapacity),
        { task -> Thread(task, "community-data-writer").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    fun <T> submit(action: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        try {
            executor.execute {
                try {
                    result.complete(action())
                } catch (error: Throwable) {
                    result.completeExceptionally(error)
                }
            }
        } catch (error: RejectedExecutionException) {
            result.completeExceptionally(error)
        }
        return result
    }

    override fun close() {
        executor.shutdown()
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow()
    }
}
