package com.casco.kettle.shell.progress

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.casco.kettle.shell.R
import com.casco.kettle.shell.history.HistoryRoute
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Section
import com.casco.kettle.shell.nav.SectionedTabFrame
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.section
import com.casco.kettle.shell.stats.StatsRoute
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType

/**
 * The Progress tab (#414): "Progress" over Overview and Sessions. [content] draws a section, a parameter
 * so a test can stand in for the screens.
 */
@Composable
internal fun ProgressTab(
    route: Route,
    content: @Composable (Section) -> Unit = { if (it == Section.OVERVIEW) StatsRoute() else HistoryRoute() },
) {
    val section = route.section(Tab.Progress) ?: return
    SectionedTabFrame(
        section,
        header = {
            Text(
                stringResource(R.string.tabs_progress),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = KettleTheme.colors.text,
            )
        },
    ) { content(section) }
}
