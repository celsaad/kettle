package com.casco.kettle.shell.nav

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A screen in the navigator's stack, keyed by where it sits, so it keeps its composition as the stack
 * grows over it.
 */
internal data class Layer(
    val index: Int,
    val route: Route,
)

/**
 * What a stack shows: the [sheet] on top, if the top is a sheet; the [pushed] screen at the top or
 * under the sheets, which covers the tab; and the pushed screen [beneath] that one, which a push slides
 * in over and a back uncovers. A sheet under another sheet shows nothing, so isn't composed: back to it
 * is a fresh screen, as it always was.
 */
internal data class Shown(
    val pushed: Layer?,
    val beneath: Layer?,
    val sheet: Layer?,
)

internal fun shownBy(stack: List<Route>): Shown {
    val top = stack.lastOrNull()
    val pushedAt = stack.indexOfLast { it.pushed }
    return Shown(
        pushed = if (pushedAt >= 0) Layer(pushedAt, stack[pushedAt]) else null,
        beneath = (pushedAt - 1).takeIf { it >= 0 && stack[it].pushed }?.let { Layer(it, stack[it]) },
        sheet = if (top != null && !top.pushed) Layer(stack.lastIndex, top) else null,
    )
}

/**
 * One transition playing: [transition], with the sheet or pushed screen it takes off the stack kept
 * composed and drawn until it has gone, and where a drag or a predictive back had left the sheet (or a
 * pushed screen) when it started, which it leaves from.
 */
internal class Motion(
    val transition: Transition,
    val leavingSheet: Layer? = null,
    val leavingPushed: Layer? = null,
    val fromY: Float = 0f,
    val fromScale: Float = 1f,
    val fromX: Float = 0f,
)

/**
 * How a layer is drawn at one frame of a transition: what its graphics layer is set to. It scales about
 * [origin], its bottom centre unless it says otherwise, as Material's predictive back for a sheet does.
 */
internal data class Look(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val origin: TransformOrigin = BottomCentre,
)

/** Halfway across, all the way down: as fractions of the layer's size. */
private val BottomCentre = TransformOrigin(pivotFractionX = 1f / 2, pivotFractionY = 1f)

/** What the sheet's frame measures by, in px: off the bottom of the screen from its top, and its top inset. */
internal data class Frame(
    val offscreen: Float,
    val top: Float,
)

/**
 * The sheet's transitions (#155's table, which [transitionBetween] decides), played in the shell's own
 * composition (#377). It hears every move the [navigator] makes, and the move's first frame draws the
 * route it arrives at with its transition at the start: the arriving screen composes in the same frame
 * the transition starts, so there is nothing to hold the window for and nothing to picture. What leaves
 * stays composed, live, through its exit. One plays at a time: starting the next ends the last.
 *
 * The hand on the sheet is here too: a grabber drag ([dragY]), and a predictive back's shrink
 * ([backScale]) or, on a pushed screen, its slide ([backX]). A transition that starts leaves from there.
 */
@Stable
internal class ShellMotion(
    private val navigator: Navigator,
    private val scope: CoroutineScope,
) : MoveListener {
    var motion by mutableStateOf<Motion?>(null)
        private set

    /** The transition's eased progress, 0 to 1: read in the draw phase, so a frame recomposes nothing. */
    val progress = mutableFloatStateOf(1f)
    val dragY = mutableFloatStateOf(0f)
    val backScale = mutableFloatStateOf(1f)
    val backX = mutableFloatStateOf(0f)

    /** Whether the sheet is settling back from a drag or a cancelled back, which a new grab takes at once. */
    var settling by mutableStateOf(false)
        private set

    private var playing: Job? = null
    private var settle: Job? = null
    private var before = Shown(null, null, null)

    override fun willMove(move: Move) {
        before = shownBy(navigator.stack)
    }

    override fun moved(move: Move) {
        val now = shownBy(navigator.stack)
        val transition = transitionBetween(move.from, move.to, move.kind)
        play(
            transition,
            leavingSheet = before.sheet.takeIf { transition in SHEET_LEAVES && it != now.sheet },
            leavingPushed =
                before.pushed.takeIf {
                    transition in PUSHED_LEAVES && it != now.pushed && it != now.beneath
                },
        )
    }

    private fun play(
        transition: Transition,
        leavingSheet: Layer?,
        leavingPushed: Layer?,
    ) {
        grab()
        val next =
            Motion(
                transition,
                leavingSheet,
                leavingPushed,
                fromY = dragY.floatValue,
                fromScale = backScale.floatValue,
                fromX = backX.floatValue,
            )
        dragY.floatValue = 0f
        backScale.floatValue = 1f
        backX.floatValue = 0f
        val timing = timingOf(transition) ?: return
        motion = next
        progress.floatValue = 0f
        playing =
            scope.launch {
                animate(0f, 1f, animationSpec = tween(timing.millis, easing = timing.easing)) { value, _ ->
                    progress.floatValue = value
                }
                if (motion === next) motion = null
            }
    }

    /** The transition playing, ended where it ends, and any settle with it: a hand has taken the sheet. */
    fun grab() {
        stop()
        settle?.cancel()
        settling = false
    }

    private fun stop() {
        playing?.cancel()
        playing = null
        motion = null
        progress.floatValue = 1f
    }

    /** The grabber's drag, [dy] px further. Never above the sheet's place. */
    fun drag(dy: Float) {
        dragY.floatValue = maxOf(0f, dragY.floatValue + dy)
    }

    /**
     * Let go of the grabber: past a quarter of the frame, or flung down, it goes back through [requestBack],
     * the system's back, which a screen's own back handler hears first; anything else settles back.
     */
    fun letGo(
        velocity: Float,
        frameHeight: Float,
        flingAt: Float,
        requestBack: () -> Unit,
    ) {
        if (dragY.floatValue > frameHeight * DISMISS_FRACTION || velocity > flingAt) requestBack() else settleBack()
    }

    /** Back where it belongs, from wherever a drag or a predictive back left it. */
    fun settleBack() {
        settle?.cancel()
        val fromY = dragY.floatValue
        val fromScale = backScale.floatValue
        val fromX = backX.floatValue
        if (fromY == 0f && fromScale == 1f && fromX == 0f) return
        settling = true
        settle =
            scope.launch {
                animate(0f, 1f, animationSpec = tween(SETTLE_MS, easing = DECELERATE)) { p, _ ->
                    dragY.floatValue = lerp(fromY, 0f, p)
                    backScale.floatValue = lerp(fromScale, 1f, p)
                    backX.floatValue = lerp(fromX, 0f, p)
                }
                settling = false
            }
    }

    /** A predictive back's progress, 0 to 1: a pushed screen starts to leave by its side, a sheet shrinks. */
    fun followBack(
        progress: Float,
        pushed: Boolean,
        endward: Float,
    ) {
        if (pushed) {
            backX.floatValue = PREDICTIVE_SLIDE * endward * progress
        } else {
            backScale.floatValue = 1 - PREDICTIVE_SHRINK * progress
        }
    }

    internal companion object {
        // Material 3's motion: emphasized easing for the sheet entering and leaving, standard elsewhere.
        val DECELERATE: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val ACCELERATE: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        val STANDARD: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        const val OPEN_MS = 400
        const val CLOSE_MS = 200
        const val SHARED_AXIS_MS = 300
        const val FADE_MS = 300
        const val SETTLE_MS = 250

        // Material 3's durations for a full-screen push and its pop: emphasized, longer in than out.
        const val PUSH_MS = 400
        const val POP_MS = 250

        /**
         * Ends with the runner's own entrance: on the S25+ (Android 16) the runner stayed clear for ~230ms
         * after the drawn frame, then faded in fully by 267ms (frame tiles, #155).
         */
        const val RUNNER_MS = 270

        /** Material's predictive back for a sheet shrinks it to 90%; a pushed screen follows 10% of the way. */
        const val PREDICTIVE_SHRINK = 0.1f
        const val PREDICTIVE_SLIDE = 0.1f

        /** A grabber let go past a quarter of the frame, or flung faster than [FLING_DP_PER_S], goes back. */
        const val DISMISS_FRACTION = 0.25f
        const val FLING_DP_PER_S = 1000f

        /** Material's shared axis and fade through: 30dp of travel, the incoming starting at 92%. */
        const val SHARED_AXIS_DP = 30f
        const val FADE_SCALE = 0.92f

        /** The point where the outgoing has faded out and the incoming starts to fade in. */
        const val FADE_SPLIT = 0.35f

        private val SHEET_LEAVES =
            setOf(Transition.Close, Transition.FadeOut, Transition.Forward, Transition.Backward, Transition.FadeThrough)
        private val PUSHED_LEAVES = setOf(Transition.PushOut, Transition.PushFadeOut)
    }
}

/** How long a transition plays and its easing; null for one that moves nothing. */
internal data class Timing(
    val millis: Int,
    val easing: Easing,
)
