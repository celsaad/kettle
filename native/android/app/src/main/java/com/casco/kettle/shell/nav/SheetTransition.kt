package com.casco.kettle.shell.nav

/** The three kinds of place the shell can be, as far as the sheet goes: a tab, a sheet, the runner. */
enum class Place { Tab, Sheet, Runner }

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

    /** The runner leaves for a tab: whatever sheet it grew from goes with it, with no animation. */
    Release,
}

/**
 * The transition from [from] to [to], told apart by [kind]. The app opens a sheet only by pushing it and
 * leaves only by going back, so everything else is a replace or a navigate from the tabs, and fades
 * through in place.
 */
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

        Place.Runner to Place.Tab -> {
            Transition.Release
        }

        else -> {
            Transition.None
        }
    }
}
