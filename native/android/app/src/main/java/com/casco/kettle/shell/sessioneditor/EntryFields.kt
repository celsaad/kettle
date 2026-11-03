package com.casco.kettle.shell.sessioneditor

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.EntryField
import com.casco.kettle.domain.EntryProblem
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.unitSuffix

// The session editor's fields' words: `ENTRY_FIELDS`' labels and units, and the validation
// messages, which `:shared` returns as descriptors (`EntryProblem`) for the screen to put into words.

/** The field's name with its unit, as RN wrote it: "Load (kg)". */
@Composable
internal fun fieldLabel(
    field: EntryField,
    units: UnitSystem,
): String {
    val label = stringResource(field.label)
    val unit = unitSuffix(field.unit, units)
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
