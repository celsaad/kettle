package com.casco.kettle.shell.programdetail

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.overrideNote
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.RowStartButton
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing

/** Where Program detail is registered, and the param that names its program, as Programs pushes it. */
const val PROGRAM_DETAIL_PATH = "/program-detail"
const val PROGRAM_ID_PARAM = "programId"

/** What `shellRoutes` registers at [PROGRAM_DETAIL_PATH], opened from a Programs row. */
@Composable
fun ProgramDetailRoute(params: Map<String, String>) =
    ProgramDetailScreen(params[PROGRAM_ID_PARAM], LocalLatestFiles.current, JsBridge::open, JsBridge::back)

/**
 * One program's weeks (#233), as RN's `program-detail.tsx`, now the empty stub at this path. A training
 * week names its workout and starts it in RN's runner; a rest week says so and starts nothing. Edit is
 * RN's editor until the library flip. [open] is `JsBridge.open`, a parameter so a test hears each
 * route opened.
 */
@Composable
fun ProgramDetailScreen(
    programId: String?,
    files: LatestFiles,
    open: (Stacking, String, Map<String, String>) -> Unit,
    onClose: () -> Unit,
) {
    val library = files.library
    val program = library?.programs?.firstOrNull { it.id == programId }
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.four)
                .navigationBarsPadding(),
        ) {
            // Nothing before the library is read, so a program that is there is never called gone.
            when {
                library == null -> {
                    Unit
                }

                program == null -> {
                    Text(
                        stringResource(R.string.programDetail_notFound),
                        style = KettleType.small,
                        color = KettleTheme.colors.textSecondary,
                    )
                }

                else -> {
                    Weeks(program, library, open)
                    Close(onClose)
                }
            }
        }
    }
}

@Composable
private fun Weeks(
    program: Program,
    library: Library,
    open: (Stacking, String, Map<String, String>) -> Unit,
) {
    Header(program.name) { open(Stacking.Push, "/program-editor", mapOf("id" to program.id)) }
    // Starts the list the way every tab screen's does: without it the weeks begin under the title with
    // nothing marking where the heading stops.
    ListHeaderRule()
    // Stable, as JS's sort is, so the days of one week keep the file's order.
    program.weeks.sortedBy { it.week }.forEachIndexed { index, week ->
        if (index > 0) ListRowSeparator()
        WeekRow(week, library) {
            val params = mapOf(PROGRAM_ID_PARAM to program.id, "week" to week.week.toString())
            // Left out rather than empty, as expo-router drops RN's undefined `day`.
            open(Stacking.Push, "/session", params + listOfNotNull(week.day?.let { "day" to it }))
        }
    }
}

@Composable
private fun Header(
    name: String,
    onEdit: () -> Unit,
) {
    val colors = KettleTheme.colors
    val edit = stringResource(R.string.programDetail_edit)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // User data, verbatim.
        Text(name, Modifier.weight(1f).semantics { heading() }, style = KettleType.subtitle, color = colors.text)
        // Icon-only, so it carries the name its pencil can't give it.
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onEdit)
                .semantics { contentDescription = edit },
            contentAlignment = Alignment.Center,
        ) {
            Text("✎", Modifier.clearAndSetSemantics {}, style = KettleType.heading, color = colors.textSecondary)
        }
    }
}

/**
 * One week: its label, its workout or "Rest day", its notes and what its overrides do. A rest week gets
 * no start control at all rather than a disabled one, since it has nothing to start, and looks no
 * workout up, so its overrides (which the schema doesn't give it) can't be drawn either.
 */
@Composable
private fun WeekRow(
    week: ProgramWeek,
    library: Library,
    onStart: () -> Unit,
) {
    val colors = KettleTheme.colors
    val number = stringResource(R.string.programs_week, week.week)
    // An empty `day` is none, as RN tested it for truth: the schema takes `day: ""`.
    val label = week.day?.takeIf { it.isNotEmpty() }?.let { "$number · $it" } ?: number
    val workout =
        (week as? ProgramWeek.Training)?.let { training ->
            library.workouts.firstOrNull {
                it.id ==
                    training.workoutId
            }
        }
    ListRow {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = KettleType.heading, color = colors.text)
            when (week) {
                is ProgramWeek.RestDay -> Secondary(stringResource(R.string.programDetail_restDay))
                is ProgramWeek.Training -> Secondary(workout?.name ?: week.workoutId)
            }
            week.notes?.takeIf { it.isNotEmpty() }?.let { Secondary(it, FontStyle.Italic) }
            if (week is ProgramWeek.Training) {
                week.overrides.orEmpty().forEach { override ->
                    overrideLines(overrideNote(override, library, workout)).forEach { Secondary(it) }
                }
            }
        }
        if (week is ProgramWeek.Training) {
            RowStartButton(stringResource(R.string.programDetail_startAccessibility, label), onStart)
        }
    }
}

@Composable
private fun Secondary(
    text: String,
    fontStyle: FontStyle? = null,
) = Text(text, style = KettleType.small, color = KettleTheme.colors.textSecondary, fontStyle = fontStyle)

@Composable
private fun Close(onClick: () -> Unit) =
    Box(
        Modifier
            .padding(top = Spacing.four)
            .fillMaxWidth()
            // RN's 52px, as a floor: heightIn, never height, which clips at large text sizes.
            .heightIn(min = 52.dp)
            .clip(CloseShape)
            .border(1.5.dp, KettleTheme.colors.border, CloseShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.common_close),
            style = KettleType.heading,
            color = KettleTheme.colors.textSecondary,
        )
    }

private val CloseShape = RoundedCornerShape(15.dp)
