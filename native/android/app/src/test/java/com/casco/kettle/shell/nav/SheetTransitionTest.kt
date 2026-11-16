package com.casco.kettle.shell.nav

import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import com.casco.kettle.shell.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** The transitions table, and the sheet playing it on the view its route draws in (#197). */
@RunWith(RobolectricTestRunner::class)
class SheetTransitionTest {
    private val context = RuntimeEnvironment.getApplication()
    private val content = FrameLayout(context)
    private val native = View(context).also(content::addView)
    private val stage =
        SheetStage(content, native, {}, {}, { _, _ -> }).apply {
            paint(LightColors)
            content.measure(
                MeasureSpec.makeMeasureSpec(WIDTH_PX, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(HEIGHT_PX, MeasureSpec.EXACTLY),
            )
            content.layout(0, 0, WIDTH_PX, HEIGHT_PX)
        }
    private val motion = SheetMotion(stage)
    private val picture = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)

    private fun finishMotion() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    @Test
    fun `a push from a tab slides the sheet up, and back slides it down`() {
        assertEquals(Transition.Open, transitionBetween(Place.Tab, Place.Sheet, MoveKind.Push))
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Tab, MoveKind.Back))
    }

    /** Your file → Import, Import → Program guide, Workout preview → Workout editor. */
    @Test
    fun `a push inside the sheet moves along the shared axis, and back reverses it`() {
        assertEquals(Transition.Forward, transitionBetween(Place.Sheet, Place.Sheet, MoveKind.Push))
        assertEquals(Transition.Backward, transitionBetween(Place.Sheet, Place.Sheet, MoveKind.Back))
    }

    /** Program guide → Import replaces; a link, a program's delete and route-check navigate from the tabs. */
    @Test
    fun `a replace, and a navigate, fade through in place`() {
        assertEquals(Transition.FadeThrough, transitionBetween(Place.Sheet, Place.Sheet, MoveKind.Replace))
        assertEquals(Transition.FadeThrough, transitionBetween(Place.Sheet, Place.Sheet, MoveKind.Navigate))
        assertEquals(Transition.FadeIn, transitionBetween(Place.Tab, Place.Sheet, MoveKind.Navigate))
        assertEquals(Transition.FadeOut, transitionBetween(Place.Sheet, Place.Tab, MoveKind.Navigate))
    }

    /** Workout preview → session, the way into the runner from a sheet. */
    @Test
    fun `the sheet grows into the runner, and shrinks back out of it`() {
        assertEquals(Transition.Grow, transitionBetween(Place.Sheet, Place.Runner, MoveKind.Push))
        assertEquals(Transition.Shrink, transitionBetween(Place.Runner, Place.Sheet, MoveKind.Back))
    }

    @Test
    fun `moves that never touch a sheet move nothing of it`() {
        assertEquals(Transition.None, transitionBetween(Place.Tab, Place.Tab, MoveKind.Navigate))
        assertEquals(Transition.None, transitionBetween(Place.Tab, Place.Runner, MoveKind.Push))
    }

    /** The runner opened over a tab or a pushed screen closes onto it, and nothing of a sheet is left to move. */
    @Test
    fun `leaving the runner for a tab or a pushed screen releases the sheet`() {
        assertEquals(Transition.Release, transitionBetween(Place.Runner, Place.Tab, MoveKind.Back))
        assertEquals(Transition.Release, transitionBetween(Place.Runner, Place.Pushed, MoveKind.Back))
        // Program detail's Start: the runner opens over it as over a tab.
        assertEquals(Transition.None, transitionBetween(Place.Pushed, Place.Runner, MoveKind.Push))
    }

    /** #489: Today → Settings, Library → Exercise detail → Program detail, and back. */
    @Test
    fun `a pushed screen slides in from the side over a tab or another, and back slides it out`() {
        assertEquals(Transition.PushIn, transitionBetween(Place.Tab, Place.Pushed, MoveKind.Push))
        assertEquals(Transition.PushOut, transitionBetween(Place.Pushed, Place.Tab, MoveKind.Back))
        assertEquals(Transition.PushIn, transitionBetween(Place.Pushed, Place.Pushed, MoveKind.Push))
        assertEquals(Transition.PushOut, transitionBetween(Place.Pushed, Place.Pushed, MoveKind.Back))
    }

    /** A link, a restore or a delete reaches or leaves a pushed screen in place, as it does a sheet. */
    @Test
    fun `a navigate to or from a pushed screen fades in place`() {
        assertEquals(Transition.PushFadeIn, transitionBetween(Place.Tab, Place.Pushed, MoveKind.Navigate))
        assertEquals(Transition.PushFadeOut, transitionBetween(Place.Pushed, Place.Tab, MoveKind.Navigate))
        assertEquals(Transition.PushFadeOut, transitionBetween(Place.Pushed, Place.Pushed, MoveKind.Replace))
    }

    /** Settings → Support or Your file: a sheet covers a pushed screen as it covers a tab. */
    @Test
    fun `a sheet opens over a pushed screen as over a tab, and closes back onto it`() {
        assertEquals(Transition.Open, transitionBetween(Place.Pushed, Place.Sheet, MoveKind.Push))
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Pushed, MoveKind.Back))
    }

    /** Full frame, square, with no chrome or scrim, sliding in from the side over the picture it covers. */
    @Test
    fun `a pushed screen comes in full frame from the side, over what it covers`() {
        stage.layOutAsSheet()

        motion.pushIn(picture, LightColors, fade = false)

        assertEquals(0, (native.layoutParams as FrameLayout.LayoutParams).topMargin)
        assertEquals(false, native.clipToOutline)
        assertEquals(View.GONE, stage.chrome.visibility)
        assertEquals(View.GONE, stage.scrim.visibility)
        assertEquals(View.VISIBLE, stage.backdrop.visibility)
        assertEquals(WIDTH_PX.toFloat(), native.translationX)

        native.viewTreeObserver.dispatchOnPreDraw()
        finishMotion()

        assertEquals(0f, native.translationX)
        // Still behind it, for what a predictive back uncovers.
        assertEquals(View.VISIBLE, stage.backdrop.visibility)
    }

    /** A predictive back slid it part way: it leaves from there, as a picture, over the screen it covered. */
    @Test
    fun `a pushed screen leaves by its side from where a predictive back left it`() {
        motion.pushIn(null, LightColors, fade = false)
        native.viewTreeObserver.dispatchOnPreDraw()
        finishMotion()
        stage.translate(0f, DRAGGED_PX)

        motion.pushOut(picture, null, fade = false)

        assertEquals(View.VISIBLE, stage.leaving.visibility)
        assertEquals(DRAGGED_PX, stage.leaving.translationX)
        assertEquals(0, (stage.leaving.layoutParams as FrameLayout.LayoutParams).topMargin)
        // A tab is live under it: nothing of the stage is left behind.
        assertEquals(View.GONE, stage.backdrop.visibility)

        finishMotion()

        assertEquals(View.GONE, stage.leaving.visibility)
    }

    @Test
    fun `the live view takes the sheet's shape and moves as the sheet`() {
        stage.layOutAsSheet()

        assertEquals(stage.top, (native.layoutParams as FrameLayout.LayoutParams).topMargin)
        assertTrue(native.clipToOutline)

        stage.translate(DRAGGED_PX)

        assertEquals(DRAGGED_PX, native.translationY)
    }

    /** Settings → Support: the old sheet leaves as a picture, and the new one comes in live. */
    @Test
    fun `a hand-over brings the live view in under the old sheet's picture`() {
        stage.layOutAsSheet()

        motion.across(picture, Across.Forward)

        assertEquals(View.VISIBLE, stage.leaving.visibility)
        assertEquals(0f, native.alpha)

        finishMotion()

        assertEquals(1f, native.alpha)
        assertEquals(0f, native.translationX)
        assertEquals(View.GONE, stage.leaving.visibility)
    }

    /** A sheet dragged down and let go leaves from where the drag left it, not from the top. */
    @Test
    fun `a sheet closes from where it was dragged to`() {
        motion.present(null, LightColors, fade = false)
        finishMotion()
        stage.translate(DRAGGED_PX)

        motion.dismiss(picture, fade = false)

        assertEquals(DRAGGED_PX, stage.leaving.translationY)
    }

    private companion object {
        const val WIDTH_PX = 400
        const val HEIGHT_PX = 800
        const val DRAGGED_PX = 120f
    }
}
