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
    private val stage: SheetStage =
        SheetStage(content, shell.nativeSheet, requestBack, { motion.follow(it) }, ::letGo)
    private val motion: SheetMotion = SheetMotion(stage)

    private var place = Place.Tab

    /** The tab under the sheet, kept while the sheet is up: the runner shrinking back needs it again. */
    private var tabPicture: Bitmap? = null
    private var sheetPicture: Bitmap? = null

    /** While an arrival is held: the picture its transition slides out, which is still on screen. */
    private var heldPicture: Bitmap? = null

    /** Back while the sheet is up, with Material's predictive shrink first where it's on. */
    val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) = motion.cancel()

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                stage.scale(1 - PREDICTIVE_SHRINK * backEvent.progress)
            }

            override fun handleOnBackCancelled() = motion.settle()

            override fun handleOnBackPressed() {
                navigator.back()
                // A back the navigator drops moves nothing, and the shrink has to come undone.
                if (place == Place.Sheet && !motion.running) motion.settle()
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
     */
    fun repaint(colors: KettleColors) = stage.paint(colors)

    /**
     * The frame the user still sees, pictured for the transition to slide out. Not for a tab switch, the
     * commonest move there is, at 8–28ms a picture (#177), nor for anything leaving the runner, which
     * covers whatever would be pictured.
     */
    override fun willMove(move: Move) {
        when (move.from) {
            Place.Tab -> if (move.to != Place.Tab) tabPicture = pictureOf(shell)

            // Held, the screen still shows the sheet before: the one live now hasn't composed.
            Place.Sheet -> sheetPicture = if (shell.nativeFrames.holding) heldPicture else pictureOf(stage.live)

            Place.Runner -> Unit
        }
    }

    /** Where [move] arrives, shown at once, with its transition held until its route has composed. */
    override fun moved(move: Move) {
        // An arrival still composing goes first, so this one starts from where that one ends.
        shell.nativeFrames.flush()
        val transition = transitionBetween(move.from, move.to, move.kind)
        val from = place
        val to = move.to
        place = to
        backCallback.isEnabled = to == Place.Sheet
        shell.showLayer(navigator.layer)
        if (from == Place.Sheet && to == Place.Sheet) stage.layOutAsSheet()
        // Each picture is used once; the tab's stays until the sheet has gone.
        val sheet = sheetPicture.also { sheetPicture = null }
        val start = {
            heldPicture = null
            play(transition, sheet)
            if (to == Place.Tab) tabPicture = null
        }
        val route = navigator.sheet
        if (to == Place.Sheet && route != null) {
            heldPicture = sheet
            shell.nativeFrames.awaitComposed(route, start)
        } else {
            start()
        }
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
        const val DISMISS_FRACTION = 0.25f
        const val FLING_DP_PER_S = 1000f
    }
}
