package com.casco.kettle.shell.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.casco.kettle.shell.R
import com.casco.kettle.shell.history.HistoryRoute
import com.casco.kettle.shell.list.UnderlineTabs
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Section
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.section
import com.casco.kettle.shell.stats.StatsRoute
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing

/**
 * The Progress tab (#414): "Progress", then Overview and Sessions as tabs, as Library's (#413), then the
 * section's screen. The section rides in the route (#411), so a link or a saved back stack lands on it,
 * and a tab is a navigate like any other. [content] draws a section, a parameter so a test can stand in
 * for the screens.
 *
 * The status bar's inset is taken here, once: the screens each take it too, and a parent's padding
 * leaves none for them, so they sit right under the tabs rather than a bar's height below them.
 */
@Composable
internal fun ProgressTab(
    route: Route,
    content: @Composable (Section) -> Unit = { if (it == Section.OVERVIEW) StatsRoute() else HistoryRoute() },
) {
    val navigator = LocalNavigator.current
    val section = route.section(Tab.Progress) ?: return
    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(start = Spacing.three, end = Spacing.three, top = Spacing.two),
        ) {
            Text(
                stringResource(R.string.tabs_progress),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = KettleTheme.colors.text,
            )
            UnderlineTabs(
                Section.of(Tab.Progress).map { it to stringResource(it.label) },
                section,
            ) { navigator.navigate(Route.of(it)) }
        }
        Box(Modifier.weight(1f)) { content(section) }
    }
}
