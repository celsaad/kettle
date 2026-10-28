package com.casco.kettle.shell.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.casco.kettle.shell.R
import com.casco.kettle.shell.programguide.ProgramGuideRoute
import com.casco.kettle.shell.progress.EXERCISE_PROGRESS_PATH
import com.casco.kettle.shell.progress.ExerciseProgressRoute
import com.casco.kettle.shell.stats.StatsRoute
import com.casco.kettle.shell.support.SupportRoute

/**
 * The tab bar's destinations, in bar order. [path] is the route RN knows the tab by, kept even once a
 * tab renders natively: RN still reports its own moves in those paths (a delete's `dismissTo`, the
 * runner's exit), and native has to know which tab they mean.
 */
enum class Tab(
    val path: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Workouts("/", R.string.tabs_workouts, R.drawable.tab_workouts),
    Library("/library", R.string.tabs_library, R.drawable.tab_library),
    History("/history", R.string.tabs_history, R.drawable.tab_history),
    Programs("/programs", R.string.tabs_programs, R.drawable.tab_programs),
    ;

    companion object {
        /** The tab RN's [pathname] is on, or null when something above the tabs is showing. */
        fun at(pathname: String): Tab? = entries.firstOrNull { it.path == pathname }
    }
}

/** How a destination renders: in the one RN surface, or as a Compose screen. */
sealed interface Screen {
    data object Rn : Screen

    /** [content] is given the params RN reported the route with, `id` or `exerciseId`. */
    class Native(
        val content: @Composable (params: Map<String, String>) -> Unit,
    ) : Screen
}

/**
 * The route registrations, by the path RN knows the route by. Anything not listed renders RN. Moving a
 * tab to native is this one line, `"/history" to Screen.Native { HistoryScreen() }`, plus an empty stub
 * route RN keeps at the same path (#190): RN stays the router, so its stack, back and route reports are
 * unchanged, and native draws the screen whenever RN reports that path: a tab's in `Destinations`, and
 * a modal route's in the sheet (`NativeSheet`, #197), with the same stub behind it.
 */
val shellRoutes: Map<String, Screen> =
    mapOf(
        "/support" to Screen.Native { SupportRoute() },
        "/program-guide" to Screen.Native { ProgramGuideRoute() },
        EXERCISE_PROGRESS_PATH to Screen.Native { params -> ExerciseProgressRoute(params) },
        "/analytics" to Screen.Native { StatsRoute() },
    )

/** How [path] renders. */
fun Map<String, Screen>.screenAt(path: String): Screen = this[path] ?: Screen.Rn
