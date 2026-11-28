package com.casco.kettle.shell.exerciseeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ConfigField
import com.casco.kettle.domain.ConfigKey
import com.casco.kettle.domain.ExerciseDraft
import com.casco.kettle.domain.FieldUnit
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.canStep
import com.casco.kettle.domain.configFields
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.shownCount
import com.casco.kettle.domain.stepping
import com.casco.kettle.domain.storedWeightKg
import com.casco.kettle.domain.typing
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.DurationButton
import com.casco.kettle.shell.form.StepButton
import com.casco.kettle.shell.library.rangeLabel
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

// The exercise editor's controls (#441): a count on a stepper, a required time on #440's picker, and
// everything that can be blank, where blank means something, as the typed field it was.

/** [field]'s control: which one is the field's kind, as the plan on #441 sets out. */
@Composable
internal fun FieldControl(
    field: ConfigField,
    context: FieldContext,
) {
    when {
        // Drawn beside the min, as "8 to 12".
        field.key == ConfigKey.TargetRepsMax -> {}

        field.key == ConfigKey.TargetRepsMin -> {
            RepsRow(context)
        }

        field.integer && !field.optional -> {
            CountRow(field, context)
        }

        field.unit == FieldUnit.Seconds && !field.optional -> {
            TimeRow(field, context)
        }

        field.key == ConfigKey.TargetWeightKg -> {
            WeightField(field, context)
        }

        else -> {
            ConfigInput(field, context.draft, context.onChange, context.units, compact = false)
        }
    }
}

/** "Sets  − 3 +". */
@Composable
private fun CountRow(
    field: ConfigField,
    context: FieldContext,
) {
    val label = fieldName(field, context.units)
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(top = Spacing.one),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = KettleType.default, color = KettleTheme.colors.text)
        Stepper(field, label, context)
    }
}

/**
 * The reps as a range, "Reps  − 8 +  to  − 12 +", and what it reads as under it. The top starts at the
 * bottom, a fixed count, and can't go below it; the bottom carries the top up past it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepsRow(context: FieldContext) {
    val fields = configFields(context.draft.type).associateBy { it.key }
    val min = fields.getValue(ConfigKey.TargetRepsMin)
    val max = fields.getValue(ConfigKey.TargetRepsMax)
    val colors = KettleTheme.colors
    Column(Modifier.padding(top = Spacing.two), verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
        Text(fieldName(min, context.units), style = KettleType.default, color = colors.text)
        // Wraps rather than clips: two steppers and "to" fill a phone's row at the largest text size.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.two),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Stepper(min, fieldName(min, context.units), context)
            Text(
                stringResource(R.string.exerciseEditor_repsTo),
                style = KettleType.default,
                color = colors.textSecondary,
            )
            Stepper(max, fieldName(max, context.units), context)
        }
        Text(repsHint(context.draft, min, max), style = KettleType.small, color = colors.textSecondary)
    }
}

/** "Reads as 3 × 8–12. The same number twice saves as a fixed count, 3 × 8." from what's set. */
@Composable
private fun repsHint(
    draft: ExerciseDraft,
    min: ConfigField,
    max: ConfigField,
): String {
    val sets = configFields(draft.type).first { it.key == ConfigKey.Sets }
    val count = draft.shownCount(sets).toInt()
    val bottom = draft.shownCount(min).toDouble()
    val top = draft.shownCount(max).toDouble()
    // The library's own "3 × 8–12", so the shape reads as every row that shows a reps target does.
    val fixed = stringResource(R.string.library_targetReps, rangeLabel(bottom, null), count, "")
    val shape = stringResource(R.string.library_targetReps, rangeLabel(bottom, top.takeIf { it > bottom }), count, "")
    return stringResource(R.string.exerciseEditor_repsHint, fixed, shape)
}

/**
 * − value +, each button named by the field it moves, and the value announced as it changes. A tap on
 * the value types one (#463), as the session editor's cells do: a 50-rep or 100-round count is dozens of
 * presses otherwise. Each keystroke is applied as [typing] takes it; Save judges its ceiling.
 */
@Composable
private fun Stepper(
    field: ConfigField,
    label: String,
    context: FieldContext,
) {
    val draft = context.draft
    var open by remember(field.key) { mutableStateOf(false) }

    /** What the field and the max it carries held as it opened: each keystroke is applied to these. */
    var opened by remember(field.key) { mutableStateOf(emptyMap<ConfigKey, String?>()) }

    // The draft as it is at each keystroke, not as it was when the field opened.
    val latest by rememberUpdatedState(context)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shown = "${draft.shownCount(field)}"
        StepButton(
            R.drawable.icon_remove,
            stringResource(R.string.common_decrease, label),
            draft.canStep(field, -1),
            shown,
        ) {
            context.onChange(draft.stepping(field, -1))
        }
        if (open) {
            Box(Modifier.widthIn(min = TYPING_WIDTH, max = TYPING_WIDTH)) {
                TypedCount(shown, label, onText = { text -> latest.retyped(field, opened, text) }) { open = false }
            }
        } else {
            CountValue(shown, label) {
                opened = typedKeys(field).associateWith { draft.values[it] }
                open = true
            }
        }
        StepButton(
            R.drawable.icon_add,
            stringResource(R.string.common_increase, label),
            draft.canStep(field, +1),
            shown,
        ) {
            context.onChange(draft.stepping(field, +1))
        }
    }
}

/** "Rest between sets  1:30 ⌄", on the workout editor's picker. */
@Composable
private fun TimeRow(
    field: ConfigField,
    context: FieldContext,
) {
    val label = stringResource(field.name.label)
    val draft = context.draft
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(top = Spacing.one),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = KettleType.default, color = KettleTheme.colors.text)
        DurationButton(label, draft.values[field.key]?.trim()?.toDoubleOrNull() ?: 0.0) {
            context.onChange(draft.copy(values = draft.values + (field.key to jsNumber(it))))
        }
    }
}

/**
 * The weight, typed in the reader's unit, and in pounds what the file keeps, "Your file keeps it as
 * 22 kg.": weights are stored in kilograms always, and an export says so.
 */
@Composable
private fun WeightField(
    field: ConfigField,
    context: FieldContext,
) {
    ConfigInput(field, context.draft, context.onChange, context.units, compact = false)
    if (context.units != UnitSystem.IMPERIAL) return
    context.draft.storedWeightKg(context.units, context.previousWeightKg)?.let {
        Text(
            stringResource(R.string.exerciseEditor_storedAs, jsNumber(it)),
            Modifier.padding(top = Spacing.one),
            style = KettleType.small,
            color = KettleTheme.colors.textSecondary,
        )
    }
}

private val ROW_HEIGHT = 52.dp

/** Wide enough for a ceiling's digits at the default size; the field scrolls past it at a larger one. */
private val TYPING_WIDTH = 72.dp
