package com.casco.kettle.shell.runner

import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.RunnerRecovery
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.encodeRunnerSnapshot
import com.casco.kettle.domain.isoInstant
import com.casco.kettle.domain.parseSessionYaml
import com.casco.kettle.domain.serializeSessionYaml
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

    private val swept by lazy { folder.root.resolve("rn-sessions-swept") }

    private fun endedAt(id: String) =
        (parseSessionYaml(sessions.resolve("$id.yaml").readText()) as ParseResult.Ok).value.endedAt

    /** The update past RN's runner (#308): what it left running is ended once, and never again. */
    @Test
    fun `ends a session RN's runner left running, at its last write, once per install`() {
        val rn = start.session.copy(id = "rn", endedAt = null)
        writeSessionFile(sessions, rn)
        sessions.resolve("rn.yaml").setLastModified(T0 + 120_000)

        sweepStrandedSessions(stateDir, sessions, swept)

        assertEquals(isoInstant(T0 + 120_000), endedAt("rn"))
        // Done for good: one left unfinished later is the native runner's prompt's, not this sweep's.
        writeSessionFile(sessions, rn.copy(id = "later"))
        sweepStrandedSessions(stateDir, sessions, swept)
        assertNull(endedAt("later"))
    }

    @Test
    fun `leaves a session the native runner's state file names for its prompt`() {
        killed()

        sweepStrandedSessions(stateDir, sessions, swept)

        assertNull(endedAt(start.session.id))
        assertTrue(find()?.recovery is RunnerRecovery.Prompt)
    }

    /** A read that failed may be a stranded session that reads next time, so the sweep stays due. */
    @Test
    fun `stays due after a session file it couldn't read, ending the rest meanwhile`() {
        writeSessionFile(sessions, start.session.copy(id = "rn", endedAt = null))
        // A directory where a file should be: reading it fails with an IOException, as a bad disk does.
        sessions.resolve("bad.yaml").mkdirs()

        sweepStrandedSessions(stateDir, sessions, swept)

        assertTrue(endedAt("rn") != null)
        assertFalse(swept.exists())
    }

    /** A stamp the disk refused leaves the session running, and the next launch tries it again. */
    @Test
    fun `stays due after a refused write, and ends the session once the disk takes it`() {
        writeSessionFile(sessions, start.session.copy(id = "rn", endedAt = null))
        sessions.setWritable(false)
        try {
            sweepStrandedSessions(stateDir, sessions, swept)

            assertNull(endedAt("rn"))
            assertFalse(swept.exists())
        } finally {
            sessions.setWritable(true)
        }

        sweepStrandedSessions(stateDir, sessions, swept)

        assertTrue(endedAt("rn") != null)
        assertTrue(swept.exists())
    }

    // An id the log can't name a file by never stamps: it is passed over, so the sessions beside it are
    // still ended and the sweep is done, rather than giving up at it on every launch.
    @Test
    fun `passes over a session whose id names no file in the log, ending the rest`() {
        sessions.mkdirs()
        val library = folder.root.resolve("exercises.yaml").apply { writeText("library") }
        val hostile = start.session.copy(id = "../exercises", endedAt = null)
        sessions.resolve("hostile.yaml").writeText(serializeSessionYaml(hostile))
        writeSessionFile(sessions, start.session.copy(id = "rn", endedAt = null))

        sweepStrandedSessions(stateDir, sessions, swept)

        assertTrue(endedAt("rn") != null)
        assertEquals("library", library.readText())
        assertTrue(swept.exists())
    }
}
