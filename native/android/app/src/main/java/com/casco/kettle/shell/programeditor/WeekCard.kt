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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.DraftWeek
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.steppingWeek
import com.casco.kettle.domain.withDay
import com.casco.kettle.domain.withNotes
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card
import com.casco.kettle.shell.workouteditor.RemoveButton
import com.casco.kettle.shell.workouteditor.StepButton

/**
 * One week of the program, a card per entry: its number on a stepper, its day and notes, whether it
 * rests, and, for a week that trains, its workout and overrides. A rest week keeps its number, day and
 * notes and nothing else, since those mean the same on either kind.
 */
@Composable
internal fun WeekCard(
    draft: DraftWeek,
    editor: Editor,
    context: WeekContext,
    modifier: Modifier,
) {
    val key = draft.key
    val week = draft.week
    Column(modifier.card(16.dp).padding(Spacing.two + 6.dp)) {
        NumberRow(week.week, onStep = { by -> editor.edit { it.steppingWeek(key, by) } }) { editor.removeWeek(key) }

        FieldLabel(stringResource(R.string.programEditor_dayOptional))
        FormField(
            week.day.orEmpty(),
            { day -> editor.edit { it.withDay(key, day) } },
            name = stringResource(R.string.programEditor_dayOptional),
            placeholder = stringResource(R.string.programEditor_dayPlaceholder),
            FieldOptions(kind = FieldKind.Text, minHeight = 44.dp),
        )

        RestSwitch(week is ProgramWeek.RestDay) { editor.toggleRest(key, context.workouts) }

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

        if (week is ProgramWeek.Training) {
            FieldLabel(stringResource(R.string.programEditor_overridesOptional))
            OverrideEditor(
                week,
                context,
                panel = editor.panels[key],
                onPanel = { if (it == null) editor.panels.remove(key) else editor.panels[key] = it },
            ) { editor.setOverrides(key, it) }
        }
    }
}

/** "Week", its number between − and +, and the ✕ that removes the card, named by its number. */
@Composable
private fun NumberRow(
    number: Long,
    onStep: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = KettleTheme.colors
    val label = stringResource(R.string.programEditor_week)
    Row(
        Modifier.padding(bottom = Spacing.one),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = KettleType.small, color = colors.textSecondary)
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.one),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−", stringResource(R.string.common_decrease, label)) { onStep(-1) }
            Text(
                "$number",
                Modifier.widthIn(min = 24.dp),
                style = KettleType.smallMedium,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            StepButton("+", stringResource(R.string.common_increase, label)) { onStep(1) }
        }
        RemoveButton(stringResource(R.string.programEditor_removeWeek, "$number"), onClick = onRemove)
    }
}

/**
 * A switch rather than a button: it flips the week between two kinds, which is what Switch announces,
 * and its state is what a screen reader reads back.
 */
@Composable
private fun RestSwitch(
    rest: Boolean,
    onToggle: () -> Unit,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .padding(top = Spacing.two - 2.dp)
            .heightIn(min = 44.dp)
            .clip(shape)
            .border(1.dp, if (rest) colors.accent else colors.border, shape)
            .then(if (rest) Modifier.background(colors.accentSoft) else Modifier)
            .toggleable(value = rest, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = Spacing.two),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.programEditor_restDay),
            style = KettleType.smallMedium,
            color = if (rest) colors.accentText else colors.textSecondary,
        )
    }
}

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
        Text(if (open) "⌄" else "›", Modifier.clearAndSetSemantics {}, color = colors.textSecondary)
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
