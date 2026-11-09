package com.casco.kettle.shell.runner

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.shell.files.StoragePaths
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper

/**
 * The service's half of a start (#318): what it does with the start [RunnerHost.open] sent when it
 * can't resolve one, and with a second while it holds a session. [RunnerStartTest] has the host's half.
 */
@RunWith(RobolectricTestRunner::class)
class RunnerServiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val library = StoragePaths(app.filesDir).library
    private val services = mutableListOf<ServiceController<RunnerService>>()

    @After
    fun clear() {
        services.forEach { it.destroy() }
        RunnerHost.startEnded()
        RunnerHost.publish(RunnerView.Idle)
        library.delete()
    }

    private fun planted() {
        library.parentFile?.mkdirs()
        library.writeText("version: 1\nexercises: []\nworkouts: []\nprograms: []\n")
    }

    /** The intent [RunnerHost.open] sent, as the platform would hand it to the service. */
    private fun opened(): Intent {
        RunnerHost.open(app, RunnerStart.Adhoc)
        return checkNotNull(shadowOf(app).nextStartedService)
    }

    private fun service(intent: Intent) =
        Robolectric.buildService(RunnerService::class.java, intent).create().also { services += it }

    /** Runs what's queued on the runner's thread, then on the main one. */
    private fun idleRunner() {
        // Every service's runner thread, this test's and those an earlier one destroyed, which have quit and
        // refuse to idle: nothing is left on them to run.
        for (looper in ShadowLooper.getAllLoopers().filter { it.thread.name == "runner" }) {
            try {
                shadowOf(looper).idle()
            } catch (_: IllegalStateException) {
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a start it can't resolve stops it, and the next open goes through`() {
        val service = service(opened())
        service.startCommand(0, 1)
        idleRunner()

        assertTrue(shadowOf(service.get()).isStoppedBySelf)
        assertNotNull(runCatching { opened() }.getOrNull())
    }

    @Test
    fun `a second start while it holds a session neither replaces it nor stops it`() {
        planted()
        val intent = opened()
        val service = service(intent)
        service.startCommand(0, 1)
        idleRunner()
        val first = RunnerHost.session
        // The library gone under it: resolved, the second start would find nothing and stop the service.
        library.delete()
        service.get().onStartCommand(intent, 0, 2)
        idleRunner()

        assertNotNull(first)
        assertTrue(RunnerHost.session === first)
        assertFalse(shadowOf(service.get()).isStoppedBySelf)
    }
}
