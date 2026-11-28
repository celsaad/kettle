package com.casco.kettle.shell.nav

// What one transition hands the next (#377): which screen it plays out as it leaves, and where it had got
// the screen that stays when the next cuts it short. Every `when` names each transition, with no `else`.

/** Whether [this] takes the top sheet off and plays it out, live, through its exit. */
internal fun Transition.leavesSheet(): Boolean =
    when (this) {
        Transition.Close, Transition.FadeOut, Transition.Forward, Transition.Backward, Transition.FadeThrough -> true

        Transition.None, Transition.Open, Transition.FadeIn, Transition.Grow, Transition.Shrink, Transition.Release,
        Transition.PushIn, Transition.PushOut, Transition.PushFadeIn, Transition.PushFadeOut,
        -> false
    }

/** Whether [this] takes the pushed screen off and plays it out, live, through its exit. */
internal fun Transition.leavesPushed(): Boolean =
    when (this) {
        Transition.PushOut, Transition.PushFadeOut -> true

        Transition.None, Transition.Open, Transition.Close, Transition.Forward, Transition.Backward,
        Transition.FadeIn, Transition.FadeOut, Transition.FadeThrough, Transition.Grow, Transition.Shrink,
        Transition.Release, Transition.PushIn, Transition.PushFadeIn,
        -> false
    }

/**
 * Where a transition cut short at [p] has the sheet that stays shown, which the next one starts from:
 * null where the sheet it moves is leaving, or it moves none.
 */
internal fun heldFrame(
    motion: Motion,
    p: Float,
    frame: Frame,
): Look? =
    when (motion.transition) {
        Transition.Open, Transition.FadeIn, Transition.Forward, Transition.Backward, Transition.FadeThrough -> {
            frameLook(motion, p, frame, dragY = 0f, backScale = 1f)
        }

        Transition.None, Transition.Close, Transition.FadeOut, Transition.Grow, Transition.Shrink, Transition.Release,
        Transition.PushIn, Transition.PushOut, Transition.PushFadeIn, Transition.PushFadeOut,
        -> {
            null
        }
    }

/** Where a transition cut short at [p] has the pushed screen that stays shown, as [heldFrame] for a sheet. */
internal fun heldX(
    motion: Motion,
    p: Float,
    endward: Float,
): Float? =
    when (motion.transition) {
        Transition.PushIn -> pushedLook(motion, p, leaving = false, endward = endward, backX = 0f).translationX

        Transition.None, Transition.Open, Transition.Close, Transition.Forward, Transition.Backward,
        Transition.FadeIn, Transition.FadeOut, Transition.FadeThrough, Transition.Grow, Transition.Shrink,
        Transition.Release, Transition.PushOut, Transition.PushFadeIn, Transition.PushFadeOut,
        -> null
    }
