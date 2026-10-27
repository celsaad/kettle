package com.casco.kettle.shell.nav

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.view.drawToBitmap
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.RnFrames
import com.casco.kettle.shell.theme.KettleColors

/**
 * RN's modal routes as a native sheet (#155): the one RN surface, never reparented, given a top margin,
 * rounded top corners and a slide over a scrim and a picture of the tab it covered ([SheetStage]).
 * Proven on a device in #177; the drawn signal and the pictures are #178's.
 *
 * RN stays the router. Every dismissal (the ×, hardware and predictive back, a grabber drag) sends RN a
 * back, and the sheet moves only once RN reports where it went and has drawn it ([RnFrames]). Two
 * Material behaviours are left out on purpose: a scrim tap and a drag from the content would each drop
 * an edit, and the editors have no confirm.
 *
 * The route reports reach [navigator] from here, at the same drawn frame, so the tab bar comes and goes
 * with the sheet rather than ahead of it.
 */
class ModalSheet(
    content: FrameLayout,
    private val shell: View,
    private val surface: View,
    private val navigator: TabNavigator,
    private val colors: () -> KettleColors,
    private val requestBack: () -> Unit,
) {
    private val stage: SheetStage = SheetStage(content, surface, requestBack, { motion.follow(it) }, ::letGo)
    private val motion: SheetMotion = SheetMotion(stage)

    /** Read on the JS thread by [wantsPicture]; written on the main one. */
    @Volatile private var at = Place.Tab

    /** Set by [steeredByBar] on the main thread, taken by the next NAVIGATE on the JS one. */
    @Volatile private var barSteering = false
    private var action: String? = null
    private val reports = mutableListOf<RouteReport>()

    /** The tab under the sheet, kept while the sheet is up: the runner shrinking back needs it again. */
    private var tabPicture: Bitmap? = null
    private var sheetPicture: Bitmap? = null

    /** Back while the sheet is up: to RN, with Material's predictive shrink first where it's on. */
    val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) = motion.cancel()

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                stage.scale(1 - PREDICTIVE_SHRINK * backEvent.progress)
            }

            override fun handleOnBackCancelled() = motion.settle()

            override fun handleOnBackPressed() {
                JsBridge.back()
                settleUnlessClosed()
            }
        }

    init {
        content.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            // A rotation or a window resize, with the sheet up and still: it keeps its shape.
            if (right - left != oldRight - oldLeft && at == Place.Sheet && !motion.running) stage.layOutAsSheet()
        }
    }

    /** The tab bar is about to steer RN to another tab, which no sheet is part of. */
    fun steeredByBar() {
        barSteering = true
    }

    /**
     * JS thread: whether an action of [type] needs a picture of the frame it replaces. Every action
     * that may open or move the sheet does, but not the bar's own tab switch: it is the commonest
     * action there is, and a picture is 8–28ms. Other NAVIGATEs (a link, route-check's messages) may
     * open a sheet, so they get one.
     */
    fun wantsPicture(type: String): Boolean {
        if (type == "NAVIGATE" && barSteering) {
            barSteering = false
            return false
        }
        return at != Place.Runner
    }

    /** UI thread, before the armed action's batch mounts: the frame the user still sees. */
    fun capture() {
        when (at) {
            Place.Tab -> tabPicture = pictureOf(shell)
            Place.Sheet -> sheetPicture = pictureOf(surface)
            Place.Runner -> Unit
        }
    }

    fun onRouteWillChange(type: String) {
        action = type
    }

    fun onRoute(report: RouteReport) {
        reports += report
        RnFrames.awaitDrawn(surface, ::arrive)
    }

    /** The first frame of the route reported last. */
    private fun arrive() {
        val latest = reports.lastOrNull() ?: return
        for (report in reports) navigator.onRoute(report.pathname)
        reports.clear()
        RnFrames.disarm()
        val transition = transitionBetween(at, latest.place, action)
        action = null
        at = latest.place
        backCallback.isEnabled = at == Place.Sheet
        // Each picture is used once; the tab's stays until the sheet has gone.
        play(transition, sheetPicture.also { sheetPicture = null })
        if (at == Place.Tab) tabPicture = null
    }

    private fun play(
        transition: Transition,
        sheet: Bitmap?,
    ) {
        when (transition) {
            Transition.None -> Unit
            Transition.Open -> motion.present(tabPicture, colors(), fade = false)
            Transition.FadeIn -> motion.present(tabPicture, colors(), fade = true)
            Transition.Close -> motion.dismiss(sheet, fade = false)
            Transition.FadeOut -> motion.dismiss(sheet, fade = true)
            Transition.Forward -> motion.across(sheet, Across.Forward)
            Transition.Backward -> motion.across(sheet, Across.Back)
            Transition.FadeThrough -> motion.across(sheet, Across.FadeThrough)
            Transition.Grow -> motion.grow()
            Transition.Shrink -> motion.shrink(tabPicture, colors())
            Transition.Release -> motion.release()
        }
    }

    /** Past a quarter of the frame, or flung down, it goes back; anything else snaps back. */
    private fun letGo(
        dy: Float,
        velocity: Float,
    ) {
        val density = surface.resources.displayMetrics.density
        if (dy > stage.frameHeight * DISMISS_FRACTION || velocity > FLING_DP_PER_S * density) {
            requestBack()
        } else {
            motion.settle()
        }
    }

    /**
     * After a back that may not close the sheet: a JS handler can take it (a picker closing, say), and
     * then nothing arrives to move the sheet off. A close lands within a few frames, well inside this.
     */
    private fun settleUnlessClosed() {
        surface.postDelayed({
            if (at == Place.Sheet && reports.isEmpty() &&
                !motion.running
            ) {
                motion.settle()
            }
        }, SETTLE_AFTER_MS)
    }

    /** Timed, since #155 asks for the cost on the editors: 8–28ms each in #177. */
    private fun pictureOf(view: View): Bitmap? {
        if (view.width == 0 || view.height == 0) return null
        val started = SystemClock.uptimeMillis()
        return view.drawToBitmap().also { Log.d(TAG, "Pictured in ${SystemClock.uptimeMillis() - started}ms") }
    }

    private companion object {
        const val TAG = "ModalSheet"
        const val PREDICTIVE_SHRINK = 0.1f
        const val DISMISS_FRACTION = 0.25f
        const val FLING_DP_PER_S = 1000f
        const val SETTLE_AFTER_MS = 500L
    }
}
