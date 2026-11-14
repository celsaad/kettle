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
import androidx.compose.ui.res.stringResource
import com.casco.kettle.shell.list.UnderlineTabs
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing

/**
 * A tab with sections, Library (#413) and Progress (#414): its [header], then the sections of
 * [section]'s tab as underline tabs, then [content], the section's screen. The section rides in the
 * route (#411), so a link or a saved back stack lands on it, and a tab is a navigate like any other.
 *
 * The status bar's inset is taken here, once: the screens each take it too, and a parent's padding
 * leaves none for them, so they sit right under the tabs rather than a bar's height below them.
 */
@Composable
internal fun SectionedTabFrame(
    section: Section,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val navigator = LocalNavigator.current
    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(start = Spacing.three, end = Spacing.three, top = Spacing.two),
        ) {
            header()
            UnderlineTabs(Section.of(section.tab).map { it to stringResource(it.label) }, section) {
                navigator.navigate(Route.of(it))
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
