package com.casco.kettle.shell.programs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.programSessionsPerWeek
import com.casco.kettle.domain.programWeekNumbers
import com.casco.kettle.domain.rankByName
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.library.rememberProgramPosition
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.NoResults
import com.casco.kettle.shell.list.SearchBar
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.ScrollsToTopOnReselect
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.programdetail.PROGRAM_ID_PARAM
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.emptyStateCard
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/** What the Library tab draws in its Programs section (#411). */
@Composable
fun ProgramsRoute() = ProgramsScreen(LocalLatestFiles.current, LocalTabReselects.current, LocalNavigator.current::push)

/**
 * Programs (#232), the library's programs as rows, under Library's tabs (#413), which hold its title
 * and New program: a name search, the rows, and the program guide as a link under them. The program
 * Today runs reads "Active, week 3". [open] pushes a route, a parameter so a test hears each one opened.
 *
 * [reselected] carries taps on the tab already showing; one on Programs scrolls back to the top.
 */
@Composable
fun ProgramsScreen(
    files: LatestFiles,
    reselected: Flow<Tab>,
    open: (Route) -> Unit,
    clock: Clock = Clock.System,
) {
    val library = files.library
    val position = rememberProgramPosition(files, clock)
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(Tab.Library, reselected, listState)

    var query by rememberSaveable { mutableStateOf("") }
    val all = library?.programs.orEmpty()
    val matching = remember(all, query) { rankByName(all, query) { it.name } }
    val openGuide = { open(Route(Destination.ProgramGuide)) }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = Spacing.three,
                    end = Spacing.three,
                    top = Spacing.two,
                    bottom = Spacing.four,
                ),
        ) {
            item(key = "header") {
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
                val week = position?.takeIf { it.program.id == program.id }?.weekNumber
                ProgramRow(program, week) {
                    open(Route(Destination.ProgramDetail, mapOf(PROGRAM_ID_PARAM to program.id)))
                }
            }
            if (matching.isNotEmpty()) item(key = "guide") { GuideLink(openGuide) }
            // Two empty states, told apart: the first-run card invites you to go read the YAML guide,
            // which is a strange answer to a mistyped program name. Neither before there is a library,
            // so the screen never says "none" before it has looked.
            when {
                library == null -> Unit
                all.isEmpty() -> item(key = "empty") { FirstRun(openGuide) }
                matching.isEmpty() -> item(key = "no-results") { NoResults(query) }
            }
        }
    }
}

/** "Writing a program by hand", the guide the `?` in the old header opened. */
@Composable
private fun GuideLink(onClick: () -> Unit) {
    Box(Modifier.padding(top = Spacing.two)) {
        TextAction(
            stringResource(R.string.programs_guideLink),
            KettleType.smallMedium,
            KettleTheme.colors.accentText,
            onClick,
        )
    }
}

/**
 * One program: its name, then "Active, week 3" in the text's ink when Today runs it at [activeWeek],
 * else how many weeks it runs, then how many sessions a week when every week holds the same number.
 */
@Composable
private fun ProgramRow(
    program: Program,
    activeWeek: Long?,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    val weeks = programWeekNumbers(program).size
    val lead =
        when {
            activeWeek != null -> stringResource(R.string.programs_active, activeWeek)
            weeks == 0 -> stringResource(R.string.programs_noWeeks)
            else -> pluralText(R.plurals.programs_weekCount, R.string.programs_weekCount_zero, weeks)
        }
    val sessions =
        programSessionsPerWeek(program)?.let {
            pluralText(R.plurals.programs_sessionsPerWeek, R.string.programs_sessionsPerWeek_zero, it)
        }
    val line = sessions?.let { stringResource(R.string.programs_rowDetail, LEAD_MARK, it) } ?: LEAD_MARK
    val at = line.indexOf(LEAD_MARK)
    Box(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        ListRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(program.name, style = KettleType.heading, color = colors.text)
                Text(
                    buildAnnotatedString {
                        append(line.replace(LEAD_MARK, lead))
                        // Placed by position, so a language can put the two parts in its own order.
                        if (activeWeek != null && at >= 0) {
                            addStyle(
                                SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold),
                                at,
                                at + lead.length,
                            )
                        }
                    },
                    style = KettleType.small,
                    color = colors.textSecondary,
                )
            }
        }
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

/** A private-use character standing in for the row's lead in its format until it's placed. */
private const val LEAD_MARK = "\uE000"
