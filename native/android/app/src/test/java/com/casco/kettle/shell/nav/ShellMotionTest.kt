package com.casco.kettle.shell.nav

import androidx.compose.ui.graphics.TransformOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What each frame of #155's transitions sets, at the start and the end of each. The durations, easing,
 * travel, fades and scales are the View sheet's (`SheetMotion`, before #377), carried over unchanged.
 */
class ShellMotionTest {
    private val frame = Frame(offscreen = HEIGHT, top = TOP)

    private fun frameAt(
        transition: Transition,
        p: Float,
        motion: Motion = Motion(transition),
    ) = frameLook(motion, p, frame, dragY = 0f, backScale = 1f)

    @Test
    fun `each transition keeps today's duration and easing`() {
        val expected =
            mapOf(
                Transition.Open to Timing(400, ShellMotion.DECELERATE),
                Transition.Close to Timing(200, ShellMotion.ACCELERATE),
                Transition.FadeIn to Timing(300, ShellMotion.STANDARD),
                Transition.FadeOut to Timing(300, ShellMotion.STANDARD),
                Transition.FadeThrough to Timing(300, ShellMotion.STANDARD),
                Transition.Forward to Timing(300, ShellMotion.STANDARD),
                Transition.Backward to Timing(300, ShellMotion.STANDARD),
                Transition.Grow to Timing(270, ShellMotion.STANDARD),
                Transition.Shrink to Timing(270, ShellMotion.STANDARD),
                Transition.PushIn to Timing(400, ShellMotion.DECELERATE),
                Transition.PushOut to Timing(250, ShellMotion.ACCELERATE),
                Transition.PushFadeIn to Timing(300, ShellMotion.STANDARD),
                Transition.PushFadeOut to Timing(300, ShellMotion.STANDARD),
            )
        for ((transition, timing) in expected) assertEquals(transition.name, timing, timingOf(transition))
        assertNull(timingOf(Transition.None))
        assertNull(timingOf(Transition.Release))
    }

    @Test
    fun `Material's curves, as the views' path interpolators drew them`() {
        // Each cubic's end points, and a point each side of the middle: emphasized decelerate is fast out of the start.
        assertEquals(1f, ShellMotion.DECELERATE.transform(1f), DELTA)
        assertEquals(true, ShellMotion.DECELERATE.transform(0.2f) > 0.6f)
        assertEquals(true, ShellMotion.ACCELERATE.transform(0.5f) < 0.3f)
        assertEquals(true, ShellMotion.STANDARD.transform(0.5f) > 0.7f)
    }

    @Test
    fun `Open slides the sheet up from off the frame, Close down off it from where it was let go`() {
        assertEquals(Look(translationY = HEIGHT), frameAt(Transition.Open, 0f))
        assertEquals(Look(), frameAt(Transition.Open, 1f))

        val dragged = Motion(Transition.Close, fromY = DRAGGED, fromScale = SHRUNK)
        assertEquals(Look(translationY = DRAGGED, scale = SHRUNK), frameAt(Transition.Close, 0f, dragged))
        assertEquals(Look(translationY = HEIGHT, scale = SHRUNK), frameAt(Transition.Close, 1f, dragged))
    }

    @Test
    fun `FadeIn grows and fades the sheet in after the split, FadeOut fades it out before it`() {
        assertEquals(Look(scale = ShellMotion.FADE_SCALE, alpha = 0f), frameAt(Transition.FadeIn, 0f))
        assertEquals(0f, frameAt(Transition.FadeIn, ShellMotion.FADE_SPLIT).alpha)
        assertEquals(Look(), frameAt(Transition.FadeIn, 1f))

        assertEquals(1f, frameAt(Transition.FadeOut, 0f).alpha)
        assertEquals(0f, frameAt(Transition.FadeOut, ShellMotion.FADE_SPLIT).alpha)
    }

    @Test
    fun `the scrim fades in with an opening sheet and out with the last one closing`() {
        assertEquals(0f, scrimAlpha(Motion(Transition.Open), 0f))
        assertEquals(1f, scrimAlpha(Motion(Transition.FadeIn), 1f))
        assertEquals(1f, scrimAlpha(Motion(Transition.Close), 0f))
        assertEquals(0f, scrimAlpha(Motion(Transition.FadeOut), 1f))
        // Sheet to sheet, and the runner shrinking back over it, keep it.
        assertEquals(1f, scrimAlpha(Motion(Transition.Forward), 0.5f))
        assertEquals(1f, scrimAlpha(Motion(Transition.Shrink), 0f))
        assertEquals(1f, scrimAlpha(null, 0f))
    }

    @Test
    fun `the shared axis moves 30dp either way, fading through at the split`() {
        val forward = Motion(Transition.Forward)
        assertEquals(
            Look(translationX = SHIFT, alpha = 0f, origin = TransformOrigin.Center),
            sheetContentLook(forward, 0f, false, SHIFT),
        )
        assertEquals(Look(origin = TransformOrigin.Center), sheetContentLook(forward, 1f, false, SHIFT))
        assertEquals(Look(translationX = -SHIFT, alpha = 0f), sheetContentLook(forward, 1f, true, SHIFT))
        assertEquals(Look(), sheetContentLook(forward, 0f, true, SHIFT))

        val backward = Motion(Transition.Backward)
        assertEquals(-SHIFT, sheetContentLook(backward, 0f, false, SHIFT).translationX)
        assertEquals(SHIFT, sheetContentLook(backward, 1f, true, SHIFT).translationX)

        val through = sheetContentLook(Motion(Transition.FadeThrough), 0f, false, SHIFT)
        assertEquals(Look(scale = ShellMotion.FADE_SCALE, alpha = 0f, origin = TransformOrigin.Center), through)
    }

    /** The frame around a handed-over sheet comes back from a predictive back's shrink as the next one enters. */
    @Test
    fun `a sheet handed over after a predictive back comes back to full size`() {
        val shrunk = Motion(Transition.Backward, fromScale = SHRUNK)
        assertEquals(Look(scale = SHRUNK), frameAt(Transition.Backward, 0f, shrunk))
        assertEquals(Look(), frameAt(Transition.Backward, 1f, shrunk))
    }

    @Test
    fun `Grow lifts the sheet to the top and squares it, Shrink takes it back`() {
        assertEquals(Look(translationY = TOP), frameAt(Transition.Grow, 0f))
        assertEquals(Look(), frameAt(Transition.Grow, 1f))
        assertEquals(1f, cornerFraction(Motion(Transition.Grow), 0f))
        assertEquals(0f, cornerFraction(Motion(Transition.Grow), 1f))

        assertEquals(Look(), frameAt(Transition.Shrink, 0f))
        assertEquals(Look(translationY = TOP), frameAt(Transition.Shrink, 1f))
        assertEquals(0f, cornerFraction(Motion(Transition.Shrink), 0f))
        assertEquals(1f, cornerFraction(Motion(Transition.Shrink), 1f))
        assertEquals(true, Transition.Grow.growing() && Transition.Shrink.growing() && !Transition.Open.growing())
    }

    @Test
    fun `with nothing playing the sheet sits where the hand has it`() {
        assertEquals(Look(translationY = DRAGGED, scale = SHRUNK), frameLook(null, 1f, frame, DRAGGED, SHRUNK))
        assertEquals(1f, cornerFraction(null, 1f))
    }

    @Test
    fun `a pushed screen slides in from the end, and out from where a predictive back left it`() {
        val pushIn = Motion(Transition.PushIn)
        assertEquals(Look(translationX = WIDTH), pushedLook(pushIn, 0f, false, WIDTH, 0f))
        assertEquals(Look(), pushedLook(pushIn, 1f, false, WIDTH, 0f))

        val pushOut = Motion(Transition.PushOut, fromX = DRAGGED)
        assertEquals(Look(translationX = DRAGGED), pushedLook(pushOut, 0f, true, WIDTH, 0f))
        assertEquals(Look(translationX = WIDTH), pushedLook(pushOut, 1f, true, WIDTH, 0f))
        // Right to left, it leaves by the left.
        assertEquals(Look(translationX = -WIDTH), pushedLook(Motion(Transition.PushOut), 1f, true, -WIDTH, 0f))

        assertEquals(Look(translationX = DRAGGED), pushedLook(null, 1f, false, WIDTH, DRAGGED))
    }

    @Test
    fun `a pushed screen fades through in place for a navigate`() {
        assertEquals(
            Look(scale = ShellMotion.FADE_SCALE, alpha = 0f),
            pushedLook(Motion(Transition.PushFadeIn), 0f, false, WIDTH, 0f),
        )
        assertEquals(Look(alpha = 0f), pushedLook(Motion(Transition.PushFadeOut), 1f, true, WIDTH, 0f))
    }

    /** What a stack shows: a sheet over sheets shows one, and a pushed screen covers the tab under the sheets. */
    @Test
    fun `a stack shows its top sheet, the pushed screen under the sheets, and the one beneath that`() {
        val settings = StackEntry(1, Route(Destination.Settings))
        val dips = StackEntry(2, Route(Destination.ExerciseDetail, mapOf("id" to "dips")))
        val import = StackEntry(3, Route(Destination.Import))
        val support = StackEntry(4, Route(Destination.Support))

        assertEquals(Shown(null, null, null), shownBy(emptyList()))
        assertEquals(Shown(null, null, support), shownBy(listOf(import, support)))
        assertEquals(Shown(dips, settings, null), shownBy(listOf(settings, dips)))
        assertEquals(Shown(dips, settings, support), shownBy(listOf(settings, dips, import, support)))
        // A sheet under a pushed screen is no pushed screen beneath it.
        assertEquals(Shown(dips, null, null), shownBy(listOf(import, dips)))
    }

    /** A transition cut short hands the next one the frame it had got to, for the sheet or pushed screen that stays. */
    @Test
    fun `a transition cut short holds the sheet or pushed screen where it had got to`() {
        assertEquals(Look(translationY = HEIGHT / 2), heldFrame(Motion(Transition.Open), 0.5f, frame))
        assertEquals(WIDTH / 2, heldX(Motion(Transition.PushIn), 0.5f, WIDTH))
        // What leaves has nothing to hand on.
        assertNull(heldFrame(Motion(Transition.Close), 0.5f, frame))
        assertNull(heldX(Motion(Transition.PushOut), 0.5f, WIDTH))
    }

    @Test
    fun `only the transitions that take a screen off play it out`() {
        val sheets = Transition.entries.filter { it.leavesSheet() }.toSet()
        val pushed = Transition.entries.filter { it.leavesPushed() }.toSet()
        assertEquals(
            setOf(
                Transition.Close,
                Transition.FadeOut,
                Transition.Forward,
                Transition.Backward,
                Transition.FadeThrough,
            ),
            sheets,
        )
        assertEquals(setOf(Transition.PushOut, Transition.PushFadeOut), pushed)
    }

    private companion object {
        const val HEIGHT = 800f
        const val WIDTH = 400f
        const val TOP = 36f
        const val DRAGGED = 120f
        const val SHRUNK = 0.95f
        const val SHIFT = 30f
        const val DELTA = 0.001f
    }
}
