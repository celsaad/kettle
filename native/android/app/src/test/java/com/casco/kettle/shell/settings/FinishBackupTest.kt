package com.casco.kettle.shell.settings

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.storage.BackupFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private class Files(
        private val folderUri: String?,
        private val log: SessionLog,
    ) : StoredFiles {
        override fun sessionLog() = log

        override fun library(): Library? = null

        override fun preferences() = Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, true, folderUri)

        override fun deleteSession(id: String) = error("A backup deletes no session")

        override fun writeSession(session: Session) = error("A backup writes no session")

        override fun writePreferences(preferences: Preferences) = error("A backup writes no preferences")
    }

    private companion object {
        const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
    }
}
