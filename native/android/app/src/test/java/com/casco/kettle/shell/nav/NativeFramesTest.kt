package com.casco.kettle.shell.nav

import android.os.Looper
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * The native sheet's arrival gate (#197): a transition waits for the screen it slides in, holding the
 * screen as it was meanwhile. Frames are dispatched by hand, as the window would.
 */
@RunWith(RobolectricTestRunner::class)
class NativeFramesTest {
    private val view = View(RuntimeEnvironment.getApplication())
    private val frames = NativeFrames(view)
    private val started = mutableListOf<String>()

    private val support = SheetContent("/support", emptyMap())
    private val progress = SheetContent("/exercise-progress", mapOf("exerciseId" to "kb-swing"))

    /** One frame's pre-draw: whether it would have been drawn. */
    private fun frame(): Boolean = view.viewTreeObserver.dispatchOnPreDraw().not()

    @Test
    fun `a native arrival holds every frame until its screen has composed`() {
        frames.awaitComposed(support) { started += "support" }

        assertEquals(false, frame())
        assertEquals(false, frame())
        assertEquals(emptyList<String>(), started)

        frames.onComposed(support)

        // The frame that finds it composed is held too, so the transition's own first frame comes next.
        assertEquals(false, frame())
        assertEquals(listOf("support"), started)
        assertEquals(true, frame())
    }

    /** Another route, or the same path with other params, is not the screen the arrival waits for. */
    @Test
    fun `a composition of anything else doesn't release it`() {
        frames.awaitComposed(progress) { started += "progress" }

        frames.onComposed(SheetContent("/exercise-progress", mapOf("exerciseId" to "goblet-squat")))
        frame()
        frames.onComposed(null)
        frame()

        assertEquals(emptyList<String>(), started)
    }

    /** A composition that never reports must not freeze the window: it goes without its screen. */
    @Test
    fun `a screen that never composes is let go after the backstop`() {
        frames.awaitComposed(support) { started += "support" }
        frame()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(BACKSTOP_MS))
        frame()

        assertEquals(listOf("support"), started)
        assertEquals(true, frame())
    }

    /** The first runs first, so the second's transition starts from where the first's ends. */
    @Test
    fun `a second arrival runs the held one first, then holds for its own`() {
        frames.awaitComposed(support) { started += "support" }
        frames.awaitComposed(progress) { started += "progress" }

        assertEquals(listOf("support"), started)

        frames.onComposed(progress)
        frame()

        assertEquals(listOf("support", "progress"), started)
    }

    @Test
    fun `a held arrival runs once, however it is let go`() {
        frames.awaitComposed(support) { started += "support" }
        frames.onComposed(support)

        frames.flush()
        frame()
        frames.flush()

        assertEquals(listOf("support"), started)
    }

    private companion object {
        const val BACKSTOP_MS = 500L
    }
}
