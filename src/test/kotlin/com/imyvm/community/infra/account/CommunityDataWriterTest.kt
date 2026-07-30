package com.imyvm.community.infra.account

import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommunityDataWriterTest {
    @Test
    fun rejectsWorkBeyondItsHardQueueCapacity() {
        val writer = CommunityDataWriter(1)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val running = writer.submit {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val queued = writer.submit { Unit }
            val rejected = writer.submit { Unit }
            assertFailsWith<CompletionException> { rejected.join() }
            release.countDown()
            running.join()
            queued.join()
        } finally {
            release.countDown()
            writer.close()
        }
    }
}
