package com.casco.kettle.shell.nav

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.view.animation.PathInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnPreDraw
import com.casco.kettle.shell.theme.KettleColors

/** How one sheet gives way to the next inside the sheet: Material's shared axis X either way, or fade through. */
enum class Across(
    val direction: Int,
) {
    Forward(1),
    Back(-1),
    FadeThrough(0),
}

/**
 * The sheet's transitions (#155's table), each started at the first frame RN draws the route it goes
 * to. One runs at a time: starting the next ends the last.
 */
class SheetMotion(
    private val stage: SheetStage,
) {
    private var animator: ValueAnimator? = null

    val running: Boolean get() = animator?.isRunning == true

    /** An open waiting a frame to start; cleared by [cancel], so a cancelled one never starts. */
    private var waiting: Any? = null

    fun cancel() {
        waiting = null
        animator?.cancel()
    }

    /** Up over the scrim and [tab], sliding, or fading through in place when [fade]. */
    fun present(
        tab: Bitmap?,
        colors: KettleColors,
        fade: Boolean,
    ) = with(stage) {
        cancel()
        paint(colors)
        backdrop.setImageBitmap(tab)
        layOutAsSheet()
        show(backdrop, scrim, sheetBack, chrome)
        val offscreen = frameHeight
        val frame = { p: Float ->
            // Reset on every present: a closing sheet leaves the scrim faded out.
            scrim.alpha = p
            if (fade) {
                sheetViews.forEach { it.alpha = fadeIn(p) }
                scale(lerp(FADE_SCALE, 1f, p))
            } else {
                translate((1 - p) * offscreen)
            }
        }
        // Hidden, over the tab's picture, which looks exactly like the tab, for one more frame: on a
        // device the drawn signal was still a frame early for a route RN adds, and an open is the one
        // transition whose first frame shows the live surface uncovered (frame tiles, #155).
        frame(0f)
        val token = Any()
        waiting = token
        surface.doOnPreDraw {
            if (waiting ===
                token
            ) {
                animate(if (fade) FADE_MS else OPEN_MS, if (fade) STANDARD else DECELERATE, frame = frame)
            }
        }
    }

    /**
     * The sheet's last picture down over the live tab, or fading out in place when [fade], as the scrim
     * fades. It leaves from wherever a drag or a predictive back left the sheet.
     */
    fun dismiss(
        sheet: Bitmap?,
        fade: Boolean,
    ) = with(stage) {
        cancel()
        val from = surface.translationY
        val scale = surface.scaleX
        layOutFull()
        backdrop.setImageDrawable(null)
        hide(backdrop, sheetBack, chrome)
        if (sheet == null) {
            hide(scrim)
            return@with
        }
        showLeaving(sheet)
        leaving.translationY = from
        leaving.scaleX = scale
        leaving.scaleY = scale
        val offscreen = frameHeight
        animate(if (fade) FADE_MS else CLOSE_MS, if (fade) STANDARD else ACCELERATE, onEnd = ::clearLeaving) { p ->
            scrim.alpha = 1 - p
            if (fade) leaving.alpha = fadeOut(p) else leaving.translationY = lerp(from, offscreen, p)
        }
    }

    /** From one sheet to the next inside the one sheet. The old picture leaves, the live surface enters. */
    fun across(
        sheet: Bitmap?,
        how: Across,
    ) = with(stage) {
        cancel()
        if (sheet == null) return@with
        showLeaving(sheet)
        val shift = SHARED_AXIS_DP * surface.resources.displayMetrics.density * how.direction
        animate(if (how == Across.FadeThrough) FADE_MS else SHARED_AXIS_MS, STANDARD, onEnd = ::clearLeaving) { p ->
            leaving.alpha = fadeOut(p)
            leaving.translationX = -shift * p
            surface.alpha = fadeIn(p)
            surface.translationX = shift * (1 - p)
            if (how == Across.FadeThrough) {
                surface.scaleX = lerp(FADE_SCALE, 1f, p)
                surface.scaleY = surface.scaleX
            }
        }
    }

    /**
     * Into the runner, which RN slides up inside the surface: the sheet grows to full height and its
     * corners flatten over the same length. One layout change up front, then translation alone, so RN
     * lays the runner out once at its full size; the bottom is clipped by the frame until it arrives.
     */
    fun grow() =
        with(stage) {
            cancel()
            val top = top.toFloat()
            hide(chrome)
            layOutFull()
            clip(true)
            animate(RUNNER_MS, STANDARD, onEnd = {
                hide(backdrop, scrim, sheetBack)
                clip(false)
            }) { p ->
                shapedViews.forEach { it.translationY = top * (1 - p) }
                round(radius * (1 - p))
            }
        }

    /** [grow], reversed as RN slides the runner away; the sheet takes its own size back at the end. */
    fun shrink(
        tab: Bitmap?,
        colors: KettleColors,
    ) = with(stage) {
        cancel()
        paint(colors)
        val top = top.toFloat()
        backdrop.setImageBitmap(tab)
        show(backdrop, scrim, sheetBack)
        scrim.alpha = 1f
        clip(true)
        animate(RUNNER_MS, STANDARD, onEnd = {
            layOutAsSheet()
            show(chrome)
        }) { p ->
            shapedViews.forEach { it.translationY = top * p }
            round(radius * p)
        }
    }

    /** Out of the runner to a tab: RN slides the runner away itself, and nothing of the sheet is left. */
    fun release() =
        with(stage) {
            cancel()
            layOutFull()
            hide(backdrop, scrim, sheetBack, chrome)
            backdrop.setImageDrawable(null)
        }

    /** The grabber's drag, [dy] px down from where it started. */
    fun follow(dy: Float) {
        cancel()
        stage.translate(dy)
    }

    /** Back where it belongs, from wherever a drag or a predictive back left it. */
    fun settle() {
        val from = stage.surface.translationY
        val scale = stage.surface.scaleX
        animate(SETTLE_MS, DECELERATE) { p ->
            stage.translate(lerp(from, 0f, p))
            stage.scale(lerp(scale, 1f, p))
        }
    }

    /**
     * One transition at a time. Cancelling one, by starting the next or by a back gesture or a drag,
     * still runs its [onEnd], so the views land in its end state before anything moves them again.
     */
    private fun animate(
        duration: Long,
        easing: TimeInterpolator,
        onEnd: () -> Unit = {},
        frame: (Float) -> Unit,
    ) {
        cancel()
        frame(0f)
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = easing
                addUpdateListener { frame(it.animatedValue as Float) }
                doOnEnd { onEnd() }
                start()
            }
    }

    private companion object {
        // Material 3's motion: emphasized easing for the sheet entering and leaving, standard elsewhere.
        val DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
        val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)
        const val OPEN_MS = 400L
        const val CLOSE_MS = 200L
        const val SHARED_AXIS_MS = 300L
        const val FADE_MS = 300L
        const val SETTLE_MS = 250L

        /**
         * Ends with the runner's own entrance, which is the platform's open animation, not a slide: on
         * the S25+ (Android 16) the runner stays clear for ~230ms after the drawn frame, then fades in
         * fully by 267ms (frame tiles, #155). Another Android version may time it differently.
         */
        const val RUNNER_MS = 270L

        /** Material's shared axis and fade through: 30dp of travel, the incoming starting at 92%. */
        const val SHARED_AXIS_DP = 30f
        const val FADE_SCALE = 0.92f

        /** The point where the outgoing has faded out and the incoming starts to fade in. */
        const val FADE_SPLIT = 0.35f

        fun lerp(
            from: Float,
            to: Float,
            p: Float,
        ) = from + (to - from) * p

        fun fadeOut(p: Float) = (1 - p / FADE_SPLIT).coerceIn(0f, 1f)

        fun fadeIn(p: Float) = ((p - FADE_SPLIT) / (1 - FADE_SPLIT)).coerceIn(0f, 1f)
    }
}
