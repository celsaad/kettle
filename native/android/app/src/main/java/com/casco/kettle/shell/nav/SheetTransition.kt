package com.casco.kettle.shell.nav

/**
 * Where RN reports its router to be, as reportRoute in src/hooks/safe-brownfield.ts sends it. The two
 * share no code, so change both. [modal] is RN's word on whether the route is one of its modal
 * sheets, so the shell needs no copy of that list. [params] are the route's, so a native screen behind
 * an RN stub route reads `id` or `exerciseId` whoever opened it (M4).
 */
data class RouteReport(
    val pathname: String,
    val modal: Boolean,
    val params: Map<String, String> = emptyMap(),
) {
    val place: Place
        get() =
            when {
                modal -> Place.Sheet
                Tab.at(pathname) != null -> Place.Tab
                else -> Place.Runner
            }

    companion object {
        /** The report in a message from JS, or null when it isn't one. */
        fun from(message: Map<String, Any?>): RouteReport? {
            val pathname = (message["pathname"] as? String).takeIf { message["type"] == "route" }
            // A bundle from before the sheet sends no flag, and presents nothing as one.
            return pathname?.let {
                RouteReport(
                    it,
                    modal = message["modal"] == true,
                    params = stringParams(message["params"]),
                )
            }
        }

        /** The bridge hands a JS object over as a map; a bundle from before params sends none. */
        private fun stringParams(params: Any?): Map<String, String> =
            (params as? Map<*, *>)
                .orEmpty()
                .mapNotNull { (key, value) -> if (key is String && value is String) key to value else null }
                .toMap()
    }
}

/**
 * The three kinds of place RN can be, as far as the sheet goes. [Runner] is everything above the tabs
 * that isn't a sheet, which today is only the runner: RN presents it full screen itself until M6.
 */
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

    /** The sheet grows to full height and its corners flatten, under RN's own runner slide. */
    Grow,

    /** [Grow], reversed, as the runner slides away. */
    Shrink,

    /** The runner leaves for a tab: whatever sheet it grew from goes with it, with no animation. */
    Release,
}

/**
 * The transition from [from] to [to], told apart by [action], the type of the navigation action RN
 * sent with routeWillChange (null when none came, as after a reload). The app opens a sheet only by
 * pushing it and leaves only by going back, so everything else is a replace or the shell's own
 * steering, and fades through in place.
 */
fun transitionBetween(
    from: Place,
    to: Place,
    action: String?,
): Transition {
    val push = action == "PUSH"
    val back = action in BACK_ACTIONS
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

/**
 * React Navigation's going-back actions. POP_TO_TOP isn't one: only the shell's navigate sends it, as
 * `dismissAll`, and the shell's steering fades.
 */
private val BACK_ACTIONS = setOf("GO_BACK", "POP", "POP_TO")
