package com.casco.kettle.shell.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseProgress
import com.casco.kettle.domain.ProgressView
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.TREND_WEEKS
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.currentStreak
import com.casco.kettle.domain.exerciseName
import com.casco.kettle.domain.exerciseProgress
import com.casco.kettle.domain.thisWeekStats
import com.casco.kettle.domain.trainingCalendar
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.firstWeekday
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.ListRowVerticalPadding
import com.casco.kettle.shell.progress.EXERCISE_ID_PARAM
import com.casco.kettle.shell.progress.EXERCISE_PROGRESS_PATH
import com.casco.kettle.shell.progress.Readings
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.util.Locale

/** What `shellRoutes` registers at `/analytics`, opened from History's header. */
@Composable
fun StatsRoute() {
    // Once per opening, as exercise progress reads it: this week, the streak and today's cell are all
    // as of when the sheet came up, and a sheet is short-lived.
    val now = remember { Clock.System.now() }
    StatsScreen(
        LocalLatestFiles.current,
        now,
        TimeZone.currentSystemDefault(),
        // The locale's calendar, as expo-localization reads RN's from the default locale, which the
        // configuration carries; read through it so a locale change is seen.
        firstWeekday(LocalConfiguration.current.locales[0]),
        open = JsBridge::open,
    )
}

/**
 * How many weeks the training calendar shows: four, since a calendar is seven cells a week and eight
 * rows of them is too much at a large text size, which is where this screen has to hold up.
 */
private const val CALENDAR_WEEKS = 4

/**
 * Stats (#201): the numbers behind History, as RN's `analytics.tsx`, now the empty stub at this path.
 * It answers the two questions an all-time total can't (am I turning up, and is anything moving), and
 * **repeats no all-time totals**: History's header says them, one screen back, in the same words.
 * Nothing here narrows with History's search; it always reports the whole log, which is the only
 * reading that makes "this week" and a streak mean anything.
 *
 * [open] is `JsBridge.open`, a parameter so a test hears which exercise a row opened.
 */
@Composable
fun StatsScreen(
    files: LatestFiles,
    now: Instant,
    zone: TimeZone,
    firstWeekday: DayOfWeek,
    open: (Stacking, String, Map<String, String>) -> Unit,
) {
    val log = files.sessionLog
    val exercises = files.library?.exercises.orEmpty()
    val locale = LocalConfiguration.current.locales[0]
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(locale)

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
            Text(
                stringResource(R.string.analytics_title),
                Modifier.semantics { heading() },
                style = KettleType.title,
                color = KettleTheme.colors.text,
            )
            // Nothing below the title until the log is read, so the sheet never says "train something
            // twice" before it has looked.
            if (log != null) {
                val sessions = log.sessions
                val stats =
                    remember(sessions, now, zone, firstWeekday) { Attendance(sessions, now, zone, firstWeekday) }
                val progress =
                    remember(sessions, exercises, now, zone) {
                        exerciseProgress(sessions, exercises, TREND_WEEKS, now, zone)
                    }
                ThisWeek(stats, locale)
                // Hidden on an empty log rather than drawn as four weeks of rest: a calendar of nothing
                // says "no data" in the most expensive way available. It appears with the first session.
                if (sessions.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.analytics_calendarTitle, CALENDAR_WEEKS), locale)
                    Card { TrainingCalendar(stats.calendar, locale) }
                }
                // Below the calendar: turning up is the precondition for getting stronger, and a lapse
                // explains a flat row better than a flat row explains itself.
                SectionLabel(stringResource(R.string.analytics_progressTitle), locale)
                ProgressSection(progress, exercises, units) { exerciseId ->
                    open(Stacking.Push, EXERCISE_PROGRESS_PATH, mapOf(EXERCISE_ID_PARAM to exerciseId))
                }
            }
        }
    }
}

/** Everything the log says about turning up, worked out once per log rather than per frame. */
private class Attendance(
    sessions: List<Session>,
    now: Instant,
    zone: TimeZone,
    firstWeekday: DayOfWeek,
) {
    val week = thisWeekStats(sessions, now, zone, firstWeekday)
    val streak = currentStreak(sessions, now, zone)
    val calendar = trainingCalendar(sessions, CALENDAR_WEEKS, now, zone, firstWeekday)
}

@Composable
private fun ThisWeek(
    stats: Attendance,
    locale: Locale,
) {
    SectionLabel(stringResource(R.string.history_thisWeekLabel), locale)
    // Intrinsic height so the three tiles are as tall as the tallest, as RN's stretched row.
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        Tile(stats.week.sessions.toString(), stringResource(R.string.history_sessions))
        Tile(
            stringResource(R.string.analytics_hoursMinutes, stats.week.hours, stats.week.minutes),
            stringResource(R.string.history_time),
        )
        // "day streak", not "streak": the one number here not scoped to the week, so a bare "streak"
        // under THIS WEEK would read a 30-day run as a weekly count.
        Tile(stats.streak.toString(), stringResource(R.string.history_streak))
    }
}

/** One stat card, its number read together with what it counts. */
@Composable
private fun RowScope.Tile(
    value: String,
    label: String,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .card()
            .padding(Spacing.two + 4.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(value, style = KettleType.heading, color = colors.text)
        Text(label, style = KettleType.small, color = colors.textSecondary)
    }
}

/**
 * What moved, and, one tap away, what didn't. **The rows that held steady fold into one**: listed with
 * the rest, a dozen "no change" rows sat above the one exercise that moved. A flat row is a real
 * answer, so it stays reachable; it just doesn't get to bury the others.
 */
@Composable
private fun ProgressSection(
    progress: ProgressView,
    exercises: List<Exercise>,
    units: UnitSystem,
    openProgress: (exerciseId: String) -> Unit,
) {
    val colors = KettleTheme.colors
    val (movers, steady, fixedHoldsLeftOut) = progress
    if (movers.isEmpty() && steady.isEmpty()) {
        // Two different empties. With fixed holds left out, "train something twice" would tell someone
        // who trained a dozen things twice to go and do it, so they are told why instead.
        val empty =
            if (fixedHoldsLeftOut >
                0
            ) {
                R.string.analytics_progressOnlyFixedHolds
            } else {
                R.string.analytics_progressEmpty
            }
        Note(stringResource(empty))
        return
    }

    Note(
        stringResource(
            if (fixedHoldsLeftOut > 0) R.string.analytics_progressBodyFixedHolds else R.string.analytics_progressBody,
            TREND_WEEKS,
        ),
    )
    if (movers.isEmpty()) Note(stringResource(R.string.analytics_nothingMoved, TREND_WEEKS), colors.text)

    val row: @Composable (ExerciseProgress) -> Unit = {
        ProgressRow(it, exerciseName(exercises, it.exerciseId), units, openProgress)
    }
    movers.forEachIndexed { index, mover ->
        if (index > 0) ListRowSeparator()
        row(mover)
    }
    if (steady.isEmpty()) return

    var showSteady by rememberSaveable { mutableStateOf(false) }
    if (movers.isNotEmpty()) ListRowSeparator()
    SteadyToggle(steady.size, showSteady) { showSteady = it }
    if (showSteady) {
        steady.forEach {
            ListRowSeparator()
            row(it)
        }
    }
}

/** The one row the held-steady exercises fold into, [count] of them, opened when [shown]. */
@Composable
private fun SteadyToggle(
    count: Int,
    shown: Boolean,
    onShow: (Boolean) -> Unit,
) {
    val colors = KettleTheme.colors
    // Text, not a chevron: its name is exactly the words on screen, which Voice Access matches, and
    // expand/collapse is what tells a screen reader it reveals rather than navigates.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowMinHeight)
            .clickable(role = Role.Button) { onShow(!shown) }
            .semantics {
                if (shown) {
                    collapse { true.also { onShow(false) } }
                } else {
                    expand { true.also { onShow(true) } }
                }
            }.padding(vertical = ListRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralText(R.plurals.analytics_heldSteady, R.string.analytics_heldSteady_zero, count),
            Modifier.weight(1f),
            style = KettleType.smallMedium,
            color = colors.text,
        )
        Text(
            stringResource(if (shown) R.string.analytics_hideSteady else R.string.analytics_showSteady),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}

/**
 * One exercise's trend: what it is at now, the shape of how it got there, and the change, and the way
 * into that exercise's own progress screen. The name is user data and renders verbatim; values print
 * through [Readings], as the screen it opens does, so the two show one number. No content description:
 * the name, the reading and the change inside it are what it is called; the click label says what a
 * tap does, which nothing on screen does.
 */
@Composable
private fun ProgressRow(
    row: ExerciseProgress,
    name: String,
    units: UnitSystem,
    openProgress: (exerciseId: String) -> Unit,
) {
    val colors = KettleTheme.colors
    val readings = Readings(LocalResources.current, row.kind, units)
    val bestEver = stringResource(R.string.analytics_bestEver)
    // What a double-tap does, where the row's contents only say what it is.
    val openLabel = stringResource(R.string.analytics_openProgress)
    Box(
        Modifier.clickable(role = Role.Button, onClickLabel = openLabel) {
            openProgress(row.exerciseId)
        },
    ) {
        ListRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = KettleType.smallMedium, color = colors.text)
                // The record mark inline rather than a section of its own: nearly every record in the
                // window is also a mover, so a separate list would say most things twice.
                Text(
                    buildAnnotatedString {
                        append(readings.reading(row.latest))
                        if (row.bestEver) {
                            withStyle(SpanStyle(color = colors.accentText)) {
                                append(" ")
                                append(bestEver)
                            }
                        }
                    },
                    style = KettleType.small,
                    color = colors.textSecondary,
                )
            }
            Sparkline(row.points)
            // The one number here that can be negative, and it gets no red/green pair: a dip is
            // information, not a failure. The accent when something moved, so the eye finds those rows.
            Text(
                readings.delta(row.delta),
                // A fixed column, so the deltas line up down the screen.
                Modifier.widthIn(min = 72.dp),
                style = KettleType.smallMedium,
                color = if (row.delta == 0.0) colors.textSecondary else colors.accentText,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** RN's `label` type uppercases at draw time; the bundle's string is in sentence case. */
@Composable
private fun SectionLabel(
    text: String,
    locale: Locale,
) = Text(
    text.uppercase(locale),
    Modifier.padding(top = Spacing.four, bottom = Spacing.two),
    style = KettleType.label,
    color = KettleTheme.colors.textSecondary,
)

@Composable
private fun Note(
    text: String,
    color: Color = KettleTheme.colors.textSecondary,
) = Text(text, Modifier.padding(bottom = Spacing.two), style = KettleType.small, color = color)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(Spacing.three),
    ) { content() }
}
