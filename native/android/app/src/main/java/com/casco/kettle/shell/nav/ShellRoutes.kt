package com.casco.kettle.shell.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.casco.kettle.shell.R

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

    class Native(
        val content: @Composable () -> Unit,
    ) : Screen
}

/**
 * The route registrations. Moving a tab to native is this one line, `Tab.History to Screen.Native {
 * HistoryScreen() }`: the bar, the back stack and RN's route reports already go through [Tab].
 */
val shellRoutes: Map<Tab, Screen> = Tab.entries.associateWith { Screen.Rn }
