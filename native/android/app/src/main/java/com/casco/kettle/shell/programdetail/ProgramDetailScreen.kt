package com.casco.kettle.shell.programdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramPosition
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.programPosition
import com.casco.kettle.domain.programSessionsPerWeek
import com.casco.kettle.domain.programWeekNumbers
import com.casco.kettle.domain.programWorkoutIds
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.library.rememberProgramPosition
import com.casco.kettle.shell.list.EditIconButton
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.routeParams
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlin.time.Clock

/** The param that names Program detail's program, as Programs pushes it. */
const val PROGRAM_ID_PARAM = "programId"

/** What the route table draws at `Destination.ProgramDetail`, opened from a Programs row. */
@Composable
fun ProgramDetailRoute(params: Map<String, String>) {
    val navigator = LocalNavigator.current
    ProgramDetailScreen(
        params[PROGRAM_ID_PARAM],
        LocalLatestFiles.current,
        navigator::push,
        navigator::openRunner,
    )
}

/**
 * Program detail (#437): where the program stands and what comes next, its weeks as a grid of
 * position, not completion, as Today's strip (#412), and the selected week's days as rows. Every
 * training day keeps a Start, as agreed, so a day can still be run out of turn, and a tap on a day
 * opens its preview. [open] pushes a route and [startRunner] opens the runner (#302), both parameters
 * so a test hears each route opened and each start. [clock] is a parameter so a test can let rest
 * days pass.
 */
@Composable
fun ProgramDetailScreen(
    programId: String?,
    files: LatestFiles,
    open: (Route) -> Unit,
    startRunner: (RunnerStart) -> Unit,
    clock: Clock = Clock.System,
) {
    val library = files.library
    val program = library?.programs?.firstOrNull { it.id == programId }
    val sessions = files.sessionLog?.logged.orEmpty()
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
            verticalArrangement = Arrangement.spacedBy(Spacing.three),
        ) {
            // Nothing before the library is read, so a program that is there is never called gone.
            when {
                library == null -> {}

                program == null -> {
                    Text(
                        stringResource(R.string.programDetail_notFound),
                        style = KettleType.small,
                        color = KettleTheme.colors.textSecondary,
                    )
                }

                else -> {
                    // Today's own reading when Today runs this program, rest days served and all, so the two
                    // never name different next slots; the slot after its last session when it doesn't.
                    val today = rememberProgramPosition(files, clock)?.takeIf { it.program.id == program.id }
                    val own = remember(program, sessions) { programPosition(program, sessions) }
                    val position = today ?: own
                    Header(program, today) {
                        open(Route(Destination.ProgramEditor, mapOf("id" to program.id)))
                    }
                    val slot = SlotActions(program.id, library, open, startRunner)
                    position?.let { NextCard(it, slot) }
                    position?.let { ProgramWeeks(it, slot) }
                    WorkoutCount(program)
                }
            }
        }
    }
}

/**
 * What a slot can do, bound to its program: start it, or open its preview. [library] names its
 * workout and reads its overrides.
 */
internal class SlotActions(
    private val programId: String,
    val library: Library,
    private val open: (Route) -> Unit,
    private val startRunner: (RunnerStart) -> Unit,
) {
    private fun start(slot: ProgramWeek) =
        RunnerStart.Planned(SessionParams.ProgramSlot(programId, slot.week, slot.day))

    fun run(slot: ProgramWeek) = startRunner(start(slot))

    fun preview(slot: ProgramWeek) = open(Route(Destination.WorkoutPreview, start(slot).routeParams()))

    fun workoutName(slot: ProgramWeek.Training): String =
        library.workouts.firstOrNull { it.id == slot.workoutId }?.name ?: slot.workoutId
}

/** The name with Edit, then "Active, week 3 · 10 weeks · 6 sessions a week", the first part only when Today runs it. */
@Composable
private fun Header(
    program: Program,
    active: ProgramPosition?,
    onEdit: () -> Unit,
) {
    val colors = KettleTheme.colors
    val edit = stringResource(R.string.programDetail_edit)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // User data, verbatim.
            Text(
                program.name,
                Modifier.weight(1f).semantics { heading() },
                style = KettleType.subtitle,
                color = colors.text,
            )
            EditIconButton(edit, onEdit)
        }
        // Counted as the programs list counts them, so weeks 1, 2 and 4 are three weeks on both.
        val weeks = programWeekNumbers(program).size
        val parts =
            listOfNotNull(
                active?.let { stringResource(R.string.programs_active, it.weekNumber) },
                // "No weeks", not "0 weeks", as the programs list says it.
                if (weeks == 0) {
                    stringResource(R.string.programs_noWeeks)
                } else {
                    pluralText(R.plurals.programs_weekCount, R.string.programs_weekCount_zero, weeks)
                },
                programSessionsPerWeek(program)?.let {
                    pluralText(R.plurals.programs_sessionsPerWeek, R.string.programs_sessionsPerWeek_zero, it)
                },
            )
        Text(parts.joinToString(" · "), style = KettleType.default, color = colors.textSecondary)
    }
}

/** "36 workouts in this program": a count, for a program like Skill Mastery with dozens. */
@Composable
private fun WorkoutCount(program: Program) {
    val count = programWorkoutIds(program).size
    if (count == 0) return
    ListRowSeparator()
    Text(
        pluralText(R.plurals.programDetail_workoutCount, R.string.programDetail_workoutCount_zero, count),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
    )
}
