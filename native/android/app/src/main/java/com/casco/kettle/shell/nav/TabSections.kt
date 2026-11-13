package com.casco.kettle.shell.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.casco.kettle.shell.R
import com.casco.kettle.shell.history.HistoryRoute
import com.casco.kettle.shell.library.LibraryRoute
import com.casco.kettle.shell.programs.ProgramsRoute
import com.casco.kettle.shell.settings.Segmented
import com.casco.kettle.shell.stats.StatsRoute
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing

// The Library and Progress tabs until their own tickets redesign them (#411): a switch over the screens
// the four-tab shell had, each drawn as it was. The section rides in the route, so a link or a saved back
// stack lands on it, and the switch is a navigate like any other.

/** Library: Exercises, today's Library tab, and Programs, which was a tab of its own. */
@Composable
internal fun LibraryTab(route: Route) =
    SectionedTab(Tab.Library, route) { section ->
        if (section == Section.PROGRAMS) ProgramsRoute() else LibraryRoute()
    }

/** Progress: Sessions, which was History, and Stats, which was a sheet off it. */
@Composable
internal fun ProgressTab(route: Route) =
    SectionedTab(Tab.Progress, route) { section ->
        if (section == Section.STATS) StatsRoute() else HistoryRoute()
    }

/**
 * The switch at the top, then the section's screen. The status bar's inset is taken here, once: the
 * screens each take it too, and a parent's padding leaves none for them, so they sit right under the
 * switch rather than a bar's height below it.
 */
@Composable
private fun SectionedTab(
    tab: Tab,
    route: Route,
    content: @Composable (String) -> Unit,
) {
    val navigator = LocalNavigator.current
    val section = route.section(tab) ?: Section.of(tab).first()
    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(start = Spacing.three, end = Spacing.three, top = Spacing.two, bottom = Spacing.two),
        ) {
            Segmented(Section.of(tab).map { it to labelOf(it) }, section) {
                navigator.navigate(Route(tab.destination, mapOf(SECTION to it)))
            }
        }
        Box(Modifier.weight(1f)) { content(section) }
    }
}

private fun labelOf(section: String): Int =
    when (section) {
        Section.PROGRAMS -> R.string.tabs_sections_programs
        Section.SESSIONS -> R.string.tabs_sections_sessions
        Section.STATS -> R.string.tabs_sections_stats
        else -> R.string.tabs_sections_exercises
    }
