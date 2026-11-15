package com.casco.kettle.shell.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.HistorySessionView
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.formatMonthBadge
import com.casco.kettle.shell.i18n.formatShortWeekday
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowVerticalPadding
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.InkBadge
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.rememberWidestText
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * One session: a row, not a card, that expands in place. It can't be a `ListRow`, since the detail
 * unfolds under the pressable part, so it shares `ListRow`'s metrics instead. No rule above the detail:
 * between rows divided by hairlines, one inside an open row would read as two sessions.
 *
 * The pressable part takes its name from its own text; expand and collapse are what TalkBack can't
 * infer from the chevron, which is decoration.
 *
 * As the board draws it (#414): the day and its weekday, the name with a PR badge when [recordIndex] gives
 * the place of the entry that set one, the minutes and sets, and one entry's result: that record's, else the
 * first logged, so the line shows what earned the badge.
 */
@Composable
internal fun SessionRow(
    row: HistorySessionView,
    name: String,
    state: RowState,
    recordIndex: Int?,
    actions: RowActions,
) {
    val colors = KettleTheme.colors
    val expanded = state.expanded
    Column(Modifier.padding(vertical = ListRowVerticalPadding)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowMinHeight)
                // What a double-tap does, which the row's own words (what it is) don't say; expand and
                // collapse carry the state.
                .clickable(
                    role = Role.Button,
                    onClickLabel =
                        stringResource(
                            if (expanded) R.string.history_hideExercises else R.string.history_showExercises,
                        ),
                    onClick = actions.toggle,
                ).semantics {
                    if (expanded) {
                        collapse {
                            true.also { actions.toggle() }
                        }
                    } else {
                        expand { true.also { actions.toggle() } }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(Spacing.two + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DayColumn(row, state.dayWidth)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.two),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.weight(1f, fill = false), style = KettleType.rowTitle, color = colors.text)
                    if (recordIndex != null) PrBadge()
                }
                Text(details(row), style = KettleType.small, color = colors.textSecondary)
                topEntry(row, recordIndex, state.units)?.let { Text(it, style = KettleType.small, color = colors.text) }
            }
            // Down while shut and up while open, as #414 drew it: the row opens in place rather than going
            // anywhere, which a › would promise. So the ⌄ is drawn either way and turned over while open.
            Chevron(Modifier.rotate(if (expanded) HALF_TURN else 0f), expanded = true)
        }
        if (expanded) Detail(row, state.failure, state.units, actions)
    }
}

/**
 * Whether a row is open, why its delete was refused, if it was, the units its loads read in, and the
 * day column's width, one for every row: the screen's state for it.
 */
internal data class RowState(
    val expanded: Boolean,
    val failure: String?,
    val units: UnitSystem,
    val dayWidth: Dp,
)

/**
 * As wide as the widest short weekday or day of the month at the reader's text size, and never under the
 * board's 38dp: a fixed 38dp broke "qua." mid-word at 200% (#480), and one width for every row keeps the
 * names lined up down the list, where each row sizing itself would stagger them. The screen works it out
 * once and hands it to every row: per row, it built seven ICU formatters for each row scrolled in.
 */
@Composable
internal fun dayColumnWidth(): Dp {
    val locale = LocalConfiguration.current.locales[0]
    // Any seven consecutive days are the seven weekdays.
    val weekdays =
        remember(locale) {
            val anyDay = LocalDate.fromEpochDays(0)
            (0 until DAYS_IN_WEEK).map { formatShortWeekday(anyDay.plus(it, DateTimeUnit.DAY), locale) }
        }
    // Every day of the month, since a proportional face's "28" can be wider than its "30".
    val widestDay = rememberWidestText(DAYS_OF_MONTH, KettleType.heading)
    return maxOf(DAY_COLUMN_MIN, rememberWidestText(weekdays, KettleType.small), widestDay)
}

/** The day of the month over its short weekday, the column a row's words line up beside. */
@Composable
private fun DayColumn(
    row: HistorySessionView,
    width: Dp,
) = Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        row.day
            ?.day
            ?.toString()
            .orEmpty(),
        style = KettleType.heading,
        color = KettleTheme.colors.text,
    )
    Text(
        row.day?.let { formatShortWeekday(it, locale) }.orEmpty(),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
    )
}

/** The record's entry, else the first, as "Squat · 3 × 5 · 60 kg". The name is the user's, verbatim. */
@Composable
private fun topEntry(
    row: HistorySessionView,
    recordIndex: Int?,
    units: UnitSystem,
): String? {
    val entry = row.entries.firstOrNull { it.index == recordIndex } ?: row.entries.firstOrNull() ?: return null
    return stringResource(
        R.string.history_topEntry,
        entry.exerciseName,
        LocalResources.current.entryResultText(entry.result, units),
    )
}

/** The PR mark on a row's name line: the page's colour on ink, as the board draws it ([InkBadge]). */
@Composable
private fun PrBadge() {
    val colors = KettleTheme.colors
    InkBadge(stringResource(R.string.history_prBadge), colors.text, colors.background)
}

/**
 * The day of the month and the short month the delete's confirm names a session by, "29 JUL". Blank
 * where `started_at` isn't an instant.
 */
@Composable
internal fun dateBadge(row: HistorySessionView): Pair<String, String> {
    val locale = LocalConfiguration.current.locales[0]
    return row.day
        ?.day
        ?.toString()
        .orEmpty() to row.day?.let { formatMonthBadge(it, locale) }.orEmpty()
}

@Composable
private fun details(row: HistorySessionView): String =
    listOfNotNull(
        stringResource(R.string.format_minutes, row.minutes),
        if (row.inRounds) {
            pluralText(R.plurals.format_round, R.string.format_round_zero, row.sets)
        } else {
            pluralText(R.plurals.format_set, R.string.format_set_zero, row.sets)
        },
    ).joinToString(" · ")

@Composable
private fun Detail(
    row: HistorySessionView,
    failure: String?,
    units: UnitSystem,
    actions: RowActions,
) {
    val colors = KettleTheme.colors
    val resources = LocalResources.current
    Column(Modifier.padding(top = Spacing.two + 4.dp), verticalArrangement = Arrangement.spacedBy(Spacing.one + 4.dp)) {
        for (entry in row.entries) {
            // Both sides are 13sp text, so their first lines line up even when the result wraps.
            // Each side up to half the row and no wider than its text, pushed to the two edges: the
            // result sits flush right as a column, and a long one wraps rather than running off.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // The name is an identifier, so it ellipsizes rather than squeezing out the result,
                // which is the logged data. The name is the user's and renders verbatim.
                Text(
                    entry.exerciseName,
                    Modifier.weight(1f, fill = false).padding(end = Spacing.two),
                    style = KettleType.small,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    resources.entryResultText(entry.result, units),
                    Modifier.weight(1f, fill = false),
                    style = KettleType.smallMedium,
                    color = colors.text,
                    textAlign = TextAlign.End,
                )
            }
        }
        failure?.let {
            Text(stringResource(R.string.common_deleteFailed, it), style = KettleType.small, color = colors.accentText)
        }
        // Anchored right, the destructive one first and muted: Export keeps the outermost spot, and
        // Delete never reads as the intended action.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.weight(1f))
            // Hidden on the row the runner's service is writing (#295): the next set logged would write it back.
            actions.delete?.let {
                TextAction(stringResource(R.string.common_delete), KettleType.small, colors.textSecondary, it)
            }
            // Hidden rather than disabled while a session runs: the runner owns that file and writes
            // through its own copy, so an edit made here would be lost at the next set logged.
            if (row.editable) {
                TextAction(stringResource(R.string.history_edit), KettleType.small, colors.textSecondary, actions.edit)
            }
            TextAction(stringResource(R.string.common_export), KettleType.small, colors.accentText, actions.export)
        }
    }
}

private const val HALF_TURN = 180f

private val DAY_COLUMN_MIN = 38.dp

private const val DAYS_IN_WEEK = 7

private const val LONGEST_MONTH = 31

private val DAYS_OF_MONTH = (1..LONGEST_MONTH).map { "$it" }
