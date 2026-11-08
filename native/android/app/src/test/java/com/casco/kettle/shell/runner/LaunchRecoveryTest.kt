package com.casco.kettle.shell.runner

import com.casco.kettle.domain.RunnerRecovery
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.encodeRunnerSnapshot
import com.casco.kettle.domain.isoInstant
import com.casco.kettle.storage.writeSessionFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * What a launch finds after process death (#307): the state files the service left, read against the
 * session files they name, and a Finish from the prompt. Robolectric for `Log`.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchRecoveryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val stateDir by lazy { folder.root.resolve("runner") }
    private val sessions by lazy { folder.root.resolve("sessions") }

    private val start = started(PULLUPS).state

    /** A session the process died under: its file and its state file, both as the service left them. */
    private fun killed(
        session: Session = start.session,
        state: String = encodeRunnerSnapshot(start.snapshot.copy(sessionId = session.id)),
    ) {
        writeSessionFile(sessions, session)
        stateDir.mkdirs()
        stateDir.resolve("${session.id}.json").writeText(state)
    }

    private fun find() = findRunnerRecovery(stateDir, sessions)

    @Test
    fun `finds nothing where no state file was left`() {
        assertNull(find())
        stateDir.mkdirs()
        assertNull(find())
    }

    @Test
    fun `offers Resume or Finish for a snapshot that fits its session`() {
        killed()

        val found = find()
        val prompt = found?.recovery as RunnerRecovery.Prompt
        assertEquals(start.snapshot, prompt.snapshot)
        assertEquals(stateDir.resolve("${start.session.id}.json"), found.stateFile)
    }

    @Test
    fun `offers Finish only for a corrupt snapshot, ending it at the session file's last write`() {
        killed(state = "{")
        val file = sessions.resolve("${start.session.id}.yaml")
        file.setLastModified(T0 + 120_000)

        val recovery = find()?.recovery as RunnerRecovery.FinishOnly
        assertEquals(isoInstant(file.lastModified()), recovery.finished.endedAt)
    }

    @Test
    fun `deletes the state file silently when its session file is gone`() {
        killed()
        sessions.resolve("${start.session.id}.yaml").delete()

        assertNull(find())
        assertTrue(stateDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `deletes the state file silently when its session already finished`() {
        killed(start.session.copy(endedAt = isoInstant(T0 + 60_000)))

        assertNull(find())
        assertTrue(stateDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `keeps the state file, and offers nothing, when the session file can't be read`() {
        killed()
        val file = sessions.resolve("${start.session.id}.yaml")
        file.delete()
        file.mkdirs() // there, but reading it fails

        assertNull(find())
        assertEquals(1, stateDir.listFiles().orEmpty().size)
    }

    @Test
    fun `reads past a state file it deletes to the next one`() {
        killed(start.session.copy(id = "a-gone"))
        sessions.resolve("a-gone.yaml").delete()
        killed(start.session.copy(id = "b-kept"))

        assertEquals("b-kept", (find()?.recovery as RunnerRecovery.Prompt).session.id)
        assertFalse(stateDir.resolve("a-gone.json").exists())
    }

    @Test
    fun `ignores a state file's half-written temporary`() {
        stateDir.mkdirs()
        stateDir.resolve("${start.session.id}.json.partial").writeText("{")
        writeSessionFile(sessions, start.session)

        assertNull(find())
    }

    @Test
    fun `finishes a session from the prompt, then deletes its state file`() {
        killed()
        val found = checkNotNull(find())
        val written = mutableListOf<Session>()

        val refused = runBlocking { finishRecovered(found) { written += it } }

        assertNull(refused)
        assertEquals(listOf(found.recovery.finished), written)
        assertEquals(isoInstant(start.snapshot.updatedAt), written.single().endedAt)
        assertFalse(found.stateFile.exists())
    }

    @Test
    fun `keeps the state file when the disk refuses the finish, and says why`() {
        killed()
        val found = checkNotNull(find())

        val refused = runBlocking { finishRecovered(found) { throw IOException("disk full") } }

        assertEquals("disk full", refused)
        assertTrue(found.stateFile.exists())
    }
}
