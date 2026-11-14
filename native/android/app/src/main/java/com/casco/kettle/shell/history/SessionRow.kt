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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.HistorySessionView
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.formatMonthBadge
import com.casco.kettle.shell.i18n.formatShortWeekday
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowVerticalPadding
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

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
            Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val locale = LocalConfiguration.current.locales[0]
                Text(
                    row.day
                        ?.day
                        ?.toString()
                        .orEmpty(),
                    style = KettleType.heading,
                    color = colors.text,
                )
                Text(
                    row.day?.let { formatShortWeekday(it, locale) }.orEmpty(),
                    style = KettleType.small,
                    color = colors.textSecondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.two),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.weight(1f, fill = false), style = KettleType.heading, color = colors.text)
                    if (recordIndex != null) PrBadge()
                }
                Text(details(row), style = KettleType.small, color = colors.textSecondary)
                topEntry(row, recordIndex)?.let { Text(it, style = KettleType.small, color = colors.text) }
            }
            Chevron(expanded)
        }
        if (expanded) Detail(row, state.failure, actions)
    }
}

/** Whether a row is open, and why its delete was refused, if it was: the screen's state for it. */
internal data class RowState(
    val expanded: Boolean,
    val failure: String?,
)

/** The record's entry, else the first, as "Squat · 3 × 5 · 60 kg". The name is the user's, verbatim. */
@Composable
private fun topEntry(
    row: HistorySessionView,
    recordIndex: Int?,
): String? {
    val entry = row.entries.firstOrNull { it.index == recordIndex } ?: row.entries.firstOrNull() ?: return null
    return stringResource(
        R.string.history_topEntry,
        entry.exerciseName,
        LocalResources.current.entryResultText(entry.result),
    )
}

/**
 * The PR mark on a row's name line: the page's colour on ink, as the board draws it. Text, so the row's
 * merged name reads it with the rest; a content description here would replace the whole row's words.
 */
@Composable
private fun PrBadge() {
    val colors = KettleTheme.colors
    Text(
        stringResource(R.string.history_prBadge),
        Modifier
            .clip(BadgeShape)
            .background(colors.text)
            .padding(horizontal = Spacing.one + 2.dp, vertical = 1.dp),
        style = KettleType.code.copy(fontWeight = FontWeight.Bold),
        color = colors.background,
    )
}

private val BadgeShape = RoundedCornerShape(6.dp)

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
        pluralText(R.plurals.format_set, R.string.format_set_zero, row.sets),
        stringResource(R.string.history_mixed).takeIf { row.mixed },
    ).joinToString(" · ")

@Composable
private fun Detail(
    row: HistorySessionView,
    failure: String?,
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
                    resources.entryResultText(entry.result),
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
