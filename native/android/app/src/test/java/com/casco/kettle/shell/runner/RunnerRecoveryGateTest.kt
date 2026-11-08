package com.casco.kettle.shell.runner

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.domain.RunnerRecovery
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.encodeRunnerSnapshot
import com.casco.kettle.storage.writeSessionFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.IOException

/**
 * One session at a time (#295): while the launch's Resume / Finish prompt is unanswered, or its Resume
 * is still rebuilding, nothing else can start one.
 */
@RunWith(RobolectricTestRunner::class)
class RunnerRecoveryGateTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val start = started(PULLUPS).state
    private val found = FoundRecovery(File("state.json"), RunnerRecovery.Prompt(start.snapshot, start.session))

    @get:Rule
    val folder = TemporaryFolder()

    @After
    fun clear() {
        LaunchRecovery.answered()
        RunnerHost.publish(RunnerView.Idle)
    }

    /** [found]'s files on disk, as the service left them when the process died. */
    private fun killed(): Pair<File, File> {
        val stateDir = folder.root.resolve("runner")
        val sessions = folder.root.resolve("sessions")
        writeSessionFile(sessions, start.session)
        stateDir.mkdirs()
        runnerStateFileFor(stateDir, start.session.id).writeText(encodeRunnerSnapshot(start.snapshot))
        return stateDir to sessions
    }

    private fun nextStarted() = shadowOf(app).nextStartedService

    @Test
    fun `starts nothing while the prompt is up`() {
        LaunchRecovery.ask(found)
        RunnerHost.open(app, RunnerStart.Adhoc)

        assertNull(nextStarted())
        assertEquals(RecoveryState.Asking(found), LaunchRecovery.state.value)
    }

    @Test
    fun `starts again once the prompt is answered`() {
        LaunchRecovery.ask(found)
        LaunchRecovery.answered()
        RunnerHost.open(app, RunnerStart.Adhoc)

        assertEquals(RunnerService::class.java.name, nextStarted()?.component?.className)
    }

    @Test
    fun `resumes in the service, and still starts nothing else while it rebuilds`() {
        LaunchRecovery.ask(found)
        LaunchRecovery.resume(app)
        val resume = nextStarted()
        RunnerHost.open(app, RunnerStart.Adhoc)

        assertEquals(RunnerService.ACTION_RESUME, resume?.action)
        assertNull(nextStarted())
        assertEquals(RecoveryState.Resuming(found), LaunchRecovery.state.value)
    }

    @Test
    fun `asks again when the service couldn't resume`() {
        LaunchRecovery.ask(found)
        LaunchRecovery.resume(app)
        LaunchRecovery.resumeFailed()

        assertEquals(RecoveryState.Asking(found), LaunchRecovery.state.value)
    }

    @Test
    fun `resumes only what it was asked about`() {
        LaunchRecovery.resume(app)

        assertNull(nextStarted())
        assertNull(LaunchRecovery.state.value)
    }

    @Test
    fun `asks at launch about a state file the process died with`() {
        val (stateDir, sessions) = killed()
        LaunchRecovery.recoverAtLaunch(stateDir, sessions)

        assertTrue((LaunchRecovery.state.value as RecoveryState.Asking).found.recovery is RunnerRecovery.Prompt)
    }

    @Test
    fun `asks nothing at launch while a session runs, which the runner attaches to instead`() {
        val (stateDir, sessions) = killed()
        RunnerHost.publish(RunnerView.Running(planOf(PULLUPS), start.snapshot, start.session))
        LaunchRecovery.recoverAtLaunch(stateDir, sessions)

        assertNull(LaunchRecovery.state.value)
    }

    @Test
    fun `finishes from the prompt, backs up, and lets sessions start again`() {
        val (stateDir, sessions) = killed()
        LaunchRecovery.recoverAtLaunch(stateDir, sessions)
        val asking = LaunchRecovery.state.value as RecoveryState.Asking
        val written = mutableListOf<Session>()
        var backups = 0

        val refused =
            runBlocking {
                LaunchRecovery.finish(asking.found, { written += it }, { backups++ }) {
                    findRunnerRecovery(stateDir, sessions)
                }
            }

        assertNull(refused)
        assertEquals(listOf(asking.found.recovery.finished), written)
        assertEquals(1, backups)
        assertNull(LaunchRecovery.state.value)
    }

    @Test
    fun `keeps the prompt up when the disk refuses the finish`() {
        LaunchRecovery.ask(found)
        var backups = 0

        val refused =
            runBlocking { LaunchRecovery.finish(found, { throw IOException("disk full") }, { backups++ }) { null } }

        assertEquals("disk full", refused)
        assertEquals(0, backups)
        assertEquals(RecoveryState.Asking(found), LaunchRecovery.state.value)
    }

    @Test
    fun `asks about the next state file once one is finished`() {
        LaunchRecovery.ask(found)
        val next = found.copy(stateFile = File("next.json"))

        runBlocking { LaunchRecovery.finish(found, {}, {}) { next } }

        assertEquals(RecoveryState.Asking(next), LaunchRecovery.state.value)
    }
}
