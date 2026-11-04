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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ConfigField
import com.casco.kettle.domain.ConfigKey
import com.casco.kettle.domain.OverrideConfirm
import com.casco.kettle.domain.OverrideEditTarget
import com.casco.kettle.domain.OverrideForm
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.confirmOverride
import com.casco.kettle.domain.overrideNote
import com.casco.kettle.domain.overrideTargets
import com.casco.kettle.domain.steppingRounds
import com.casco.kettle.shell.R
import com.casco.kettle.shell.exerciseeditor.configProblemText
import com.casco.kettle.shell.exerciseeditor.fieldName
import com.casco.kettle.shell.exerciseeditor.optionalSuffix
import com.casco.kettle.shell.form.EditorButtons
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.form.dashedBorder
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.programdetail.overrideLines
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.workouteditor.RemoveButton
import com.casco.kettle.shell.workouteditor.StepButton

/**
 * A training week's overrides (#243), as RN's `ProgramOverrideEditor`: a row per override, and an
 * add or an edit in a panel under them. The patches stay raw snake_case throughout (`OverrideEditing.kt`
 * converts); [onChange] gets [week]'s whole new list.
 *
 * The rows are inert while the panel is open. Both their controls change the list the open edit
 * points into, and RN's stale index there crashed, or edited the wrong override.
 */
@Composable
internal fun OverrideEditor(
    week: ProgramWeek.Training,
    context: WeekContext,
    panel: OverridePanel?,
    onPanel: (OverridePanel?) -> Unit,
    onChange: (List<ProgramOverride>) -> Unit,
) {
    val overrides = week.overrides.orEmpty()
    val workout = context.workout(week.workoutId)
    val locked = panel != null
    Column(Modifier.padding(top = Spacing.one), verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
        overrides.forEachIndexed { index, override ->
            val form = OverrideForm.editing(index, override, context.library, workout, context.units)
            OverrideRow(
                overrideLines(overrideNote(override, context.library, workout)),
                // A row whose target has gone has nothing to seed a form from, so it doesn't open.
                opens = form != null && !locked,
                removes = !locked,
                onOpen = { form?.let { onPanel(OverridePanel.Editing(it)) } },
                onRemove = { onChange(overrides.filterIndexed { i, _ -> i != index }) },
            )
        }
        when (panel) {
            null -> AddOverride(workout, context) { onPanel(OverridePanel.Choosing) }
            OverridePanel.Choosing -> TargetPicker(overrideTargets(context.library, workout), context, onPanel)
            is OverridePanel.Editing -> FieldsPanel(panel, overrides, context, onPanel, onChange)
        }
    }
}

@Composable
private fun OverrideRow(
    lines: List<String>,
    opens: Boolean,
    removes: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.border, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                // Disabled rather than silently inert: TalkBack then says it's unavailable.
                .clickable(enabled = opens, role = Role.Button, onClick = onOpen)
                .padding(Spacing.two - 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (line in lines) Text(line, style = KettleType.small, color = colors.textSecondary)
        }
        RemoveButton(stringResource(R.string.overrideEditor_removeAccessibility), enabled = removes, onClick = onRemove)
    }
}

/**
 * "+ Add override", disabled while the week's workout gives it nothing to patch. With no workout
 * chosen there's nothing to explain yet; with one that has nothing in it, the hint says so.
 */
@Composable
private fun AddOverride(
    workout: Workout?,
    context: WeekContext,
    onAdd: () -> Unit,
) {
    val colors = KettleTheme.colors
    val nothing = overrideTargets(context.library, workout).isEmpty()
    val enabled = workout != null && !nothing
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .dashedBorder(1.dp, colors.border, 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onAdd)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.overrideEditor_addOverride),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
    if (workout != null && nothing) {
        Text(
            stringResource(R.string.overrideEditor_nothingToOverride),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}

/** What the week can patch, each with its kind beside it; a circuit by its id, which is the user's. */
@Composable
private fun TargetPicker(
    targets: List<OverrideEditTarget>,
    context: WeekContext,
    onPanel: (OverridePanel?) -> Unit,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(10.dp)
    for (target in targets) {
        val (name, kind) =
            when (target) {
                is OverrideEditTarget.Exercise -> target.exercise.name to R.string.overrideEditor_exercise
                is OverrideEditTarget.Circuit -> target.id to R.string.overrideEditor_circuit
            }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(colors.backgroundElement)
                .border(1.dp, colors.border, shape)
                .clickable(role = Role.Button) {
                    onPanel(OverridePanel.Editing(OverrideForm.adding(target, context.units)))
                }.padding(Spacing.two - 2.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, Modifier.weight(1f), style = KettleType.smallMedium, color = colors.text)
            Text(stringResource(kind), style = KettleType.small, color = colors.textSecondary)
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextAction(
            stringResource(R.string.common_cancel),
            KettleType.small,
            colors.textSecondary,
            onClick = { onPanel(null) },
        )
    }
}

/** One patch's fields, its refusal, and Cancel beside the confirm. */
@Composable
private fun FieldsPanel(
    panel: OverridePanel.Editing,
    overrides: List<ProgramOverride>,
    context: WeekContext,
    onPanel: (OverridePanel?) -> Unit,
    onChange: (List<ProgramOverride>) -> Unit,
) {
    val colors = KettleTheme.colors
    val form = panel.form
    // Any keystroke clears a refusal: it named what was typed before.
    val type = { key: ConfigKey, text: String ->
        onPanel(OverridePanel.Editing(form.copy(values = form.values + (key to text))))
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .padding(Spacing.two - 2.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.one),
    ) {
        val title =
            when (val target = form.target) {
                is OverrideEditTarget.Exercise -> target.exercise.name
                is OverrideEditTarget.Circuit -> stringResource(R.string.overrideEditor_circuitTitle, target.id)
            }
        Text(title, style = KettleType.smallMedium, color = colors.text)
        for (field in form.fields) {
            if (field.key == ConfigKey.Rounds && form.target is OverrideEditTarget.Circuit) {
                RoundsStepper(form.values[ConfigKey.Rounds].orEmpty()) {
                    onPanel(OverridePanel.Editing(form.steppingRounds(it)))
                }
            } else {
                ConfigInput(field, form.values[field.key].orEmpty(), context) { type(field.key, it) }
            }
        }
        panel.problem?.let { Text(configProblemText(it), style = KettleType.small, color = colors.accentText) }
        EditorButtons(
            stringResource(
                if (form.opened ==
                    null
                ) {
                    R.string.overrideEditor_confirmAdd
                } else {
                    R.string.overrideEditor_saveOverride
                },
            ),
            onCancel = { onPanel(null) },
            onPrimary = {
                when (val confirmed = confirmOverride(form, overrides, context.units)) {
                    is OverrideConfirm.Refused -> {
                        onPanel(panel.copy(problem = confirmed.problem))
                    }

                    is OverrideConfirm.Done -> {
                        onChange(confirmed.overrides)
                        onPanel(null)
                    }
                }
            },
            compact = true,
        )
    }
}

/** A circuit's rounds on a stepper, which `steppingRounds` holds to the schema's bounds. */
@Composable
private fun RoundsStepper(
    text: String,
    onStep: (Int) -> Unit,
) {
    val colors = KettleTheme.colors
    // One name for the field, its buttons and its refusal, which names it as `FieldName.Rounds`.
    val label = stringResource(R.string.exerciseForm_field_rounds)
    Text(label, style = KettleType.small, color = colors.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.one), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", stringResource(R.string.common_decrease, label)) { onStep(-1) }
        Text(
            text.ifEmpty { "1" },
            Modifier.widthIn(min = 24.dp),
            style = KettleType.smallMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        StepButton("+", stringResource(R.string.common_increase, label)) { onStep(1) }
    }
}

/** One field, labelled as the exercise form labels it: its unit, and whether it's optional. */
@Composable
private fun ConfigInput(
    field: ConfigField,
    value: String,
    context: WeekContext,
    onChange: (String) -> Unit,
) {
    val named = fieldName(field, context.units)
    Text(named + optionalSuffix(field), style = KettleType.small, color = KettleTheme.colors.textSecondary)
    FormField(value, onChange, name = named, placeholder = "", FieldOptions(minHeight = 44.dp))
}
