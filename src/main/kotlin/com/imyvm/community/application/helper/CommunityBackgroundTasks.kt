package com.imyvm.community.application.helper

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object CommunityBackgroundTasks {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "WorldGeo Community Background").also { it.isDaemon = true }
    }

    fun <T> supply(task: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync(task, executor)
}
