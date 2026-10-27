package com.casco.kettle.shell.nav

import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import com.casco.kettle.shell.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * The transitions table, and the sheet playing it on whichever view is live (#197): RN's surface, or the
 * Compose view beside it that a native sheet draws in.
 */
@RunWith(RobolectricTestRunner::class)
class SheetTransitionTest {
    private val context = RuntimeEnvironment.getApplication()
    private val content = FrameLayout(context)
    private val surface = View(context).also(content::addView)
    private val native = View(context).also(content::addView)
    private val stage =
        SheetStage(content, surface, native, {}, {}, { _, _ -> }).apply {
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
    fun `a report is on a tab, in the sheet, or on the runner`() {
        assertEquals(Place.Tab, RouteReport("/history", modal = false).place)
        assertEquals(Place.Sheet, RouteReport("/settings", modal = true).place)
        // Above the tabs but not a sheet: the runner, which RN still presents itself.
        assertEquals(Place.Runner, RouteReport("/session", modal = false).place)
    }

    @Test
    fun `a route message reads as a report`() {
        assertEquals(
            RouteReport("/settings", modal = true),
            RouteReport.from(mapOf("type" to "route", "pathname" to "/settings", "modal" to true)),
        )
    }

    /** A JS bundle from before the sheet sends no flag: it presents nothing as a sheet. */
    @Test
    fun `a route message without the modal flag is not a modal`() {
        assertEquals(
            RouteReport("/settings", modal = false),
            RouteReport.from(mapOf("type" to "route", "pathname" to "/settings")),
        )
    }

    /**
     * A native screen behind an RN stub route reads its `id` or `exerciseId` from here (M4). The bridge
     * hands a JS object over as a map, and anything but a string is no param a screen reads.
     */
    @Test
    fun `a route message carries its string params`() {
        assertEquals(
            RouteReport("/exercise-progress", modal = true, params = mapOf("exerciseId" to "kb-swing")),
            RouteReport.from(
                mapOf(
                    "type" to "route",
                    "pathname" to "/exercise-progress",
                    "modal" to true,
                    "params" to hashMapOf("exerciseId" to "kb-swing", "n" to 3.0),
                ),
            ),
        )
        // A bundle from before params sends none.
        assertEquals(emptyMap<String, String>(), RouteReport.from(mapOf("type" to "route", "pathname" to "/"))?.params)
    }

    @Test
    fun `a message that is not a route report reads as nothing`() {
        assertNull(RouteReport.from(mapOf("type" to "ready")))
        assertNull(RouteReport.from(mapOf("type" to "route", "pathname" to 42)))
    }

    @Test
    fun `a push from a tab slides the sheet up, and back slides it down`() {
        assertEquals(Transition.Open, transitionBetween(Place.Tab, Place.Sheet, "PUSH"))
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Tab, "GO_BACK"))
        // An editor's own close and a delete's dismissTo.
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Tab, "POP"))
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Tab, "POP_TO"))
    }

    /** Settings → Import or Support, Stats → Exercise progress, Program detail → Program editor. */
    @Test
    fun `a push inside the sheet moves along the shared axis, and back reverses it`() {
        assertEquals(Transition.Forward, transitionBetween(Place.Sheet, Place.Sheet, "PUSH"))
        assertEquals(Transition.Backward, transitionBetween(Place.Sheet, Place.Sheet, "GO_BACK"))
    }

    /**
     * Program guide → Import replaces; the shell's own navigate messages dismiss everything (POP_TO_TOP)
     * and navigate. The app opens nothing by NAVIGATE itself.
     */
    @Test
    fun `a replace, and the shell's own steering, fade through in place`() {
        assertEquals(Transition.FadeThrough, transitionBetween(Place.Sheet, Place.Sheet, "REPLACE"))
        assertEquals(Transition.FadeIn, transitionBetween(Place.Tab, Place.Sheet, "NAVIGATE"))
        assertEquals(Transition.FadeOut, transitionBetween(Place.Sheet, Place.Tab, "POP_TO_TOP"))
    }

    /** A report with no action before it: a reload, or a link the app was opened with. */
    @Test
    fun `a move with no action to go by fades`() {
        assertEquals(Transition.FadeIn, transitionBetween(Place.Tab, Place.Sheet, null))
        assertEquals(Transition.FadeOut, transitionBetween(Place.Sheet, Place.Tab, null))
    }

    /** Program detail → session, the one way into the runner from a sheet. */
    @Test
    fun `the sheet grows into the runner, and shrinks back out of it`() {
        assertEquals(Transition.Grow, transitionBetween(Place.Sheet, Place.Runner, "PUSH"))
        assertEquals(Transition.Shrink, transitionBetween(Place.Runner, Place.Sheet, "GO_BACK"))
    }

    @Test
    fun `moves that never touch a sheet are RN's own`() {
        assertEquals(Transition.None, transitionBetween(Place.Tab, Place.Tab, "NAVIGATE"))
        assertEquals(Transition.None, transitionBetween(Place.Tab, Place.Runner, "PUSH"))
    }

    /** Finishing a session the sheet grew into lands on a tab: whatever was under the runner goes. */
    @Test
    fun `leaving the runner for a tab drops whatever sheet it covered`() {
        assertEquals(Transition.Release, transitionBetween(Place.Runner, Place.Tab, "POP_TO"))
    }

    @Test
    fun `the live view takes the sheet's shape and moves as the sheet, whichever it is`() {
        stage.live = native
        stage.layOutAsSheet()

        assertEquals(stage.top, (native.layoutParams as FrameLayout.LayoutParams).topMargin)
        assertEquals(true, native.clipToOutline)
        // The stub under a native sheet keeps whatever shape it had: it shows nothing.
        assertEquals(0, (surface.layoutParams as FrameLayout.LayoutParams).topMargin)

        stage.translate(DRAGGED_PX)

        assertEquals(DRAGGED_PX, native.translationY)
        assertEquals(0f, surface.translationY)
    }

    /** Settings → Support: RN's sheet leaves as a picture, and the native one comes in live. */
    @Test
    fun `a hand-over brings the live view in under the old sheet's picture`() {
        stage.layOutAsSheet()
        stage.live = native
        stage.layOutAsSheet()

        motion.across(picture, Across.Forward)

        assertEquals(View.VISIBLE, stage.leaving.visibility)
        assertEquals(0f, native.alpha)
        assertEquals(1f, surface.alpha)

        finishMotion()

        assertEquals(1f, native.alpha)
        assertEquals(0f, native.translationX)
        assertEquals(View.GONE, stage.leaving.visibility)
    }

    /**
     * A native sheet dragged down and let go closes onto a tab, which makes the surface live again before
     * the close starts. The picture still leaves from where the drag left the sheet, not from the top.
     */
    @Test
    fun `a native sheet closes from where it was dragged to`() {
        stage.live = native
        motion.present(null, LightColors, fade = false)
        finishMotion()
        stage.translate(DRAGGED_PX)

        stage.live = surface
        motion.dismiss(picture, fade = false)

        assertEquals(DRAGGED_PX, stage.leaving.translationY)
        // The tab under it is full frame again.
        assertEquals(0, (surface.layoutParams as FrameLayout.LayoutParams).topMargin)
    }

    private companion object {
        const val WIDTH_PX = 400
        const val HEIGHT_PX = 800
        const val DRAGGED_PX = 120f
    }
}
