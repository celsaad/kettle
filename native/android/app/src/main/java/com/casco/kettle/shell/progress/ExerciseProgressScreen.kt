package com.casco.kettle.shell.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ExerciseProgress
import com.casco.kettle.domain.TREND_WEEKS
import com.casco.kettle.domain.exerciseName
import com.casco.kettle.domain.exerciseProgress
import com.casco.kettle.domain.trendWindowStart
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.formatFullDate
import com.casco.kettle.shell.i18n.formatMonthDay
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.util.Locale

/**
 * Where exercise progress is registered, and the param that names its exercise: the path and key RN's
 * stub route is pushed with. Shared so Stats' rows and this screen can't drift apart on a rename.
 */
const val EXERCISE_PROGRESS_PATH = "/exercise-progress"
const val EXERCISE_ID_PARAM = "exerciseId"

/** What `shellRoutes` registers at [EXERCISE_PROGRESS_PATH], opened from a Stats row. */
@Composable
fun ExerciseProgressRoute(params: Map<String, String>) {
    // Once per opening, as RN's screen read its clock once per mount: the window doesn't creep under
    // a reader who leaves the sheet up.
    val now = remember { Clock.System.now() }
    ExerciseProgressScreen(params[EXERCISE_ID_PARAM], LocalLatestFiles.current, now, TimeZone.currentSystemDefault())
}

/**
 * One exercise's progress (#200), opened by tapping its row in Getting stronger: where it is now, how
 * far it moved, a chart of every session in the window, and the sessions themselves. Ported from RN's
 * `exercise-progress.tsx`, now the empty stub at this path.
 *
 * **It reads the row it was opened from.** Same selector, same window ([TREND_WEEKS]), same measure,
 * the same new-best rule, so the number on the row and the number at the top here are one reading,
 * with no second definition of "getting stronger" to drift. The cost is that it can't show more than
 * the row can: no longer window, and no chart for what the row leaves out (interval work, fixed-length
 * holds). Both would need the row to change first. **No exercise picker**, either: the row chose.
 */
@Composable
fun ExerciseProgressScreen(
    exerciseId: String?,
    files: LatestFiles,
    now: Instant,
    zone: TimeZone,
) {
    val log = files.sessionLog
    val exercises = files.library?.exercises.orEmpty()
    val locale = LocalConfiguration.current.locales[0]
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(locale)
    val lookup =
        remember(log, exercises, exerciseId, now, zone) {
            when (log) {
                null -> {
                    RowLookup.NotRead
                }

                else -> {
                    val view = exerciseProgress(log.sessions, exercises, TREND_WEEKS, now, zone)
                    (view.movers + view.steady)
                        .firstOrNull { it.exerciseId == exerciseId }
                        ?.let(RowLookup::Found) ?: RowLookup.NoRow
                }
            }
        }

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
                .padding(bottom = Spacing.six)
                .navigationBarsPadding(),
        ) {
            // User data, verbatim, and shown even with nothing to chart, so the line below is about
            // something rather than floating on its own.
            if (exerciseId != null) {
                Text(
                    exerciseName(exercises, exerciseId),
                    Modifier.semantics { heading() },
                    style = KettleType.subtitle,
                    color = KettleTheme.colors.text,
                )
            }
            if (lookup == RowLookup.NoRow) NotFound()
            if (lookup is RowLookup.Found) {
                val progress = lookup.progress
                val readings = Readings(LocalResources.current, progress.kind, units)
                val dates = Dates(locale, zone)
                Headline(progress, readings, dates)
                // The window the selector measured, so the axis starts where the row's "since" does.
                ChartCard(progress, readings, dates, trendWindowStart(now, TREND_WEEKS, zone)..now)
                SessionList(progress, readings, dates)
            }
        }
    }
}

/**
 * The row the sheet was opened for. [NotRead] shows nothing, so the sheet never says "nothing to
 * chart" before it has looked.
 */
private sealed interface RowLookup {
    data object NotRead : RowLookup

    data object NoRow : RowLookup

    data class Found(
        val progress: ExerciseProgress,
    ) : RowLookup
}

/** A stale route: the row it was opened from has gone, say a session deleted from History since. */
@Composable
private fun NotFound() =
    Text(
        stringResource(R.string.exerciseProgress_notFound, TREND_WEEKS),
        Modifier.padding(top = Spacing.three),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
    )

/** Where the exercise is now, how far it moved, and since when. */
@Composable
private fun Headline(
    row: ExerciseProgress,
    readings: Readings,
    dates: Dates,
) = FlowRow(
    Modifier.padding(vertical = Spacing.three),
    horizontalArrangement = Arrangement.spacedBy(Spacing.two),
) {
    val colors = KettleTheme.colors
    Text(readings.reading(row.latest), Modifier.alignByBaseline(), style = KettleType.heading, color = colors.text)
    // No red or green, the Stats row's rule for the Stats row's reason: a dip is information.
    Text(
        readings.delta(row.delta),
        Modifier.alignByBaseline(),
        style = KettleType.smallMedium,
        color = if (row.delta == 0.0) colors.textSecondary else colors.accentText,
    )
    Text(
        stringResource(R.string.exerciseProgress_since, dates.monthDay(row.sessions.first().at)),
        Modifier.alignByBaseline(),
        style = KettleType.small,
        color = colors.textSecondary,
    )
}

@Composable
private fun ChartCard(
    row: ExerciseProgress,
    readings: Readings,
    dates: Dates,
    window: ClosedRange<Instant>,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(Spacing.three),
    ) {
        ProgressChart(
            row.sessions,
            window,
            firstLabel = readings.reading(row.sessions.first().value),
            lastLabel = readings.reading(row.sessions.last().value),
            dateLabel = dates::monthDay,
        )
        // The key is for the dots, so it's drawn only when there are dots other than the latest's.
        if (row.sessions.any { it.newBest }) {
            Row(
                Modifier.padding(top = Spacing.two),
                horizontalArrangement = Arrangement.spacedBy(Spacing.one + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(KeyDot)
                        .background(colors.accent, CircleShape),
                )
                Text(
                    stringResource(R.string.exerciseProgress_newBest),
                    style = KettleType.small,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** The chart's table: every session, newest first, since the latest is the one a reader came to check. */
@Composable
private fun SessionList(
    row: ExerciseProgress,
    readings: Readings,
    dates: Dates,
) {
    val colors = KettleTheme.colors
    Text(
        // RN's `label` type uppercases at draw time; the bundle's string is in sentence case.
        stringResource(R.string.exerciseProgress_sessionsTitle, TREND_WEEKS).uppercase(dates.locale),
        Modifier.padding(top = Spacing.four),
        style = KettleType.label,
        color = colors.textSecondary,
    )
    ListHeaderRule()
    val newBestTag = stringResource(R.string.exerciseProgress_newBestTag)
    row.sessions.asReversed().forEachIndexed { index, session ->
        if (index > 0) ListRowSeparator()
        ListRow {
            Text(
                dates.fullDate(session.at),
                Modifier.weight(1f),
                style = KettleType.small,
                color = colors.textSecondary,
            )
            Text(
                buildAnnotatedString {
                    append(readings.reading(session.value))
                    if (session.newBest) {
                        withStyle(SpanStyle(fontWeight = KettleType.small.fontWeight, color = colors.accentText)) {
                            append(" ")
                            append(newBestTag)
                        }
                    }
                },
                style = KettleType.smallMedium,
                color = colors.text,
            )
        }
    }
}

/** Dates in the reader's locale and zone. */
private class Dates(
    val locale: Locale,
    private val zone: TimeZone,
) {
    fun monthDay(at: Instant) = formatMonthDay(at, locale, zone)

    fun fullDate(at: Instant) = formatFullDate(at, locale, zone)
}

private val KeyDot = 8.dp
