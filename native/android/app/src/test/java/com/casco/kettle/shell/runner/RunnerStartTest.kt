package com.casco.kettle.shell.runner

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerCue
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A start the runner asked for and the service hasn't begun yet (#318): it counts as starting, so a
 * second start is refused and a leave in that window is applied once the session exists.
 */
@RunWith(RobolectricTestRunner::class)
class RunnerStartTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val loop = FakeRunnerLoop()
    private val written = mutableListOf<Session>()
    private val stateFile by lazy { RunnerStateFile(folder.root.resolve("runner"), loop) }
    private var stops = 0

    @After
    fun clear() {
        RunnerHost.startEnded()
        RunnerHost.session = null
        RunnerHost.publish(RunnerView.Idle)
    }

    private fun nextStarted() = shadowOf(app).nextStartedService

    /** What the service does with a start it resolved: a session the host adopts, then counts into. */
    private fun begin(): RunnerSession? = adopted()?.also { it.begin() }

    /** Adopted and not yet begun, as while the service waits on its sounds before the count-in. */
    private fun adopted(): RunnerSession? {
        var current: RunnerSession? = null
        val created =
            RunnerSession(
                planOf(PLANK, PULLUPS),
                null,
                loop,
                now = { loop.now },
                effects =
                    RunnerEffects(
                        writeSession = { written += it },
                        onSessionWritten = {},
                        stateFile = stateFile,
                        sounds =
                            object : CueSounds {
                                override fun on() = false

                                override fun play(cue: RunnerCue) = Unit
                            },
                        alerts = StepAlerts(post = { _, _ -> }, cancel = {}),
                        backUp = {},
                    ),
                publish = RunnerHost.publisher({ current }) { stops++ },
            )
        current = created
        return created.takeIf { RunnerHost.adopt(it) }
    }

    @Test
    fun `a leave before the service begins ends the session it begins, with nothing written`() {
        RunnerHost.open(app, RunnerStart.Adhoc)
        assertTrue(RunnerHost.leave())
        begin()
        loop.advanceBy(10_000)

        assertEquals(RunnerView.Idle, RunnerHost.view.value)
        assertTrue(written.isEmpty())
        assertFalse(stateFile.written())
        // Idle over Idle publishes nothing the service's collector sees, so the session's own Idle stops it.
        assertEquals(1, stops)
    }

    @Test
    fun `a second start before the service begins is refused, and so is a second begin`() {
        RunnerHost.open(app, RunnerStart.Adhoc)
        RunnerHost.open(app, RunnerStart.Adhoc)
        val first = begin()
        val second = begin()

        assertNotNull(nextStarted())
        assertNull(nextStarted())
        assertNotNull(first)
        assertNull(second)
        assertTrue(RunnerHost.session === first)
        loop.advanceBy(3000)
        assertTrue(RunnerHost.view.value is RunnerView.Running)
        assertEquals(1, written.size)
    }

    @Test
    fun `a start the service couldn't resolve lets the next one through`() {
        RunnerHost.open(app, RunnerStart.Adhoc)
        nextStarted()
        RunnerHost.startEnded()
        RunnerHost.open(app, RunnerStart.Adhoc)

        assertNotNull(nextStarted())
    }

    @Test
    fun `a leave after the count-in ended says it didn't leave, so the route stays on the session`() {
        RunnerHost.open(app, RunnerStart.Adhoc)
        begin()
        loop.advanceBy(3000)

        // The count-in's last tick ran first: the session runs, and the route mustn't hand back to RN.
        assertFalse(RunnerHost.leave())
        assertTrue(RunnerHost.view.value is RunnerView.Running)
        assertEquals(0, stops)
    }

    @Test
    fun `still starting while the adopted session waits on its sounds, and a leave then goes to it`() {
        // The review's interleaving: adopting cleared the start, a second open slipped into the sounds' wait,
        // and a leave was held for that start, then dropped with it, while the first counted in unseen.
        RunnerHost.open(app, RunnerStart.Adhoc)
        nextStarted()
        val first = adopted()
        RunnerHost.open(app, RunnerStart.Adhoc)
        assertNull(nextStarted())

        assertTrue(RunnerHost.leave())
        first?.begin()
        loop.advanceBy(10_000)
        assertEquals(RunnerView.Idle, RunnerHost.view.value)
        assertTrue(written.isEmpty())
        assertEquals(1, stops)
    }

    @Test
    fun `starts again once the session has shown, and the service's release frees its place`() {
        RunnerHost.open(app, RunnerStart.Adhoc)
        val first = begin()
        loop.advanceBy(10_000)
        first?.send(RunnerCommand.Finish)
        RunnerHost.dismiss()
        RunnerHost.release(first)
        RunnerHost.open(app, RunnerStart.Adhoc)
        val second = begin()

        assertNotNull(second)
        // A dead service's late release can't take the newer session's place.
        RunnerHost.release(first)
        assertTrue(RunnerHost.session === second)
    }
}
