package com.casco.kettle.shell.settings

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.storage.BackupFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * The runner's finish backup (#220), which `session-history-store.test.ts`'s "backing up when a
 * session finishes" cases hand over to native: whether it runs, what it backs up, and the answer RN's
 * completion screen warns from. The backup itself is `:shared`'s `BackupTest`.
 */
class FinishBackupTest {
    private val finished =
        Session(1, "legs", "w", null, null, null, "2026-07-01T09:00:00.000Z", "2026-07-01T09:40:00.000Z", emptyList())
    private val calls = mutableListOf<Pair<String, List<Session>>>()

    private fun finish(
        folderUri: String?,
        answer: BackupFailure? = null,
    ): Boolean? =
        finishBackup(Files(folderUri, SessionLog(listOf(finished), emptyList()))) { uri, sessions ->
            calls += uri to sessions
            answer
        }

    // Someone who never opted in has nothing to be told, and answering at all would light a warning on
    // every completion screen in the app.
    @Test
    fun `takes no backup and gives no answer when no folder is set`() {
        assertNull(finish(folderUri = null))
        assertEquals(emptyList<Pair<String, List<Session>>>(), calls)
    }

    // From the disk, which RN wrote before asking, so the session that just ended is in it, finished.
    @Test
    fun `backs the log on disk up into the folder the preferences name`() {
        assertFalse(finish(FOLDER)!!)
        assertEquals(listOf(FOLDER to listOf(finished)), calls)
    }

    @Test
    fun `answers failed where the folder couldn't be written`() {
        assertTrue(finish(FOLDER, BackupFailure.Unreachable)!!)
    }

    /**
     * The archive is the whole log, so the backup that writes last decides what the folder holds. One
     * that read the log before waiting its turn could write it over a newer one, losing a session
     * until the next backup: the read waits with the write.
     */
    @Test
    fun `reads the log only once the backup ahead of it has finished`() {
        val ahead = CountDownLatch(1)
        val aheadMayFinish = CountDownLatch(1)
        val files = Files(FOLDER, SessionLog(listOf(finished), emptyList()))
        val first =
            thread {
                oneBackupAtATime {
                    ahead.countDown()
                    aheadMayFinish.await(5, TimeUnit.SECONDS)
                }
            }
        ahead.await(5, TimeUnit.SECONDS)

        val second = thread { finishBackup(files) { _, _ -> null } }
        // Long enough for a read outside the lock to have happened, which is the failure this pins.
        second.join(200)
        assertEquals(0, files.reads.get())

        aheadMayFinish.countDown()
        first.join()
        second.join()
        assertEquals(1, files.reads.get())
    }

    // The real backUp takes the same lock again, from inside this one: a lock that couldn't be
    // re-entered would hang every finish backup.
    @Test(timeout = 5_000)
    fun `runs a backup that takes the lock itself`() {
        val files = Files(FOLDER, SessionLog(listOf(finished), emptyList()))

        assertFalse(finishBackup(files) { _, _ -> oneBackupAtATime { null } }!!)
    }

    private class Files(
        private val folderUri: String?,
        private val log: SessionLog,
    ) : FakeStoredFiles() {
        val reads = AtomicInteger()

        override fun sessionLog() = log.also { reads.incrementAndGet() }

        override fun preferences() = Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, true, folderUri)
    }

    private companion object {
        const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
    }
}
