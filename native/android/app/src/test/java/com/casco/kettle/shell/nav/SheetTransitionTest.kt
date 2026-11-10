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

    /** Settings → Import or Support, Stats → Exercise progress, Program detail → Program editor. */
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

    /** Program detail → session, the one way into the runner from a sheet. */
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

    /** The runner opened over a tab closes onto it, and nothing of a sheet is left to move. */
    @Test
    fun `leaving the runner for a tab releases the sheet`() {
        assertEquals(Transition.Release, transitionBetween(Place.Runner, Place.Tab, MoveKind.Back))
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
