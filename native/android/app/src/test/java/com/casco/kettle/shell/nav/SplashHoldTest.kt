package com.casco.kettle.shell.nav

import android.view.View
import androidx.activity.ComponentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The splash's hold: the window draws nothing, so the system splash stays up, until the shell lets it
 * go ([LocalSplashExit]). A hold that never lets go hangs every launch on the splash.
 */
@RunWith(RobolectricTestRunner::class)
class SplashHoldTest {
    private val content: View =
        Robolectric
            .buildActivity(ComponentActivity::class.java)
            .setup()
            .get()
            .findViewById(android.R.id.content)

    /** `dispatchOnPreDraw` answers whether the frame was cancelled. */
    private fun frameCancelled() = content.viewTreeObserver.dispatchOnPreDraw()

    @Test
    fun `cancels every frame until it is released`() {
        SplashHold.hold(content)

        assertTrue(frameCancelled())
        assertTrue(frameCancelled())
    }

    @Test
    fun `lets frames draw once released, and stays out of the way after`() {
        val hold = SplashHold.hold(content)
        frameCancelled()

        hold.release()

        assertFalse(frameCancelled())
        // Released twice, as a second destination's exit would: still nothing to hold.
        hold.release()
        assertFalse(frameCancelled())
    }
}
