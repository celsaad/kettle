package com.casco.kettle.shell.nav

import android.view.View
import androidx.activity.ComponentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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

    /** Each test is a fresh process as far as the splash knows: Robolectric keeps statics between them. */
    @Before
    fun freshProcess() = SplashHold.forgetRelease()

    @Test
    fun `cancels every frame until it is released`() {
        SplashHold.hold(content)

        assertTrue(frameCancelled())
        assertTrue(frameCancelled())
    }

    @Test
    fun `lets frames draw once released`() {
        val hold = SplashHold.hold(content)
        frameCancelled()

        hold.release()

        assertFalse(frameCancelled())
    }

    /**
     * An activity made again in the same process (a font or display size change) has no system splash
     * to hold, so it draws at once. A process brought back after death starts over, as the first test.
     */
    @Test
    fun `holds nothing once the process has let a splash go`() {
        SplashHold.hold(content).release()
        frameCancelled()

        SplashHold.hold(content)

        assertFalse(frameCancelled())
    }
}
