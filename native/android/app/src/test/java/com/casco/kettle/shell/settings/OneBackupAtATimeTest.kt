package com.casco.kettle.shell.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Settings' Back up now and the runner's finish backup both write the same documents and
 * `backup-targets.json`, and either can start while the other is writing (#238's review): the second
 * waits for the first rather than writing beside it.
 */
class OneBackupAtATimeTest {
    @Test
    fun `a second backup waits until the first has finished`() {
        val firstWriting = CountDownLatch(1)
        val firstMayFinish = CountDownLatch(1)
        val order = mutableListOf<String>()

        val first =
            thread {
                oneBackupAtATime {
                    firstWriting.countDown()
                    firstMayFinish.await(5, TimeUnit.SECONDS)
                    synchronized(order) { order += "first done" }
                }
            }
        firstWriting.await(5, TimeUnit.SECONDS)
        val second = thread { oneBackupAtATime { synchronized(order) { order += "second" } } }

        // Long enough for an unlocked second backup to have run, which is the failure this pins.
        second.join(200)
        assertFalse(synchronized(order) { "second" in order })

        firstMayFinish.countDown()
        first.join()
        second.join()
        assertEquals(listOf("first done", "second"), order)
    }
}
