package com.casco.kettle.shell.programeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.DraftWeek
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.freeDayLabel
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.ListRowVerticalPadding
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * One week's row (#438): a chevron, "Week 3", "7 days · 3 changed", opening and closing the week, and a
 * ⋯ menu that moves, duplicates or deletes it. The row is named by its own words; expand and collapse
 * carry its state, as History's rows do.
 */
@Composable
internal fun WeekRow(
    week: Long,
    days: List<DraftWeek>,
    open: Boolean,
    position: WeekPosition,
    outline: EditorOutline,
) {
    val colors = KettleTheme.colors
    val toggle = { outline.toggleWeek(week) }
    ListRowSeparator()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = ListRowMinHeight)
                .clickable(role = Role.Button, onClick = toggle)
                .semantics { if (open) collapse { true.also { toggle() } } else expand { true.also { toggle() } } },
            horizontalArrangement = Arrangement.spacedBy(Spacing.two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chevron(expanded = open)
            Text(stringResource(R.string.programs_week, week), style = KettleType.heading, color = colors.text)
            Text(summary(days), style = KettleType.small, color = colors.textSecondary)
        }
        WeekMenu(week, position, outline)
    }
}

/** Where a week sits, which says whether it can move up or down. */
internal data class WeekPosition(
    val first: Boolean,
    val last: Boolean,
)

/** "7 days · 3 changed": its days, and how many carry a change for that day. */
@Composable
private fun summary(days: List<DraftWeek>): String {
    val changed = days.count { (it.week as? ProgramWeek.Training)?.overrides?.isNotEmpty() == true }
    val count = pluralText(R.plurals.programEditor_dayCount, R.string.programEditor_dayCount_zero, days.size)
    return if (changed == 0) {
        count
    } else {
        "$count · " +
            pluralText(R.plurals.programEditor_changedCount, R.string.programEditor_changedCount_zero, changed)
    }
}

/** The ⋯: icon-only, so it carries a name saying whose actions they are. A move that can't happen isn't offered. */
@Composable
private fun WeekMenu(
    week: Long,
    position: WeekPosition,
    outline: EditorOutline,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.programEditor_weekActions, week)
    Box {
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            TypeIcon(R.drawable.icon_more, KettleType.heading, KettleTheme.colors.textSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val item = @Composable { text: Int, enabled: Boolean, action: () -> Unit ->
                DropdownMenuItem(
                    text = { Text(stringResource(text), style = KettleType.default) },
                    onClick = {
                        open = false
                        action()
                    },
                    enabled = enabled,
                )
            }
            item(R.string.programEditor_moveUp, !position.first) { outline.moveWeek(week, -1) }
            item(R.string.programEditor_moveDown, !position.last) { outline.moveWeek(week, +1) }
            item(R.string.programEditor_duplicate, true) { outline.duplicateWeek(week) }
            item(R.string.programEditor_deleteWeek, true) { outline.deleteWeek(week) }
        }
    }
}

/**
 * A day folded shut: its label, its workout or "Rest day", and "Note · 1 change". The label is the
 * user's; with none it reads "Day N".
 */
@Composable
internal fun DayRow(
    day: DraftWeek,
    number: Int,
    context: WeekContext,
    onOpen: () -> Unit,
) {
    val colors = KettleTheme.colors
    val week = day.week
    val training = week as? ProgramWeek.Training
    val note = stringResource(R.string.programEditor_noteTag).takeIf { !week.notes.isNullOrBlank() }
    val changes =
        training?.overrides?.size?.takeIf { it > 0 }?.let {
            pluralText(R.plurals.programEditor_changeCount, R.string.programEditor_changeCount_zero, it)
        }
    ListRowSeparator()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowMinHeight)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { expand { true.also { onOpen() } } }
            .padding(vertical = ListRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.half)) {
            Text(dayLabel(week, number), style = KettleType.smallMedium, color = colors.textSecondary)
            Text(
                training?.let { context.workout(it.workoutId)?.name ?: it.workoutId.ifEmpty { null } }
                    ?: stringResource(
                        if (training ==
                            null
                        ) {
                            R.string.programEditor_restDay
                        } else {
                            R.string.programEditor_selectWorkout
                        },
                    ),
                style = KettleType.heading,
                color = if (training != null) colors.text else colors.textSecondary,
            )
            listOfNotNull(note, changes).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = KettleType.small, color = colors.textSecondary)
            }
        }
        Chevron(expanded = false)
    }
}

/** The day's own label, or "Day N" where it has none. */
@Composable
internal fun dayLabel(
    week: ProgramWeek,
    number: Int,
): String = week.day?.takeIf { it.isNotBlank() } ?: stringResource(R.string.programEditor_dayN, number)

/**
 * Under an open week's days: a day more, labelled with the first "Day N" the week doesn't have, or the
 * whole week again right after it. No day to add with no workout to give it: Save would refuse it.
 */
@Composable
internal fun WeekFooter(
    week: Long,
    editor: Editor,
    context: WeekContext,
    outline: EditorOutline,
) {
    val resources = LocalResources.current
    val newLabel = editor.draft.freeDayLabel(week) { resources.getString(R.string.programEditor_dayN, "$it") }
    ListRowSeparator()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.four)) {
        if (context.workouts.isNotEmpty()) {
            TextAction(
                stringResource(R.string.programEditor_addDay),
                KettleType.smallMedium,
                KettleTheme.colors.text,
                onClick = { outline.addDay(week, newLabel, context.workouts) },
            )
        }
        TextAction(
            stringResource(R.string.programEditor_duplicateThisWeek, week),
            KettleType.smallMedium,
            KettleTheme.colors.text,
            onClick = { outline.duplicateWeek(week) },
        )
    }
}
