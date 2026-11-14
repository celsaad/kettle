package com.casco.kettle.shell.programeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.DraftWeek
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.withDay
import com.casco.kettle.domain.withNotes
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.form.StepButton
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.settings.Segmented
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The open day (#438): its label with the uniqueness hint Save holds it to, Workout or Rest day, the
 * workout, notes, and its changes for this day only, then its moves and its removal. A rest day keeps
 * its label and notes and nothing else, since those mean the same on either kind.
 */
@Composable
internal fun DayEditor(
    draft: DraftWeek,
    place: DayPlace,
    editor: Editor,
    context: WeekContext,
    outline: EditorOutline,
) {
    val key = draft.key
    val week = draft.week
    val label = dayLabel(week, place.number)
    Column(Modifier.padding(bottom = Spacing.two)) {
        ListRowSeparator()
        OpenDayHeader(label) { outline.toggleDay(key) }

        FieldLabel(stringResource(R.string.programEditor_dayLabel))
        FormField(
            week.day.orEmpty(),
            { day -> editor.edit { it.withDay(key, day) } },
            name = stringResource(R.string.programEditor_dayLabel),
            placeholder = stringResource(R.string.programEditor_dayPlaceholder),
            FieldOptions(kind = FieldKind.Text, minHeight = 44.dp),
        )
        Hint(stringResource(R.string.programEditor_dayLabelHint))

        FieldLabel(stringResource(R.string.programEditor_runs))
        Segmented(KINDS, week is ProgramWeek.RestDay) { rest ->
            if (rest != (week is ProgramWeek.RestDay)) editor.toggleRest(key, context.workouts)
        }

        if (week is ProgramWeek.Training) {
            FieldLabel(stringResource(R.string.programEditor_workout))
            WorkoutPicker(week, open = editor.pickerOpen == key, context, onToggle = { editor.togglePicker(key) }) {
                editor.chooseWorkout(key, it)
            }
        }

        FieldLabel(stringResource(R.string.programEditor_notesOptional))
        FormField(
            week.notes.orEmpty(),
            { notes -> editor.edit { it.withNotes(key, notes) } },
            name = stringResource(R.string.programEditor_notesOptional),
            placeholder = stringResource(R.string.programEditor_notesPlaceholder),
            FieldOptions(kind = FieldKind.Text, minHeight = 44.dp),
        )

        // Rest days take no changes: the schema gives a rest week no overrides.
        if (week is ProgramWeek.Training) {
            FieldLabel(stringResource(R.string.programEditor_changesTitle))
            Hint(stringResource(R.string.programEditor_changesHint))
            OverrideEditor(
                week,
                context,
                panel = editor.panels[key],
                onPanel = { if (it == null) editor.panels.remove(key) else editor.panels[key] = it },
            ) { editor.setOverrides(key, it) }
        }

        DayMoves(label, place, onMove = { by -> outline.moveDay(key, by) }) {
            // A week's only day goes with its week, closing the gap as the week menu's delete does.
            if (place.first && place.last) outline.deleteWeek(week.week) else editor.removeWeek(key)
        }
    }
}

/** The open day's label over its fields, and the chevron that folds it shut again. */
@Composable
private fun OpenDayHeader(
    label: String,
    onCollapse: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onCollapse)
            .semantics { collapse { true.also { onCollapse() } } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = KettleType.heading, color = KettleTheme.colors.text)
        Chevron(expanded = true)
    }
}

/** Up and down within the week, icon-only so named by the day, and Remove day in words. */
@Composable
private fun DayMoves(
    label: String,
    place: DayPlace,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.padding(top = Spacing.two),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            R.drawable.icon_up,
            stringResource(R.string.programEditor_moveDayUp, label),
            !place.first,
        ) { onMove(-1) }
        StepButton(
            R.drawable.icon_down,
            stringResource(R.string.programEditor_moveDayDown, label),
            !place.last,
        ) { onMove(+1) }
        Box(Modifier.weight(1f))
        TextAction(
            stringResource(R.string.programEditor_removeDay),
            KettleType.smallMedium,
            KettleTheme.colors.textSecondary,
            onRemove,
        )
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, Modifier.padding(top = Spacing.half), style = KettleType.small, color = KettleTheme.colors.textSecondary)

/** Where a day sits in its week: its number, and whether it can move up or down. */
internal data class DayPlace(
    val number: Int,
    val first: Boolean,
    val last: Boolean,
)

/** What a day runs: a workout, or rest. */
private val KINDS = listOf(false to R.string.programEditor_workout, true to R.string.programEditor_restDay)

/** The week's workout, and the library's workouts under it while it's open. Names are the user's. */
@Composable
private fun WorkoutPicker(
    week: ProgramWeek.Training,
    open: Boolean,
    context: WeekContext,
    onToggle: () -> Unit,
    onChoose: (String) -> Unit,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                if (open) collapse { true.also { onToggle() } } else expand { true.also { onToggle() } }
            }.padding(horizontal = Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            context.workout(week.workoutId)?.name ?: stringResource(R.string.programEditor_selectWorkout),
            Modifier.weight(1f),
            style = KettleType.smallMedium,
            color = colors.text,
        )
        Chevron(expanded = open)
    }
    if (!open) return
    Column(Modifier.padding(top = Spacing.one), verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
        for (workout in context.workouts) {
            val chosen = workout.id == week.workoutId
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(colors.backgroundElement)
                    .border(1.dp, if (chosen) colors.accent else colors.border, shape)
                    .selectable(selected = chosen, role = Role.Button) { onChoose(workout.id) }
                    .padding(Spacing.two - 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text(workout.name, style = KettleType.smallMedium, color = colors.text) }
        }
    }
}

@Composable
internal fun FieldLabel(text: String) =
    Text(
        text,
        Modifier.padding(top = Spacing.two - 2.dp, bottom = 3.dp),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
    )
