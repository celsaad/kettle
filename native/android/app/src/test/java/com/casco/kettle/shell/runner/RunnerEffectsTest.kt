package com.casco.kettle.shell.runner

import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerCue
import com.casco.kettle.domain.RunnerEffect
import com.casco.kettle.domain.RunnerSnapshot
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SnapshotRead
import com.casco.kettle.domain.decodeRunnerSnapshot
import com.casco.kettle.domain.reduceRunner
import com.casco.kettle.shell.files.SessionWrites
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The service's effect executor (#305): what each of the engine's effects does to the disk, the
 * speaker and the shade. Robolectric for `Log`, which the executor reports failures to.
 */
@RunWith(RobolectricTestRunner::class)
class RunnerEffectsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val loop = FakeRunnerLoop()
    private val events = mutableListOf<String>()
    private val written = mutableListOf<Session>()
    private val cues = mutableListOf<RunnerCue>()
    private val alerts = mutableListOf<Pair<StepEndAlert, Boolean>>()
    private var soundsOn = true
    private var failWrites = false
    private val stateFile by lazy { RunnerStateFile(folder.root.resolve("runner"), loop) }

    private val effects by lazy {
        RunnerEffects(
            writeSession = { session ->
                if (failWrites) throw IOException("disk full")
                written += session
                events += "write"
            },
            onSessionWritten = { events += "reread" },
            stateFile = stateFile,
            sounds =
                object : CueSounds {
                    override fun on() = soundsOn

                    override fun play(cue: RunnerCue) {
                        cues += cue
                    }
                },
            alerts =
                StepAlerts(
                    post = { alert, silent -> alerts += alert to silent },
                    cancel = { events += "cancel" },
                ),
            backUp = { events += "backup" },
        )
    }

    private fun saved(): RunnerSnapshot? =
        stateFile.dir.listFiles().orEmpty().singleOrNull()?.let {
            (
                decodeRunnerSnapshot(
                    it.readText(),
                ) as SnapshotRead.Read
            ).snapshot
        }

    @Test
    fun `writes the session, then has the screens read the log again`() {
        val start = started(PULLUPS)
        effects.carryOut(start.effects, start.state.snapshot)

        assertEquals(listOf(start.state.session), written)
        assertEquals(listOf("write", "reread"), events)
    }

    @Test
    fun `waits for the session lock another writer holds`() {
        val start = started(PULLUPS)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder =
            thread {
                runBlocking {
                    SessionWrites.lock.lock()
                    locked.countDown()
                    release.await()
                    SessionWrites.lock.unlock()
                }
            }
        locked.await()
        val writer = thread { effects.carryOut(start.effects, start.state.snapshot) }
        writer.join(300)

        assertTrue(written.isEmpty())
        release.countDown()
        writer.join(TimeUnit.SECONDS.toMillis(5))
        holder.join()
        assertEquals(1, written.size)
    }

    @Test
    fun `keeps going past a session write the disk refuses`() {
        failWrites = true
        val start = started(PULLUPS)
        effects.carryOut(start.effects, start.state.snapshot)

        assertFalse("reread" in events)
        assertEquals(start.state.snapshot, saved())
    }

    @Test
    fun `writes the state file at once on a transition`() {
        val start = started(PULLUPS)
        effects.carryOut(start.effects, start.state.snapshot)

        assertEquals(start.state.snapshot, saved())
    }

    @Test
    fun `debounces the state file for an input edit, writing the latest after a second`() {
        val start = started(PULLUPS)
        effects.carryOut(start.effects, start.state.snapshot)
        val first = reduceRunner(start.state, RunnerCommand.SetReps(4), loop.now)
        effects.carryOut(first.effects, first.state.snapshot)
        val second = reduceRunner(first.state, RunnerCommand.SetReps(5), loop.now)
        effects.carryOut(second.effects, second.state.snapshot)

        loop.advanceBy(900)
        assertEquals(start.state.snapshot, saved())
        loop.advanceBy(100)
        assertEquals(5L, saved()?.inputs?.reps)
    }

    @Test
    fun `flushes a debounced write at once, as when the runner leaves the screen`() {
        val start = started(PULLUPS)
        val edit = reduceRunner(start.state, RunnerCommand.SetReps(4), loop.now)
        effects.carryOut(edit.effects, edit.state.snapshot)
        stateFile.flush()

        assertEquals(4L, saved()?.inputs?.reps)
        assertEquals(0, loop.scheduled)
    }

    @Test
    fun `deletes the state file when the session ends, and drops a pending write`() {
        val start = started(PULLUPS)
        effects.carryOut(start.effects, start.state.snapshot)
        val edit = reduceRunner(start.state, RunnerCommand.SetReps(4), loop.now)
        effects.carryOut(edit.effects, edit.state.snapshot)
        val finish = reduceRunner(edit.state, RunnerCommand.Finish(edit.state.snapshot.visit), loop.now)
        effects.carryOut(finish.effects, finish.state.snapshot)
        loop.advanceBy(2000)

        assertNull(saved())
        assertTrue("backup" in events)
    }

    @Test
    fun `keeps the session running when the state file can't be written`() {
        val blocked = folder.newFile("not-a-dir")
        val file = RunnerStateFile(blocked.resolve("runner"), loop)
        val start = started(PULLUPS)
        file.save(start.state.snapshot, debounce = false)

        assertFalse(file.written())
    }

    @Test
    fun `plays a cue only with sounds on, read at each cue`() {
        val start = started(PULLUPS)
        effects.carryOut(listOf(RunnerEffect.PlayCue(RunnerCue.TICK)), start.state.snapshot)
        soundsOn = false
        effects.carryOut(listOf(RunnerEffect.PlayCue(RunnerCue.MILESTONE)), start.state.snapshot)

        assertEquals(listOf(RunnerCue.TICK), cues)
    }

    @Test
    fun `posts a step-end alert only while the runner isn't visible`() {
        val ended = endedPlank()
        effects.alerts.runnerVisible = true
        effects.carryOut(ended.effects, ended.state.snapshot)
        assertTrue(alerts.isEmpty())

        effects.alerts.runnerVisible = false
        effects.carryOut(ended.effects, ended.state.snapshot)
        assertEquals(1, alerts.size)
    }

    @Test
    fun `posts the alert silently with sounds on, since the service cues it, and with sound when off`() {
        val ended = endedPlank()
        effects.alerts.runnerVisible = false
        effects.carryOut(ended.effects, ended.state.snapshot)
        soundsOn = false
        effects.carryOut(ended.effects, ended.state.snapshot)

        assertEquals(listOf(true, false), alerts.map { it.second })
    }

    @Test
    fun `cancels the alert when the runner is visible again`() {
        effects.alerts.runnerVisible = false
        effects.alerts.runnerVisible = true

        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun `cancels the alert on a finish, but keeps the one a last step ending by itself posted`() {
        effects.alerts.runnerVisible = false
        val last = started(ROW)
        val ended = reduceRunner(last.state, RunnerCommand.TimeUpdate(last.state.snapshot.visit), T0 + 60_000)
        effects.carryOut(ended.effects, ended.state.snapshot)
        assertFalse("cancel" in events)
        assertEquals(StepEndAlert(StepEndTitle.TIME_UP, nextName = null), alerts.single().first)

        val other = started(PULLUPS)
        val finish = reduceRunner(other.state, RunnerCommand.Finish(other.state.snapshot.visit), T0)
        effects.carryOut(finish.effects, finish.state.snapshot)
        assertTrue("cancel" in events)
    }

    /** The plank's first set, ended by itself at the top of its range. */
    private fun endedPlank() =
        started(PLANK).let { reduceRunner(it.state, RunnerCommand.TimeUpdate(it.state.snapshot.visit), T0 + 25_000) }
}
