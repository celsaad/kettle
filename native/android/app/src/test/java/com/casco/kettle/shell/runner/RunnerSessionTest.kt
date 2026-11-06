package com.casco.kettle.shell.runner

import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerCue
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.Session
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
    private val stateFile by lazy { RunnerStateFile(folder.root.resolve("runner-state.json"), loop) }

    private fun session(plan: RunnerPlan.Runnable = planOf(PLANK, PULLUPS)) =
        RunnerSession(
            plan,
            start = null,
            loop = loop,
            now = { loop.now },
            effects =
                RunnerEffects(
                    writeSession = { written += it },
                    onSessionWritten = {},
                    stateFile = stateFile,
                    sounds =
                        object : CueSounds {
                            override fun on() = true

                            override fun play(cue: RunnerCue) {
                                check(!failCues) { "no speaker" }
                                cues += cue
                            }
                        },
                    alerts = StepAlerts(post = { _, _ -> }, cancel = {}),
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
        assertFalse(stateFile.file.exists())

        loop.advanceBy(1)
        assertEquals(listOf(RunnerCue.TICK, RunnerCue.TICK, RunnerCue.TICK, RunnerCue.EXERCISE_CHANGE), cues)
        assertEquals(1, written.size)
        assertTrue(stateFile.file.exists())
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
        assertFalse(stateFile.file.exists())
    }

    @Test
    fun `can't be left once it runs, only finished`() {
        val session = session()
        session.begin()
        loop.advanceBy(3000)
        session.leave()

        assertTrue(view is RunnerView.Running)
        assertEquals(1, loop.scheduled)
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
        session.send(RunnerCommand.Finish)
        loop.advanceBy(3000)

        assertTrue(view is RunnerView.Running)
    }

    @Test
    fun `completes on Finish, with the backup pending, and sleeps no more`() {
        val session = session()
        session.begin()
        loop.advanceBy(3000)
        session.send(RunnerCommand.Finish)

        val completed = view as RunnerView.Completed
        assertEquals(FinishBackupState.PENDING, completed.backup)
        assertTrue(completed.session.endedAt != null)
        assertEquals(1, backups)
        assertEquals(0, loop.scheduled)
        assertFalse(stateFile.file.exists())

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
}
