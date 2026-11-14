package com.casco.kettle.shell.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseProgress
import com.casco.kettle.domain.ProgressView
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.TREND_WEEKS
import com.casco.kettle.domain.UnitSystem
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
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.progress.EXERCISE_ID_PARAM
import com.casco.kettle.shell.progress.Readings
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** What the Progress tab draws in its Overview section (#414), which was Stats. */
@Composable
fun StatsRoute() {
    val time = rememberStatsTime()
    StatsScreen(
        LocalLatestFiles.current,
        time.now,
        time.zone,
        // The locale's calendar, as expo-localization reads RN's from the default locale, which the
        // configuration carries; read through it so a locale change is seen.
        firstWeekday(LocalConfiguration.current.locales[0]),
        open = LocalNavigator.current::push,
    )
}

/**
 * How many weeks the training calendar shows: four, since a calendar is seven cells a week and eight
 * rows of them is too much at a large text size, which is where this screen has to hold up.
 */
private const val CALENDAR_WEEKS = 4

/**
 * Progress's Overview (#414), which was Stats (#201): the two questions an all-time total can't answer,
 * am I turning up and is anything moving, and **no all-time totals**. Nothing here narrows with
 * Sessions' search; it always reports the whole log, which is the only reading that makes "this week"
 * mean anything. The day streak went with #414: the calendar shows the same run of days without a
 * number that resets on one rest day too many.
 *
 * [open] pushes a route, a parameter so a test hears which exercise a row opened.
 */
@Composable
fun StatsScreen(
    files: LatestFiles,
    now: Instant,
    zone: TimeZone,
    firstWeekday: DayOfWeek,
    open: (Route) -> Unit,
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
            // Nothing until the log is read, so it never says "train something twice" before it has looked.
            if (log != null) {
                val sessions = log.logged
                val stats =
                    remember(sessions, now, zone, firstWeekday) { Attendance(sessions, now, zone, firstWeekday) }
                val progress =
                    remember(sessions, exercises, now, zone) {
                        exerciseProgress(sessions, exercises, TREND_WEEKS, now, zone)
                    }
                ThisWeek(stats)
                // Hidden on an empty log rather than drawn as four weeks of rest: a calendar of nothing
                // says "no data" in the most expensive way available. It appears with the first session.
                if (sessions.isNotEmpty()) {
                    SectionHeading(stringResource(R.string.analytics_calendarTitle))
                    TrainingCalendar(stats.calendar, locale)
                }
                // Below the calendar: turning up is the precondition for getting stronger, and a lapse
                // explains a flat row better than a flat row explains itself.
                SectionHeading(
                    stringResource(R.string.analytics_progressTitle),
                    stringResource(R.string.exerciseDetail_window, TREND_WEEKS),
                )
                ProgressSection(progress, exercises, units) { exerciseId ->
                    open(Route(Destination.ExerciseProgress, mapOf(EXERCISE_ID_PARAM to exerciseId)))
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
    val calendar = trainingCalendar(sessions, CALENDAR_WEEKS, now, zone, firstWeekday)
}

/**
 * This week's two numbers side by side, ruled rather than carded (#414): a hairline under the pair and
 * one between them, as the board draws them.
 */
@Composable
private fun ThisWeek(stats: Attendance) {
    val colors = KettleTheme.colors
    Row(
        Modifier
            .padding(top = Spacing.three)
            .height(IntrinsicSize.Min)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    colors.border,
                    Offset(0f, size.height - stroke / 2),
                    Offset(
                        size.width,
                        size.height - stroke / 2,
                    ),
                    stroke,
                )
            },
    ) {
        Tile(stats.week.sessions.toString(), stringResource(R.string.analytics_sessionsThisWeek))
        Box(Modifier.fillMaxHeight().width(1.dp).background(colors.border))
        Tile(
            stringResource(R.string.analytics_hoursMinutes, stats.week.hours, stats.week.minutes),
            stringResource(R.string.analytics_trainedThisWeek),
            Modifier.padding(start = Spacing.three),
        )
    }
}

/** One of the two, its number read together with what it counts. */
@Composable
private fun RowScope.Tile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .weight(1f)
            .padding(vertical = Spacing.three)
            .then(modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(value, style = KettleType.subtitle, color = colors.text)
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
 * One exercise's trend, as the board draws it (#414): its name, then what it is at now and the change in
 * ink with ▲ or ▼, then the sparkline, and the way into that exercise's progress screen. The name is
 * user data and renders verbatim; values print through [Readings], as the screen it opens does, so the
 * two show one number. The arrow is drawn, not said: the line's description reads the signed change
 * ("+2.5 kg") that the arrow and its unsigned number show. The click label says what a tap does, which
 * nothing on screen does.
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
    val bestEver = stringResource(R.string.analytics_bestEver).takeIf { row.bestEver }
    // What a double-tap does, where the row's contents only say what it is.
    val openLabel = stringResource(R.string.analytics_openProgress)
    val reading = readings.reading(row.latest)
    // No red or green: a dip is information, not a failure.
    val change =
        when {
            row.delta > 0 -> "▲ ${readings.reading(row.delta)}"
            row.delta < 0 -> "▼ ${readings.reading(-row.delta)}"
            else -> readings.delta(0.0)
        }
    val spoken = "$reading · ${readings.delta(row.delta)}" + bestEver?.let { " $it" }.orEmpty()
    Box(
        Modifier.clickable(role = Role.Button, onClickLabel = openLabel) {
            openProgress(row.exerciseId)
        },
    ) {
        ListRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = KettleType.default.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                Text(
                    buildAnnotatedString {
                        append(reading)
                        append(" · ")
                        withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) { append(change) }
                        // The record mark inline rather than a section of its own: nearly every record
                        // in the window is also a mover, so a separate list would say most things twice.
                        bestEver?.let { withStyle(SpanStyle(color = colors.accentText)) { append(" $it") } }
                    },
                    Modifier.semantics { contentDescription = spoken },
                    style = KettleType.small.copy(fontFeatureSettings = "tnum"),
                    color = colors.textSecondary,
                )
            }
            Sparkline(row.points)
        }
    }
}

/** A section's heading, and what it covers on the right where that needs saying ("Last 8 weeks"). */
@Composable
private fun SectionHeading(
    text: String,
    aside: String? = null,
) = Row(Modifier.padding(top = Spacing.four, bottom = Spacing.two), verticalAlignment = Alignment.Bottom) {
    Text(text, Modifier.weight(1f).semantics { heading() }, style = KettleType.heading, color = KettleTheme.colors.text)
    aside?.let { Text(it, style = KettleType.small, color = KettleTheme.colors.textSecondary) }
}

@Composable
private fun Note(
    text: String,
    color: Color = KettleTheme.colors.textSecondary,
) = Text(text, Modifier.padding(bottom = Spacing.two), style = KettleType.small, color = color)
