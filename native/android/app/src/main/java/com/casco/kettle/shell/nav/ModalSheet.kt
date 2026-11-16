package com.casco.kettle.shell.nav

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.view.drawToBitmap
import com.casco.kettle.shell.theme.KettleColors

/**
 * The sheet routes, presented as a sheet (#155): the [shell]'s native sheet view given a top margin,
 * rounded top corners and a slide over a scrim and a picture of the tab it covered ([SheetStage]).
 *
 * The [navigator] is the router (#309), and this hears every move it makes. Before one lands, the screen
 * still shows where it starts, so that is when it is pictured; after, the transition waits for the
 * route it arrives at to compose ([NativeFrames]), then plays. Every dismissal (the ×, hardware and
 * predictive back, a grabber drag) is a back. Two Material behaviours are left out on purpose: a scrim
 * tap and a drag from the content would each drop an edit, and the editors have no confirm.
 *
 * Over detekt's function count by [repaint], which stays here: this is the one owner of the views the
 * sheet draws, and the only way in to them.
 */
@Suppress("TooManyFunctions")
class ModalSheet(
    content: FrameLayout,
    private val shell: ShellLayout,
    private val navigator: Navigator,
    private val colors: () -> KettleColors,
    private val requestBack: () -> Unit,
) : MoveListener {
    internal val stage: SheetStage =
        SheetStage(content, shell.nativeSheet, requestBack, { motion.follow(it) }, ::letGo)
    private val motion: SheetMotion = SheetMotion(stage)

    private var place = Place.Tab

    /**
     * What the sheet covers, a tab or a pushed screen, kept while the sheet is up: the runner shrinking
     * back needs it again.
     */
    private var tabPicture: Bitmap? = null

    /**
     * What each pushed screen covers, bottom to top (#489): a predictive back uncovers it, and the one
     * under a pushed screen that goes back to another is behind that one. Null where there was nothing
     * to picture, as after a restore.
     */
    private val pushedUnder = mutableListOf<Bitmap?>()

    /** The picture [willMove] took for a pushed screen arriving, which [moved] files under it. */
    private var arrivingUnder: Bitmap? = null
    private var sheetPicture: Bitmap? = null

    /** While an arrival is held: the picture its transition slides out, which is still on screen. */
    private var heldPicture: Bitmap? = null

    /** The scheme [repaint] last painted, to tell a change of scheme from the same one again. */
    private var painted: KettleColors? = null

    /** Back while the sheet is up, with Material's predictive shrink first where it's on. */
    val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) = motion.cancel()

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                // A pushed screen starts to leave the way it will, by its side; a sheet shrinks.
                if (place == Place.Pushed) {
                    stage.translate(0f, PREDICTIVE_SLIDE * stage.frameWidth * backEvent.progress)
                } else {
                    stage.scale(1 - PREDICTIVE_SHRINK * backEvent.progress)
                }
            }

            override fun handleOnBackCancelled() = motion.settle()

            override fun handleOnBackPressed() {
                navigator.back()
                // A back the navigator drops moves nothing, and the shrink has to come undone.
                if (place.stacked && !motion.running) motion.settle()
            }
        }

    init {
        content.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            // A rotation or a window resize, with the sheet up and still: it keeps its shape.
            if (right - left != oldRight - oldLeft && place == Place.Sheet && !motion.running) stage.layOutAsSheet()
        }
    }

    /**
     * Paints the chrome the shell draws itself, the × and the sheet behind it, in [colors]: what a scheme
     * changed while the sheet is up needs, as native Settings' Appearance does (#237). Each transition
     * paints them as it starts too.
     *
     * The tab's picture behind the sheet goes with a change of scheme, leaving the backdrop's own colour
     * under the scrim: taken as the sheet opened, it kept the old scheme in the band above the sheet,
     * near-black with dark icons over it after a switch to light (#481). Retaking it would picture a tab
     * hidden behind the sheet, mid-recomposition; on a phone the band is the screen's background either
     * way. What it costs: a tablet's tab beside the sheet, and the tab a drag or a predictive back
     * uncovers, show plain until the sheet closes.
     */
    fun repaint(colors: KettleColors) {
        if (place != Place.Tab && painted != null && colors != painted) {
            tabPicture = null
            // Settings, pushed, is where the scheme changes: what it covers was pictured in the old one.
            pushedUnder.replaceAll { null }
            stage.backdrop.setImageDrawable(null)
        }
        painted = colors
        stage.paint(colors)
    }

    /**
     * The frame the user still sees, pictured for the transition to slide out. Not for a tab switch, the
     * commonest move there is, at 8–28ms a picture (#177), nor for anything leaving the runner, which
     * covers whatever would be pictured.
     */
    override fun willMove(move: Move) {
        arrivingUnder = null
        when (move.from) {
            Place.Tab -> {
                if (move.to != Place.Tab) {
                    val tab = pictureOf(shell)
                    if (move.to == Place.Pushed) arrivingUnder = tab else tabPicture = tab
                }
            }

            // Held, the screen still shows the sheet before: the one live now hasn't composed.
            Place.Sheet -> {
                sheetPicture = livePicture()
            }

            // Full frame, so the live view's picture is the screen's: what a sheet or the next pushed
            // screen covers, and what slides out when this one goes.
            Place.Pushed -> {
                val pushed = livePicture()
                sheetPicture = pushed
                // A link to a sheet leaves the stack for the tabs, so the sheet covers no pushed screen.
                if (move.to == Place.Sheet && move.kind != MoveKind.Navigate) tabPicture = pushed
                if (move.to == Place.Pushed && move.kind == MoveKind.Push) arrivingUnder = pushed
            }

            // The runner covers whatever would be pictured.
            Place.Runner -> {}
        }
    }

    private fun livePicture() = if (shell.nativeFrames.holding) heldPicture else pictureOf(stage.live)

    /** Keeps [pushedUnder] one picture per pushed screen in the stack, as [move] changes it. */
    private fun file(move: Move) {
        when {
            move.to == Place.Tab -> {
                pushedUnder.clear()
            }

            move.kind == MoveKind.Navigate -> {
                pushedUnder.clear()
                if (move.to == Place.Pushed) pushedUnder += arrivingUnder
            }

            move.to == Place.Pushed && move.kind == MoveKind.Push && move.from != Place.Runner -> {
                pushedUnder += arrivingUnder
            }

            move.from == Place.Pushed && move.kind == MoveKind.Back -> {
                pushedUnder.removeLastOrNull()
            }

            // A replace, or a sheet opening onto a pushed screen: its place is the one it took.
            move.to == Place.Pushed && pushedUnder.isEmpty() -> {
                pushedUnder += null
            }
        }
        arrivingUnder = null
    }

    /** Where [move] arrives, shown at once, with its transition held until its route has composed. */
    override fun moved(move: Move) {
        // An arrival still composing goes first, so this one starts from where that one ends.
        shell.nativeFrames.flush()
        val transition = transitionBetween(move.from, move.to, move.kind)
        val from = place
        val to = move.to
        place = to
        file(move)
        backCallback.isEnabled = to.stacked
        shell.show(navigator.place)
        if (from == Place.Sheet && to == Place.Sheet) stage.layOutAsSheet()
        // Each picture is used once; the tab's stays until the sheet has gone.
        val sheet = sheetPicture.also { sheetPicture = null }
        val start = {
            heldPicture = null
            play(transition, sheet)
            if (to == Place.Tab || to == Place.Pushed) tabPicture = null
        }
        val route = navigator.sheet
        if (to.stacked && route != null) {
            heldPicture = sheet
            shell.nativeFrames.awaitComposed(route, start)
        } else {
            start()
        }
    }

    // Over detekt's complexity by one branch per transition: the table's other half, as transitionBetween.
    @Suppress("CyclomaticComplexMethod")
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
            Transition.PushIn -> motion.pushIn(pushedUnder.lastOrNull(), colors(), fade = false)
            Transition.PushFadeIn -> motion.pushIn(pushedUnder.lastOrNull(), colors(), fade = true)
            Transition.PushOut -> motion.pushOut(sheet, pushedUnder.lastOrNull(), fade = false)
            Transition.PushFadeOut -> motion.pushOut(sheet, pushedUnder.lastOrNull(), fade = true)
        }
        // Back on a pushed screen from a sheet or the runner, which leave it laid out as theirs.
        if (place == Place.Pushed && transition !in PUSH_MOTIONS) motion.rest(pushedUnder.lastOrNull())
    }

    /** Past a quarter of the frame, or flung down, it goes back; anything else snaps back. */
    private fun letGo(
        dy: Float,
        velocity: Float,
    ) {
        val density = shell.resources.displayMetrics.density
        if (dy > stage.frameHeight * DISMISS_FRACTION || velocity > FLING_DP_PER_S * density) {
            requestBack()
        } else {
            motion.settle()
        }
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

        /** How far across the frame a pushed screen follows a predictive back before it's let go. */
        const val PREDICTIVE_SLIDE = 0.1f

        val PUSH_MOTIONS = setOf(Transition.PushIn, Transition.PushFadeIn, Transition.PushOut, Transition.PushFadeOut)
        const val DISMISS_FRACTION = 0.25f
        const val FLING_DP_PER_S = 1000f
    }
}
