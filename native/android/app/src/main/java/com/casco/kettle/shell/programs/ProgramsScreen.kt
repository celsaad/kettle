package com.casco.kettle.shell.programs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.programWeekNumbers
import com.casco.kettle.domain.programWeeksWithNotes
import com.casco.kettle.domain.rankByName
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.NewItemButton
import com.casco.kettle.shell.list.NoResults
import com.casco.kettle.shell.list.SearchBar
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.ScrollsToTopOnReselect
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.programdetail.PROGRAM_DETAIL_PATH
import com.casco.kettle.shell.programdetail.PROGRAM_ID_PARAM
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.emptyStateCard
import kotlinx.coroutines.flow.Flow

/** What `shellRoutes` registers at `/programs`, the tab. */
@Composable
fun ProgramsRoute() = ProgramsScreen(LocalLatestFiles.current, LocalTabReselects.current, JsBridge::open)

/**
 * Programs (#232), the library's programs as rows, as RN's `(tabs)/programs.tsx`, now the empty stub
 * at this path: a name search, the program guide behind `?`, and New program, which is RN's editor
 * until the library flip. [open] is `JsBridge.open`, a parameter so a test hears each route opened.
 *
 * [reselected] carries taps on the tab already showing; one on Programs scrolls back to the top.
 */
@Composable
fun ProgramsScreen(
    files: LatestFiles,
    reselected: Flow<Tab>,
    open: (Stacking, String, Map<String, String>) -> Unit,
) {
    val library = files.library
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(Tab.Programs, reselected, listState)

    var query by rememberSaveable { mutableStateOf("") }
    val all = library?.programs.orEmpty()
    val matching = remember(all, query) { rankByName(all, query) { it.name } }
    val openGuide = { open(Stacking.Push, "/program-guide", emptyMap()) }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            state = listState,
            // The bottom clears the FAB, which floats 24dp off the bottom and is 52 tall; RN found a
            // 64dp inset left the last row, the one you scroll to reach, half under it.
            contentPadding =
                PaddingValues(
                    start = Spacing.three,
                    end = Spacing.three,
                    top = Spacing.two,
                    bottom = Spacing.six + Spacing.four,
                ),
        ) {
            item(key = "header") {
                Header(openGuide)
                // Keyed off the whole library rather than what's visible: hidden on an empty result, it
                // would take the field you need to clear the search with it.
                if (all.isNotEmpty()) {
                    SearchBar(
                        query,
                        { query = it },
                        pluralText(
                            R.plurals.programs_searchPlaceholder,
                            R.string.programs_searchPlaceholder_zero,
                            all.size,
                        ),
                    )
                }
                ListHeaderRule()
            }
            itemsIndexed(matching, key = { _, program -> program.id }) { index, program ->
                if (index > 0) ListRowSeparator()
                ProgramRow(program) { open(Stacking.Push, PROGRAM_DETAIL_PATH, mapOf(PROGRAM_ID_PARAM to program.id)) }
            }
            // Two empty states, told apart: the first-run card invites you to go read the YAML guide,
            // which is a strange answer to a mistyped program name. Neither before there is a library,
            // so the screen never says "none" before it has looked.
            when {
                library == null -> Unit
                all.isEmpty() -> item(key = "empty") { FirstRun(openGuide) }
                matching.isEmpty() -> item(key = "no-results") { NoResults(query) }
            }
        }
        NewItemButton(stringResource(R.string.programs_newProgram), Modifier.align(Alignment.BottomEnd)) {
            open(Stacking.Push, "/program-editor", emptyMap())
        }
    }
}

@Composable
private fun Header(onHelp: () -> Unit) {
    val colors = KettleTheme.colors
    val help = stringResource(R.string.programs_helpLabel)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.programs_title),
            Modifier.weight(1f).semantics { heading() },
            style = KettleType.subtitle,
            color = colors.text,
        )
        // Icon-only, so it carries the name its "?" can't give it.
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onHelp)
                .semantics { contentDescription = help },
            contentAlignment = Alignment.Center,
        ) {
            Text("?", Modifier.clearAndSetSemantics {}, style = KettleType.heading, color = colors.textSecondary)
        }
    }
}

/** One program: its name, its week range, and how many weeks carry notes or overrides, when any do. */
@Composable
private fun ProgramRow(
    program: Program,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    val withNotes = programWeeksWithNotes(program)
    Box(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        ListRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(program.name, style = KettleType.heading, color = colors.text)
                Text(weekRange(program), style = KettleType.small, color = colors.textSecondary)
                if (withNotes > 0) {
                    Text(
                        pluralText(R.plurals.programs_weeksWithNotes, R.string.programs_weeksWithNotes_zero, withNotes),
                        style = KettleType.small,
                        color = colors.textSecondary,
                    )
                }
            }
            Text("›", Modifier.clearAndSetSemantics {}, color = colors.textSecondary)
        }
    }
}

@Composable
private fun weekRange(program: Program): String {
    val weeks = programWeekNumbers(program)
    val first = weeks.firstOrNull() ?: return stringResource(R.string.programs_noWeeks)
    val last = weeks.last()
    return if (first == last) {
        stringResource(R.string.programs_week, first)
    } else {
        stringResource(R.string.programs_weeksRange, first, last)
    }
}

/** A library with no programs at all: what programs are, and where to learn to write one. */
@Composable
private fun FirstRun(onYaml: () -> Unit) {
    val colors = KettleTheme.colors
    Column(Modifier.emptyStateCard(), verticalArrangement = Arrangement.spacedBy(FirstRunGap)) {
        Text(stringResource(R.string.programs_emptyTitle), style = KettleType.heading, color = colors.text)
        Text(stringResource(R.string.programs_emptyBody), style = KettleType.small, color = colors.textSecondary)
        Text(
            stringResource(R.string.programs_emptyYamlLink),
            Modifier
                .padding(top = 2.dp)
                .heightIn(min = 48.dp)
                .clip(LinkShape)
                .border(1.dp, colors.border, LinkShape)
                .clickable(role = Role.Button, onClick = onYaml)
                .padding(LinkPadding)
                .wrapContentHeight(Alignment.CenterVertically),
            style = KettleType.smallMedium,
            color = colors.text,
        )
    }
}

private val FirstRunGap = Spacing.one + 2.dp
private val LinkShape = RoundedCornerShape(10.dp)
private val LinkPadding = PaddingValues(horizontal = Spacing.two + 2.dp, vertical = Spacing.one + 2.dp)
