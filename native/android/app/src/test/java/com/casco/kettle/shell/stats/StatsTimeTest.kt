package com.casco.kettle.shell.stats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * #431's review: Stats' midnight timer counts uptime, which stops while the phone sleeps, so coming back
 * to the screen has to read the time again on its own.
 */
@RunWith(RobolectricTestRunner::class)
class StatsTimeTest {
    @get:Rule
    val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    private var wall = Instant.parse("2026-11-13T23:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = wall
        }

    @Test
    fun `coming back to the screen reads the time again, a night asleep or not`() {
        val owner = Owner().apply { lifecycle.currentState = Lifecycle.State.RESUMED }
        var seen: StatsTime? = null
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                seen = rememberStatsTime(clock) { TimeZone.UTC }
            }
        }
        compose.waitForIdle()
        assertEquals(Instant.parse("2026-11-13T23:00:00Z"), seen?.now)

        // The screen goes off at 23:00 and comes back at 07:00, with no midnight timer having fired.
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        wall = Instant.parse("2026-11-14T07:00:00Z")
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        compose.waitForIdle()

        assertEquals(Instant.parse("2026-11-14T07:00:00Z"), seen?.now)
    }
}
