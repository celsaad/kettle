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
 * What a stack shows: the [sheet] on top, if the top is a sheet; the [pushed] screen at the top or
 * under the sheets, which covers the tab; and the pushed screen [beneath] that one, which a push slides
 * in over and a back uncovers. A sheet under another sheet shows nothing, so isn't composed: back to it
 * is a fresh screen, as it always was. A pushed screen under a sheet, or under another pushed screen,
 * stays composed and live.
 */
internal data class Shown(
    val pushed: StackEntry?,
    val beneath: StackEntry?,
    val sheet: StackEntry?,
)

/**
 * The sheet directly under a pushed screen on top, which only a link opens (#569): kept in its frame
 * under that screen, not hidden, so the screen leaves off it as it would off a tab. Null otherwise.
 */
internal fun sheetUnderPushed(stack: List<StackEntry>): StackEntry? =
    stack.getOrNull(stack.size - 2)?.takeIf { stack.last().route.pushed && !it.route.pushed }

internal fun shownBy(stack: List<StackEntry>): Shown {
    val top = stack.lastOrNull()
    val pushedAt = stack.indexOfLast { it.route.pushed }
    return Shown(
        pushed = stack.getOrNull(pushedAt),
        beneath = stack.getOrNull(pushedAt - 1)?.takeIf { pushedAt > 0 && it.route.pushed },
        sheet = top?.takeIf { !it.route.pushed },
    )
}

/**
 * One transition playing: [transition], with the sheet or pushed screen it takes off the stack kept
 * composed and drawn until it has gone, and where the sheet (or a pushed screen) was when it started,
 * which it leaves from: where a drag or a predictive back had it, or where the transition it cut short
 * had got it to.
 */
internal class Motion(
    val transition: Transition,
    val leavingSheet: StackEntry? = null,
    val leavingPushed: StackEntry? = null,
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
 * the transition starts, so there is nothing to hold the window for. What leaves stays composed, live,
 * through its exit. One plays at a time: starting the next takes over from where the last had got to.
 *
 * The hand on the sheet is here too: a grabber drag ([dragY]), and a predictive back's shrink
 * ([backScale]) or, on a pushed screen, its slide ([backX]). A transition that starts leaves from there.
 */
@Stable
internal class ShellMotion(
    private val navigator: Navigator,
    private val scope: CoroutineScope,
) : MoveListener {
    /** The transition playing, or null when nothing is. */
    var playing by mutableStateOf<Motion?>(null)
        private set

    /** The transition's eased progress, 0 to 1: read in the draw phase, so a frame recomposes nothing. */
    val progress = mutableFloatStateOf(1f)
    val dragY = mutableFloatStateOf(0f)
    val backScale = mutableFloatStateOf(1f)
    val backX = mutableFloatStateOf(0f)

    /** Whether the sheet is settling back from a drag or a cancelled back, which a new grab takes at once. */
    var settling by mutableStateOf(false)
        private set

    /** The sheet's frame as last laid out, and how far off a pushed screen goes: where a cut-short one had got to. */
    var frame = Frame(offscreen = 0f, top = 0f)
    var endward = 0f

    private var player: Job? = null
    private var settle: Job? = null
    private var before = Shown(null, null, null)

    override fun willMove(move: Move) {
        before = shownBy(navigator.entries)
    }

    override fun moved(move: Move) {
        val now = shownBy(navigator.entries)
        val transition = transitionBetween(move.from, move.to, move.kind)
        play(
            transition,
            leavingSheet = before.sheet.takeIf { transition.leavesSheet() && it != now.sheet },
            leavingPushed =
                before.pushed.takeIf {
                    transition.leavesPushed() && it != now.pushed && it != now.beneath
                },
        )
    }

    private fun play(
        transition: Transition,
        leavingSheet: StackEntry?,
        leavingPushed: StackEntry?,
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
        grabbedAt = 0f
        val timing = timingOf(transition) ?: return
        playing = next
        progress.floatValue = 0f
        player =
            scope.launch {
                animate(0f, 1f, animationSpec = tween(timing.millis, easing = timing.easing)) { value, _ ->
                    progress.floatValue = value
                }
                if (playing === next) playing = null
            }
    }

    /**
     * A hand has taken the sheet, or the next move has: the transition playing stops where it has got
     * to, which becomes where the hand has the sheet, and any settle stops with it.
     */
    fun grab() {
        val cut = playing != null
        stop()
        settle?.cancel()
        settling = false
        grabbedAt = if (cut) dragY.floatValue else minOf(grabbedAt, dragY.floatValue)
    }

    /**
     * Where the sheet was when the hand took it from a transition: a cut-short Open leaves it part
     * way down, and only the hand's own travel from there counts towards closing it. A grab of a settle-back
     * or of a sheet at rest keeps the origin it had, never further down than the sheet is now: a drag in two
     * strokes still adds up from rest, and a settling cut-short Open still counts from where it was caught.
     */
    private var grabbedAt = 0f

    private fun stop() {
        val cut = playing
        if (cut != null) {
            val p = progress.floatValue
            heldFrame(cut, p, frame)?.let {
                dragY.floatValue = it.translationY
                backScale.floatValue = it.scale
            }
            heldX(cut, p, endward)?.let { backX.floatValue = it }
        }
        player?.cancel()
        player = null
        playing = null
        progress.floatValue = 1f
    }

    /** The grabber's drag, [dy] px further. Never above the sheet's place. */
    fun drag(dy: Float) {
        dragY.floatValue = maxOf(0f, dragY.floatValue + dy)
    }

    /**
     * Let go of the grabber: dragged a quarter of the frame from where it was grabbed, or flung down, it
     * goes back through [requestBack], the system's back, which a screen's own back handler hears first;
     * anything else settles back to rest.
     */
    fun letGo(
        velocity: Float,
        frameHeight: Float,
        flingAt: Float,
        requestBack: () -> Unit,
    ) {
        val travel = dragY.floatValue - grabbedAt
        if (travel > frameHeight * DISMISS_FRACTION || velocity > flingAt) requestBack() else settleBack()
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

    /**
     * A predictive back's progress, 0 to 1: a pushed screen starts to leave by its side, a sheet shrinks,
     * from wherever the hand or a cut-short transition had it.
     */
    fun followBack(
        progress: Float,
        pushed: Boolean,
        startX: Float,
        startScale: Float,
    ) {
        if (pushed) {
            backX.floatValue = startX + PREDICTIVE_SLIDE * endward * progress
        } else {
            backScale.floatValue = startScale * (1 - PREDICTIVE_SHRINK * progress)
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
    }
}

/** How long a transition plays and its easing; null for one that moves nothing. */
internal data class Timing(
    val millis: Int,
    val easing: Easing,
)
