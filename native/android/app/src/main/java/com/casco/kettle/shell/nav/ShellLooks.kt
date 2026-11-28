package com.casco.kettle.shell.nav

import androidx.compose.ui.graphics.TransformOrigin

/** [transition]'s timing: today's durations and Material 3's easing, as `SheetMotion` played them. */
internal fun timingOf(transition: Transition): Timing? =
    with(ShellMotion) {
        when (transition) {
            Transition.None, Transition.Release -> null
            Transition.Open -> Timing(OPEN_MS, DECELERATE)
            Transition.Close -> Timing(CLOSE_MS, ACCELERATE)
            Transition.FadeIn, Transition.FadeOut, Transition.FadeThrough -> Timing(FADE_MS, STANDARD)
            Transition.Forward, Transition.Backward -> Timing(SHARED_AXIS_MS, STANDARD)
            Transition.Grow, Transition.Shrink -> Timing(RUNNER_MS, STANDARD)
            Transition.PushIn -> Timing(PUSH_MS, DECELERATE)
            Transition.PushOut -> Timing(POP_MS, ACCELERATE)
            Transition.PushFadeIn, Transition.PushFadeOut -> Timing(FADE_MS, STANDARD)
        }
    }

/** Whether [transition] lays the sheet out full frame, square, as the runner it grows into or out of. */
internal fun Transition?.growing() = this == Transition.Grow || this == Transition.Shrink

/**
 * The sheet's frame at [p]: the sheet slides up over the scrim, down off it, fades through in place,
 * or (laid out full frame, [growing]) grows to the top under the runner and shrinks back. Otherwise it
 * sits where the hand left it, [dragY] down and shrunk to [backScale].
 *
 * Over detekt's complexity by one branch per transition: the table's other half, as [transitionBetween].
 */
@Suppress("CyclomaticComplexMethod")
internal fun frameLook(
    motion: Motion?,
    p: Float,
    frame: Frame,
    dragY: Float,
    backScale: Float,
): Look =
    when (motion?.transition) {
        Transition.Open -> {
            Look(translationY = (1 - p) * frame.offscreen)
        }

        Transition.FadeIn -> {
            Look(scale = lerp(ShellMotion.FADE_SCALE, 1f, p), alpha = fadeIn(p))
        }

        Transition.Close -> {
            Look(translationY = lerp(motion.fromY, frame.offscreen, p), scale = motion.fromScale)
        }

        Transition.FadeOut -> {
            Look(translationY = motion.fromY, scale = motion.fromScale, alpha = fadeOut(p))
        }

        // Back to its place from a predictive back's shrink, as the next sheet comes in.
        Transition.Forward, Transition.Backward, Transition.FadeThrough -> {
            Look(translationY = lerp(motion.fromY, 0f, p), scale = lerp(motion.fromScale, 1f, p))
        }

        Transition.Grow -> {
            Look(translationY = frame.top * (1 - p))
        }

        Transition.Shrink -> {
            Look(translationY = frame.top * p)
        }

        else -> {
            Look(translationY = dragY, scale = backScale)
        }
    }

/** The sheet's top corners at [p], as a fraction of their radius: flattened as the sheet grows into the runner. */
internal fun cornerFraction(
    motion: Motion?,
    p: Float,
): Float =
    when (motion?.transition) {
        Transition.Grow -> 1 - p
        Transition.Shrink -> p
        else -> 1f
    }

/** The scrim's opacity at [p]: in as a sheet opens, out as the last one closes. */
internal fun scrimAlpha(
    motion: Motion?,
    p: Float,
): Float =
    when (motion?.transition) {
        Transition.Open, Transition.FadeIn -> p
        Transition.Close, Transition.FadeOut -> 1 - p
        else -> 1f
    }

/**
 * A sheet's screen inside the sheet at [p], [leaving] or arriving: Material's shared axis X along
 * [shift] px either way, or fade through in place. The frame stays put around them.
 */
internal fun sheetContentLook(
    motion: Motion?,
    p: Float,
    leaving: Boolean,
    shift: Float,
): Look {
    val direction =
        when (motion?.transition) {
            Transition.Forward -> 1
            Transition.Backward -> -1
            Transition.FadeThrough -> 0
            else -> return Look()
        }
    val travel = shift * direction
    return if (leaving) {
        Look(translationX = 0f - travel * p, alpha = fadeOut(p))
    } else {
        // Fade through grows the incoming screen from its centre, inside the frame that stays put.
        val scale = if (direction == 0) lerp(ShellMotion.FADE_SCALE, 1f, p) else 1f
        Look(translationX = travel * (1 - p), alpha = fadeIn(p), scale = scale, origin = TransformOrigin.Center)
    }
}

/**
 * A pushed screen at [p] (#489): sliding in from the side, [endward] px off, over what it covers, or
 * back out from wherever a predictive back left it; fading through in place for a navigate. Otherwise
 * where a predictive back has it, [backX] along.
 */
internal fun pushedLook(
    motion: Motion?,
    p: Float,
    leaving: Boolean,
    endward: Float,
    backX: Float,
): Look =
    if (leaving) {
        when (motion?.transition) {
            Transition.PushOut -> Look(translationX = lerp(motion.fromX, endward, p))
            Transition.PushFadeOut -> Look(translationX = motion.fromX, alpha = fadeOut(p))
            else -> Look()
        }
    } else {
        when (motion?.transition) {
            Transition.PushIn -> Look(translationX = (1 - p) * endward)
            Transition.PushFadeIn -> Look(scale = lerp(ShellMotion.FADE_SCALE, 1f, p), alpha = fadeIn(p))
            else -> Look(translationX = backX)
        }
    }

internal fun lerp(
    from: Float,
    to: Float,
    p: Float,
) = from * (1 - p) + to * p

private fun fadeOut(p: Float) = (1 - p / ShellMotion.FADE_SPLIT).coerceIn(0f, 1f)

private fun fadeIn(p: Float) = ((p - ShellMotion.FADE_SPLIT) / (1 - ShellMotion.FADE_SPLIT)).coerceIn(0f, 1f)
