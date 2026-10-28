package com.casco.kettle.shell.progress

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.casco.kettle.domain.RecordKind
import com.casco.kettle.domain.TREND_WEEKS
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.exerciseName
import com.casco.kettle.domain.exerciseProgress
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.domain.trendWindowStart
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.formatFullDate
import com.casco.kettle.shell.i18n.formatMonthDay
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.ListRowVerticalPadding
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/** What `shellRoutes` registers at `/exercise-progress`, opened by RN Stats with `exerciseId`. */
@Composable
fun ExerciseProgressRoute(params: Map<String, String>) {
    // Once per opening, as RN's screen read its clock once per mount: the window doesn't creep under
    // a reader who leaves the sheet up.
    val now = remember { Clock.System.now() }
    ExerciseProgressScreen(params["exerciseId"], LocalLatestFiles.current, now, TimeZone.currentSystemDefault())
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
    // Null until the first read lands, so the sheet doesn't say "nothing to chart" before it has looked.
    val row =
        remember(log, exercises, exerciseId, now, zone) {
            log?.let {
                val view = exerciseProgress(it.sessions, exercises, TREND_WEEKS, now, zone)
                RowLookup((view.movers + view.steady).firstOrNull { row -> row.exerciseId == exerciseId })
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
            val progress = row?.progress
            if (row != null && progress == null) NotFound()
            if (progress != null) {
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

/** The lookup's answer once the log is read: [progress] null where the exercise has no row. */
private class RowLookup(
    val progress: ExerciseProgress?,
)

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
        stringResource(R.string.exerciseProgress_since, dates.monthDay(Instant.parse(row.sessions.first().at))),
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
            .clip(CardShape)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, CardShape)
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
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowMinHeight)
                .padding(vertical = ListRowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dates.fullDate(Instant.parse(session.at)),
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

/**
 * A progress value as text: kilograms through the reader's units, seconds and reps rounded. RN's
 * `useProgressReading` and `formatProgressReading`, which RN's Stats row shares with this screen so
 * the two print one number. Stats' port (#201) should move this out and share it the same way.
 */
private class Readings(
    private val resources: Resources,
    private val kind: RecordKind,
    private val units: UnitSystem,
) {
    fun reading(value: Double): String =
        when (kind) {
            RecordKind.HeaviestSet -> {
                val unit =
                    resources.getString(
                        if (units ==
                            UnitSystem.IMPERIAL
                        ) {
                            R.string.units_lb
                        } else {
                            R.string.units_kg
                        },
                    )
                "${jsNumber(toDisplayWeight(value, units))} $unit"
            }

            RecordKind.LongestHold -> {
                resources.getString(R.string.format_seconds, jsRound(value).toString())
            }

            RecordKind.MostReps, RecordKind.MostRounds -> {
                val reps = jsRound(value).toInt()
                resources.pluralText(R.plurals.format_rep, R.string.format_rep_zero, reps)
            }
        }

    /**
     * The change, signed. The magnitude is read and the sign added, so "−1 rep" pluralises on 1; and a
     * flat window has its own words rather than a "+0" that reads as a rounding artefact.
     */
    fun delta(delta: Double): String =
        when (delta.sign) {
            0.0 -> resources.getString(R.string.analytics_progressFlat)
            1.0 -> resources.getString(R.string.analytics_progressUp, reading(abs(delta)))
            else -> resources.getString(R.string.analytics_progressDown, reading(abs(delta)))
        }
}

/** JavaScript's `Math.round`, halves toward +∞, where Kotlin's `round` would round 52.5 s to even. */
private fun jsRound(value: Double): Long = floor(value + HALF).toLong()

/** A number as RN's template literal prints it: `12`, not Kotlin's `12.0`. */
private fun jsNumber(value: Double): String =
    if (value == floor(value) && abs(value) < WHOLE_LIMIT) value.toLong().toString() else value.toString()

private const val HALF = 0.5
private const val WHOLE_LIMIT = 1e15

private val CardShape = RoundedCornerShape(14.dp)
private val KeyDot = 8.dp
