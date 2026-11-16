package com.casco.kettle.shell.nav

/** The kinds of place the shell can be, as far as the sheet goes: a tab, a sheet, a pushed screen, the runner. */
enum class Place {
    Tab,
    Sheet,
    Pushed,
    Runner,
    ;

    /** Over the tabs in the navigator's stack, a sheet or a pushed screen: back is the sheet's to take. */
    val stacked: Boolean get() = this == Sheet || this == Pushed
}

/** How the sheet gets from one place to the next (#155's transitions table). */
enum class Transition {
    /** Nothing of the sheet's moves: a tab switch, or the runner opening over a tab. */
    None,

    /** The sheet slides up over the scrim and the picture of the tab. */
    Open,

    /** The sheet's last picture slides down over the live tab, and the scrim fades out. */
    Close,

    /** Shared axis X inside the one sheet: the old picture leaves, the live surface enters. */
    Forward,

    /** [Forward], reversed. */
    Backward,

    /** Fade through, in place: the sheet appears over the tab. */
    FadeIn,

    /** Fade through, in place: the sheet's picture goes, leaving the live tab. */
    FadeOut,

    /** Fade through, in place, from one sheet to the next. */
    FadeThrough,

    /** The sheet grows to full height and its corners flatten, under the runner. */
    Grow,

    /** [Grow], reversed, as the runner goes. */
    Shrink,

    /** The runner leaves for a tab or a pushed screen: whatever sheet it grew from goes with it, with no animation. */
    Release,

    /** A pushed screen slides in from the side, over the picture of what it covers (#489). */
    PushIn,

    /** The pushed screen's last picture slides back out to the side, over the live screen it covered. */
    PushOut,

    /** Fade through, in place: a pushed screen appears over what it covers. */
    PushFadeIn,

    /** Fade through, in place: the pushed screen's picture goes, leaving the live screen under it. */
    PushFadeOut,
}

/**
 * The transition from [from] to [to], told apart by [kind]. The app opens a sheet only by pushing it and
 * leaves only by going back, so everything else is a replace or a navigate from the tabs, and fades
 * through in place.
 *
 * Over detekt's complexity by one branch per pair of places: the table is the point, and splitting it
 * would hide a missing pair.
 */
@Suppress("CyclomaticComplexMethod")
fun transitionBetween(
    from: Place,
    to: Place,
    kind: MoveKind,
): Transition {
    val push = kind == MoveKind.Push
    val back = kind == MoveKind.Back
    return when (from to to) {
        Place.Tab to Place.Sheet -> {
            if (push) Transition.Open else Transition.FadeIn
        }

        Place.Sheet to Place.Tab -> {
            if (back) Transition.Close else Transition.FadeOut
        }

        // A sheet opened from a pushed screen covers it as it covers a tab, and closes back onto it.
        Place.Pushed to Place.Sheet -> {
            if (push) Transition.Open else Transition.FadeIn
        }

        // Nothing opens a pushed screen from a sheet today: a fade, rather than a motion of its own.
        Place.Sheet to Place.Pushed -> {
            if (back) Transition.Close else Transition.FadeOut
        }

        Place.Tab to Place.Pushed -> {
            if (push) Transition.PushIn else Transition.PushFadeIn
        }

        Place.Pushed to Place.Tab -> {
            if (back) Transition.PushOut else Transition.PushFadeOut
        }

        Place.Pushed to Place.Pushed -> {
            when {
                push -> Transition.PushIn
                back -> Transition.PushOut
                else -> Transition.PushFadeOut
            }
        }

        Place.Sheet to Place.Sheet -> {
            when {
                push -> Transition.Forward
                back -> Transition.Backward
                else -> Transition.FadeThrough
            }
        }

        Place.Sheet to Place.Runner -> {
            Transition.Grow
        }

        Place.Runner to Place.Sheet -> {
            Transition.Shrink
        }

        Place.Runner to Place.Tab, Place.Runner to Place.Pushed -> {
            Transition.Release
        }

        else -> {
            Transition.None
        }
    }
}
