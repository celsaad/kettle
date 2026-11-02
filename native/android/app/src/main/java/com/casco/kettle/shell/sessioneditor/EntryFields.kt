package com.casco.kettle.shell.sessioneditor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.EntryField
import com.casco.kettle.domain.EntryProblem
import com.casco.kettle.domain.FieldUnit
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

// The session editor's fields and their words: `ENTRY_FIELDS`' labels and units, and the validation
// messages, which `:shared` returns as descriptors (`EntryProblem`) for the screen to put into words.

/** A number the user corrects, named by [name], since the label above it isn't tied to it. */
@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    name: String,
    placeholder: String,
) {
    val colors = KettleTheme.colors
    BasicTextField(
        value,
        onValueChange,
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = name },
        textStyle = KettleType.default.copy(fontSize = 15.sp, color = colors.text),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        cursorBrush = SolidColor(colors.text),
        decorationBox = { input ->
            Box(
                Modifier
                    .fillMaxWidth()
                    // RN's 46px, as a floor: heightIn, never height, which clips at large text sizes.
                    .heightIn(min = 46.dp)
                    .clip(FieldShape)
                    .border(1.dp, colors.border, FieldShape)
                    .background(colors.background)
                    .padding(horizontal = Spacing.two),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = KettleType.default.copy(fontSize = 15.sp),
                        color = colors.textSecondary,
                    )
                }
                input()
            }
        },
    )
}

/** The field's name with its unit, as RN wrote it: "Load (kg)". */
@Composable
internal fun fieldLabel(
    field: EntryField,
    units: UnitSystem,
): String {
    val label = stringResource(field.label)
    val unit =
        when (field.unit) {
            FieldUnit.Weight -> {
                stringResource(
                    if (units ==
                        UnitSystem.IMPERIAL
                    ) {
                        R.string.units_lb
                    } else {
                        R.string.units_kg
                    },
                )
            }

            // RN's literal suffixes, the same in every locale, as RN's `unit` strings are.
            FieldUnit.Seconds -> {
                "sec"
            }

            FieldUnit.Metres -> {
                "m"
            }

            null -> {
                null
            }
        }
    return if (unit == null) label else "$label ($unit)"
}

@Composable
internal fun problemText(problem: EntryProblem): String =
    when (problem) {
        is EntryProblem.Required -> {
            stringResource(R.string.exerciseForm_error_required, stringResource(problem.field.label))
        }

        is EntryProblem.MustBeNumber -> {
            stringResource(R.string.exerciseForm_error_mustBeNumber, stringResource(problem.field.label))
        }

        is EntryProblem.MustBeAtLeast -> {
            stringResource(
                R.string.exerciseForm_error_mustBeAtLeast,
                stringResource(problem.field.label),
                jsNumber(problem.min),
            )
        }

        is EntryProblem.MustBeAtMost -> {
            stringResource(
                R.string.exerciseForm_error_mustBeAtMost,
                stringResource(problem.field.label),
                jsNumber(problem.max),
            )
        }

        is EntryProblem.MustBeWhole -> {
            stringResource(R.string.exerciseForm_error_mustBeWhole, stringResource(problem.field.label))
        }

        EntryProblem.CardioNeedsOne -> {
            stringResource(R.string.sessionEditor_error_cardioNeedsOne)
        }
    }

@get:StringRes
private val EntryField.label: Int
    get() =
        when (this) {
            EntryField.Reps -> R.string.sessionEditor_field_reps
            EntryField.Load -> R.string.sessionEditor_field_load
            EntryField.Rpe -> R.string.sessionEditor_field_rpe
            EntryField.Hold -> R.string.sessionEditor_field_hold
            EntryField.Rounds -> R.string.sessionEditor_field_rounds
            EntryField.ExtraReps -> R.string.sessionEditor_field_extraReps
            EntryField.Duration -> R.string.sessionEditor_field_duration
            EntryField.Distance -> R.string.sessionEditor_field_distance
        }

private val FieldShape = RoundedCornerShape(12.dp)
