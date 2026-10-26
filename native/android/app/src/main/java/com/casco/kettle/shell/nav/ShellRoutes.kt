package com.casco.kettle.shell.nav

import androidx.compose.runtime.Composable

/**
 * The tab bar's destinations, in bar order. [path] is the route RN knows the tab by, kept even once a
 * tab renders natively: RN still reports its own moves in those paths (a delete's `dismissTo`, the
 * runner's exit), and native has to know which tab they mean.
 */
enum class Tab(
    val path: String,
) {
    Workouts("/"),
    Library("/library"),
    History("/history"),
    Programs("/programs"),
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
