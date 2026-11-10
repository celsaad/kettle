package com.casco.kettle.shell.runner

import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerCue
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerRecovery
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SnapshotRead
import com.casco.kettle.domain.decodeRunnerSnapshot
import com.casco.kettle.domain.resumeRunner
import com.casco.kettle.domain.startRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The running session as the service hosts it (#305): the count-in it owns, the session it starts
 * after, the timer it sleeps on, and what the screen is shown. The engine's rules are `:shared`'s
 * tests; these are about driving it on one thread and a clock.
 */
@RunWith(RobolectricTestRunner::class)
class RunnerSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val loop = FakeRunnerLoop()
    private val written = mutableListOf<Session>()
    private val cues = mutableListOf<RunnerCue>()
    private val views = mutableListOf<RunnerView>()
    private var backups = 0
    private var failCues = false
    private var failClock = false
    private val stateFile by lazy { RunnerStateFile(folder.root.resolve("runner"), loop) }

    private fun session(plan: RunnerPlan.Runnable = planOf(PLANK, PULLUPS)) =
        RunnerSession(
            plan,
            start = null,
            loop = loop,
            now = {
                check(!failClock) { "no clock" }
                loop.now
            },
            effects =
                testEffects(
                    stateFile,
                    writeSession = { written += it },
                    sounds =
                        object : CueSounds {
                            override fun on() = true

                            override fun play(cue: RunnerCue) {
                                check(!failCues) { "no speaker" }
                                cues += cue
                            }
                        },
                    backUp = { backups++ },
                ),
            publish = { views += it },
        )

    private val view get() = views.last()
    private val step get() = (view as RunnerView.Running).snapshot.step

    @Test
    fun `counts in 3, 2, 1 before the session, writing nothing until it ends`() {
        session().begin()
        assertTrue(view is RunnerView.CountingIn)
        loop.advanceBy(2999)
        assertTrue(written.isEmpty())
        assertFalse(stateFile.written())

        loop.advanceBy(1)
        assertEquals(listOf(RunnerCue.TICK, RunnerCue.TICK, RunnerCue.TICK, RunnerCue.EXERCISE_CHANGE), cues)
        assertEquals(1, written.size)
        assertTrue(stateFile.written())
        assertTrue(step is RunnerStep.Hold)
    }

    @Test
    fun `leaves during the count-in with no file and no state file`() {
        val session = session()
        session.begin()
        loop.advanceBy(1500)

        session.leave()
        loop.advanceBy(10_000)
        assertEquals(RunnerView.Idle, view)
        assertTrue(written.isEmpty())
        assertFalse(stateFile.written())
    }

    @Test
    fun `can't be left once it runs, only finished`() {
        val session = session()
        session.begin()
        loop.advanceBy(3000)

        assertFalse(session.leave())
        assertTrue(view is RunnerView.Running)
        assertEquals(1, loop.scheduled)
    }

    @Test
    fun `a leave sent as the count-in ends still leaves, though its block runs after the last tick`() {
        // #318: back pressed right at the end of the count-in queued its block behind the last tick, which
        // started the session while the route had already handed back to RN.
        val session = session()
        session.begin()
        loop.advanceBy(2999)
        loop.hold()

        assertTrue(session.leave())
        loop.advanceBy(1)
        loop.flushHeld()
        loop.advanceBy(10_000)
        assertEquals(RunnerView.Idle, view)
        assertTrue(written.isEmpty())
        assertFalse(stateFile.written())
    }

    @Test
    fun `stays left when the leave lands before the count-in began`() {
        val session = session()
        session.leave()
        session.begin()
        loop.advanceBy(10_000)

        assertEquals(RunnerView.Idle, view)
        assertTrue(written.isEmpty())
    }

    @Test
    fun `ends a step whose end wake was lost, on the next thing that happens`() {
        // Seen on the S25+ (#306): an end wake never ran and the session sat on 0:00. With every timer
        // lost and the step long past its end, a pause and resume reschedules, and that wakes at once.
        val session = session(planOf(ROW)).apply { begin() }
        loop.advanceBy(3000)
        loop.dropPending()
        loop.now += 70_000

        session.send(RunnerCommand.TogglePause)
        session.send(RunnerCommand.TogglePause)
        loop.advanceBy(0)

        assertTrue("still ${views.last()}", view is RunnerView.Completed)
    }

    @Test
    fun `completes by itself when the workout's last step is a countdown that ends`() {
        // Seen on the S25+ (#306): the last HIIT round of Foundations · Push sat at 0:00 for good.
        session(planOf(BURPEES)).begin()
        loop.advanceBy(3000)
        // Work, rest, work: 40 + 20 + 40 seconds, and a little over.
        loop.advanceBy(100_500)
        assertTrue("still ${views.last()}", view is RunnerView.Completed)
    }

    @Test
    fun `ends a hold on time by itself, sleeping until its moments rather than polling`() {
        session().begin()
        loop.advanceBy(3000)
        assertEquals(1, loop.scheduled)

        loop.advanceBy(24_999)
        assertTrue(step is RunnerStep.Hold)
        loop.advanceBy(1)
        assertTrue(step is RunnerStep.Rest)
        // The minimum crossed at 15 s, the ticks at 22, 23 and 24, then the hand-over to the rest.
        assertEquals(listOf(RunnerCue.MILESTONE, RunnerCue.TICK, RunnerCue.TICK, RunnerCue.TICK), cues.drop(4).take(4))
    }

    @Test
    fun `shows the screen nothing new for a cue alone`() {
        session().begin()
        loop.advanceBy(3000)
        val before = views.size
        loop.advanceBy(15_000) // the milestone

        assertEquals(before, views.size)
    }

    @Test
    fun `stops the timer while paused and picks it up on resume`() {
        val session = session()
        session.begin()
        loop.advanceBy(3000)
        session.send(RunnerCommand.TogglePause)
        assertEquals(0, loop.scheduled)

        loop.advanceBy(60_000)
        session.send(RunnerCommand.TogglePause)
        loop.advanceBy(25_000)
        assertTrue(step is RunnerStep.Rest)
    }

    @Test
    fun `ignores commands sent during the count-in`() {
        val session = session()
        session.begin()
        session.send(RunnerCommand.Finish(visit = 0))
        loop.advanceBy(3000)

        assertTrue(view is RunnerView.Running)
    }

    @Test
    fun `completes on Finish, with the backup pending, and sleeps no more`() {
        val session = session()
        session.begin()
        loop.advanceBy(3000)
        session.send(RunnerCommand.Finish((view as RunnerView.Running).snapshot.visit))

        val completed = view as RunnerView.Completed
        assertEquals(FinishBackupState.PENDING, completed.backup)
        assertTrue(completed.session.endedAt != null)
        assertEquals(1, backups)
        assertEquals(0, loop.scheduled)
        assertFalse(stateFile.written())

        session.backedUp(failed = true)
        assertEquals(FinishBackupState.FAILED, (view as RunnerView.Completed).backup)
    }

    @Test
    fun `keeps time through an effect that throws`() {
        failCues = true
        session().begin()
        loop.advanceBy(3000 + 25_000)

        assertTrue(step is RunnerStep.Rest)
        assertEquals(1, loop.scheduled)
    }

    @Test
    fun `leaves a count-in that throws, with nothing written, rather than crash`() {
        session().begin()
        loop.advanceBy(1000)
        failClock = true
        loop.advanceBy(1000)

        assertEquals(RunnerView.Idle, view)
        assertTrue(written.isEmpty())
        assertEquals(0, loop.scheduled)
    }

    /** The launch's Resume (#307): [plan]'s session, as the service left it at T0, rebuilt [awayMs] later. */
    private fun resumed(
        plan: RunnerPlan.Runnable,
        awayMs: Long,
    ): RunnerSession {
        val dead = startRunner(plan, null, T0).state
        stateFile.save(dead.snapshot, debounce = false)
        loop.advanceBy(awayMs)
        val session = session(plan)
        session.begin(resumeRunner(RunnerRecovery.Prompt(dead.snapshot, dead.session), loop.now))
        loop.advanceBy(0)
        return session
    }

    @Test
    fun `resumes a rebuilt session paused, with no count-in, its state file rewritten`() {
        resumed(planOf(PLANK, PULLUPS), awayMs = 5_000)

        val running = view as RunnerView.Running
        assertTrue(running.snapshot.clock.paused)
        assertTrue(views.none { it is RunnerView.CountingIn })
        assertTrue(cues.isEmpty())
        val saved = decodeRunnerSnapshot(runnerStateFileFor(stateFile.dir, running.snapshot.sessionId).readText())
        assertEquals(running.snapshot, (saved as SnapshotRead.Read).snapshot)

        loop.advanceBy(600_000) // paused: nothing ends by itself
        assertEquals(running.snapshot.stepIndex, (view as RunnerView.Running).snapshot.stepIndex)
    }

    @Test
    fun `completes a resumed session whose last step ran out during the kill`() {
        resumed(planOf(ROW), awayMs = 600_000)

        assertTrue(view is RunnerView.Completed)
        assertEquals(1, backups)
        assertFalse(stateFile.written())
    }
}
