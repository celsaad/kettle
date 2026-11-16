package com.casco.kettle.shell.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseProgress
import com.casco.kettle.domain.ExerciseTrend
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.RecordKind
import com.casco.kettle.domain.TREND_WEEKS
import com.casco.kettle.domain.exerciseHistory
import com.casco.kettle.domain.exerciseTrend
import com.casco.kettle.domain.programWorkoutIds
import com.casco.kettle.domain.trendWindowStart
import com.casco.kettle.domain.workoutsUsing
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.unitsOrDevice
import com.casco.kettle.shell.history.entryResultText
import com.casco.kettle.shell.i18n.formatMonthDay
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.programdetail.PROGRAM_ID_PARAM
import com.casco.kettle.shell.progress.ChartCard
import com.casco.kettle.shell.progress.Dates
import com.casco.kettle.shell.progress.Headline
import com.casco.kettle.shell.progress.Readings
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Progress: Stats' own reading of this exercise (#413), the same selector, window and chart its Getting
 * stronger row opens, so detail and Stats can't show two numbers. Where Getting stronger has no row for
 * it, it says which of the four reasons it is, so a trained exercise isn't called untrained.
 */
@Composable
internal fun ProgressSection(
    exerciseId: String,
    files: LatestFiles,
    now: Instant,
    zone: TimeZone,
) {
    val log = files.sessionLog ?: return
    val exercises = files.library?.exercises.orEmpty()
    val trend =
        remember(log, exercises, exerciseId, now, zone) {
            exerciseTrend(log.logged, exercises, exerciseId, now, zone)
        }
    val colors = KettleTheme.colors
    // Flowing, not weighted: naming the kind makes the label long enough, in pt at large text, to
    // squeeze the heading into a break mid-word. It drops under the heading instead (#491).
    FlowRow(
        Modifier.fillMaxWidth().padding(top = Spacing.four),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Text(
            stringResource(R.string.exerciseDetail_progress),
            Modifier.alignByBaseline().semantics { heading() },
            style = KettleType.heading,
            color = colors.text,
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(windowLabel(trend), TREND_WEEKS),
            Modifier.alignByBaseline(),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
    if (trend !is ExerciseTrend.Charted) {
        Text(
            when (trend) {
                ExerciseTrend.Fixed -> stringResource(R.string.exerciseDetail_fixed)
                ExerciseTrend.Unmeasured -> stringResource(R.string.exerciseDetail_unmeasured)
                ExerciseTrend.TooFew -> stringResource(R.string.exerciseDetail_tooFew, TREND_WEEKS)
                else -> stringResource(R.string.exerciseDetail_notTrained, TREND_WEEKS)
            },
            Modifier.padding(top = Spacing.two),
            style = KettleType.small,
            color = colors.textSecondary,
        )
        return
    }
    val progress = trend.progress
    val readings = Readings(LocalResources.current, progress.kind, files.unitsOrDevice())
    val dates = Dates(LocalConfiguration.current.locales[0], zone)
    Headline(progress, readings, dates)
    ChartCard(
        progress,
        readings,
        dates,
        trendWindowStart(now, TREND_WEEKS, zone)..now,
        chartDescription(progress, readings, dates),
    )
}

/**
 * What the headline measures, over the window: the kind it's read as, since "Top set" would misname a
 * hold or a bodyweight exercise. Uncharted, there's no kind to name, only the window.
 */
private fun windowLabel(trend: ExerciseTrend): Int =
    when ((trend as? ExerciseTrend.Charted)?.progress?.kind) {
        RecordKind.HeaviestSet -> R.string.exerciseDetail_windowHeaviest
        RecordKind.MostReps -> R.string.exerciseDetail_windowMostReps
        RecordKind.LongestHold -> R.string.exerciseDetail_windowLongestHold
        RecordKind.MostRounds -> R.string.exerciseDetail_windowMostRounds
        null -> R.string.exerciseDetail_window
    }

/**
 * The chart in words, session by session, newest first: Stats' sheet has its list under the chart for
 * a screen reader to step through, and this page has none.
 */
@Composable
private fun chartDescription(
    progress: ExerciseProgress,
    readings: Readings,
    dates: Dates,
): String {
    val resources = LocalResources.current
    val newBest = stringResource(R.string.exerciseProgress_newBestTag)
    return progress.sessions.asReversed().joinToString("\n") { session ->
        val value = readings.reading(session.value) + if (session.newBest) " $newBest" else ""
        // Placeholders numbered alphabetically: date, then value.
        resources.getString(R.string.exerciseDetail_chartPoint, dates.fullDate(session.at), value)
    }
}

/** The last few times it was logged, newest first, as the editor's Recent listed them. None, no heading. */
@Composable
internal fun RecentSessions(
    exerciseId: String,
    files: LatestFiles,
    zone: TimeZone,
) {
    val log = files.sessionLog ?: return
    val units = files.unitsOrDevice()
    val history = remember(log, exerciseId, units) { exerciseHistory(log.logged, exerciseId, units) }
    if (history.isEmpty()) return
    val colors = KettleTheme.colors
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    SectionHeading(stringResource(R.string.exerciseDetail_recent))
    history.forEach { entry ->
        ListRowSeparator()
        ListRow {
            // A started_at that isn't an instant has no day to show.
            Text(
                entry.startedAt?.let { formatMonthDay(it, locale, zone) }.orEmpty(),
                Modifier.weight(1f),
                style = KettleType.rowLabel,
                color = colors.text,
            )
            Text(
                resources.entryResultText(entry.result, units),
                style = KettleType.smallMedium,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * The workouts that use it, which open in the editor, then the programs that run those, through the
 * first such workout, which open their detail. Names are the user's, verbatim.
 */
@Composable
internal fun UsedIn(
    exercise: Exercise,
    library: Library,
    open: (Route) -> Unit,
) {
    val workouts = remember(library, exercise.id) { workoutsUsing(library, exercise.id) }
    val programs =
        remember(library, workouts) {
            library.programs.mapNotNull { program ->
                val trains = programWorkoutIds(program)
                workouts.firstOrNull { it.id in trains }?.let { program to it }
            }
        }
    SectionHeading(stringResource(R.string.exerciseDetail_usedIn))
    if (workouts.isEmpty()) {
        Text(
            stringResource(R.string.exerciseDetail_notUsed),
            style = KettleType.small,
            color = KettleTheme.colors.textSecondary,
        )
        return
    }
    workouts.forEach { workout ->
        UsedInRow(workout.name, stringResource(R.string.exerciseDetail_workout)) {
            open(Route(Destination.WorkoutEditor, mapOf("id" to workout.id)))
        }
    }
    programs.forEach { (program, through) ->
        UsedInRow(program.name, stringResource(R.string.exerciseDetail_programThrough, through.name)) {
            open(Route(Destination.ProgramDetail, mapOf(PROGRAM_ID_PARAM to program.id)))
        }
    }
}

@Composable
private fun SectionHeading(text: String) =
    Text(
        text,
        Modifier.padding(top = Spacing.four, bottom = Spacing.one).semantics { heading() },
        style = KettleType.heading,
        color = KettleTheme.colors.text,
    )

/** "Full Body A", then "workout": one button, named by its two texts, opening what it names. */
@Composable
private fun UsedInRow(
    name: String,
    kind: String,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    ListRowSeparator()
    Box(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        ListRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = KettleType.rowLabel, color = colors.text)
                Text(kind, style = KettleType.small, color = colors.textSecondary)
            }
            Chevron()
        }
    }
}
