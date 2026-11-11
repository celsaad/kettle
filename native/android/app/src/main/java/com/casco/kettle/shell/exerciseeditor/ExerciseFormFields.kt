package com.casco.kettle.shell.exerciseeditor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ConfigField
import com.casco.kettle.domain.ConfigProblem
import com.casco.kettle.domain.ExerciseDraft
import com.casco.kettle.domain.ExerciseProblem
import com.casco.kettle.domain.ExerciseType
import com.casco.kettle.domain.FORM_TYPES
import com.casco.kettle.domain.FieldName
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.configFields
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.form.withUnit
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * An exercise's name, type and config, as one form both the editor (#241) and the workout
 * editor's quick-add use, rather than RN's two copies that drifted. It holds nothing: [draft] is the
 * caller's, and every keystroke comes back through [onChange], so the caller decides what Save does.
 * [ExerciseNotesField] follows it, wherever the caller puts it.
 *
 * [typeLocked] for an exercise that exists: each type keys a different config, so switching would
 * read the form under keys the new type doesn't have. The pills stay, dimmed and announced disabled.
 * [compact] is the quick-add's smaller layout, in a card on the workout editor's picker, and puts the
 * keyboard on the name: it opens on a tap that means "a new one called…".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseFormFields(
    draft: ExerciseDraft,
    onChange: (ExerciseDraft) -> Unit,
    units: UnitSystem,
    typeLocked: Boolean,
    compact: Boolean = false,
) {
    val look = fieldLook(compact)
    val nameFocus = remember { FocusRequester() }
    if (compact) LaunchedEffect(Unit) { nameFocus.requestFocus() }

    Column {
        Label(stringResource(R.string.common_name), compact)
        FormField(
            draft.name,
            { onChange(draft.copy(name = it)) },
            name = stringResource(R.string.common_name),
            placeholder = stringResource(R.string.exerciseEditor_namePlaceholder),
            look.copy(kind = FieldKind.Text, focus = nameFocus),
        )

        Label(stringResource(R.string.exerciseEditor_type), compact)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.one else Spacing.two - 2.dp),
        ) {
            for (type in FORM_TYPES) {
                TypePill(type, type == draft.type, enabled = !typeLocked, compact) { onChange(draft.copy(type = type)) }
            }
        }

        Column(Modifier.padding(top = if (compact) 2.dp else Spacing.two)) {
            for (field in configFields(draft.type)) ConfigInput(field, draft, onChange, units, compact)
        }
    }
}

/**
 * The form's notes, apart from [ExerciseFormFields] so the editor can put the drawing between the
 * config and them, where RN's sat. The sheet's notes are a box to write a cue in; the quick-add's a
 * line, as RN's were.
 */
@Composable
fun ExerciseNotesField(
    draft: ExerciseDraft,
    onChange: (ExerciseDraft) -> Unit,
    compact: Boolean = false,
) {
    val look = fieldLook(compact)
    Label(stringResource(R.string.exerciseEditor_notesOptional), compact)
    FormField(
        draft.notes,
        { onChange(draft.copy(notes = it)) },
        name = stringResource(R.string.exerciseEditor_notesOptional),
        placeholder = stringResource(R.string.exerciseEditor_notesPlaceholder),
        if (compact) look.copy(kind = FieldKind.Text) else look.copy(kind = FieldKind.Notes, minHeight = NOTES_HEIGHT),
    )
}

/** The quick-add's fields are smaller, and fill with the background, since they sit in a card. */
@Composable
private fun fieldLook(compact: Boolean) =
    if (compact) {
        FieldOptions(minHeight = 44.dp, fill = KettleTheme.colors.background)
    } else {
        FieldOptions(fill = KettleTheme.colors.backgroundElement)
    }

/**
 * One type to choose. The pill is RN's size, inside a 44dp target that grows with the reader's text
 * size; [enabled] false dims it, and `selectable` then announces it disabled, so a locked type isn't
 * a control that looks dimmed and still says it can be tapped.
 */
@Composable
private fun TypePill(
    type: ExerciseType,
    active: Boolean,
    enabled: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(PillShape)
            .selectable(selected = active, enabled = enabled, role = Role.Button, onClick = onSelect)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        val pill =
            Modifier
                .clip(PillShape)
                .then(
                    if (active) {
                        Modifier.background(colors.text)
                    } else {
                        Modifier.background(if (compact) colors.background else colors.backgroundElement).border(
                            1.dp,
                            colors.border,
                            PillShape,
                        )
                    },
                ).padding(horizontal = if (compact) Spacing.two - 2.dp else Spacing.three - 3.dp, vertical = 7.dp)
        Text(
            stringResource(type.label),
            pill,
            style = KettleType.small,
            color = if (active) colors.onAccent else colors.textSecondary,
        )
    }
}

@Composable
private fun ConfigInput(
    field: ConfigField,
    draft: ExerciseDraft,
    onChange: (ExerciseDraft) -> Unit,
    units: UnitSystem,
    compact: Boolean,
) {
    val named = fieldName(field, units)
    Label(withOptional(field, named), compact)
    FormField(
        draft.values[field.key].orEmpty(),
        { onChange(draft.copy(values = draft.values + (field.key to it))) },
        name = named,
        placeholder = "0",
        fieldLook(compact),
    )
}

/** [field]'s name with its unit, "Weight (lb)": the number means nothing without it. */
@Composable
internal fun fieldName(
    field: ConfigField,
    units: UnitSystem,
): String = withUnit(stringResource(field.name.label), field.unit, units)

/** [named], marked optional if [field] is: "Hold (max) (sec) · optional". A required field's is unchanged. */
@Composable
internal fun withOptional(
    field: ConfigField,
    named: String,
): String =
    if (field.optional) {
        stringResource(R.string.exerciseForm_optionalField, named, stringResource(R.string.exerciseEditor_optional))
    } else {
        named
    }

@Composable
private fun Label(
    text: String,
    compact: Boolean,
) = Text(
    text,
    Modifier.padding(
        top = if (compact) Spacing.two - 2.dp else Spacing.three - 2.dp,
        bottom = if (compact) 4.dp else Spacing.one,
    ),
    style = KettleType.small,
    color = KettleTheme.colors.textSecondary,
)

/** Why Save refused, in words: the name's problems, or the config's. */
@Composable
fun exerciseProblemText(problem: ExerciseProblem): String =
    when (problem) {
        ExerciseProblem.NameRequired -> stringResource(R.string.common_nameRequired)
        ExerciseProblem.NoId -> stringResource(R.string.common_couldNotDeriveId)
        is ExerciseProblem.Config -> configProblemText(problem.problem)
    }

/** Why a config can't be saved, in words: the exercise form's, and the override panel's (#243). */
@Composable
internal fun configProblemText(problem: ConfigProblem): String =
    when (problem) {
        is ConfigProblem.Required -> {
            stringResource(R.string.exerciseForm_error_required, stringResource(problem.field.label))
        }

        is ConfigProblem.MustBeNumber -> {
            stringResource(R.string.exerciseForm_error_mustBeNumber, stringResource(problem.field.label))
        }

        is ConfigProblem.MustBeAtLeast -> {
            stringResource(
                R.string.exerciseForm_error_mustBeAtLeast,
                stringResource(problem.field.label),
                jsNumber(problem.min),
            )
        }

        is ConfigProblem.MustBeAtMost -> {
            stringResource(
                R.string.exerciseForm_error_mustBeAtMost,
                stringResource(problem.field.label),
                jsNumber(problem.max),
            )
        }

        is ConfigProblem.MustBeWhole -> {
            stringResource(R.string.exerciseForm_error_mustBeWhole, stringResource(problem.field.label))
        }

        is ConfigProblem.TooManyIntervals -> {
            stringResource(R.string.exerciseForm_error_tooManyIntervals, problem.max)
        }

        ConfigProblem.RepsMaxBelowMin -> {
            stringResource(R.string.exerciseForm_error_repsMaxBelowMin)
        }

        ConfigProblem.HoldMaxNeedsMin -> {
            stringResource(R.string.exerciseForm_error_holdMaxNeedsMin)
        }

        ConfigProblem.HoldMaxBelowMin -> {
            stringResource(R.string.exerciseForm_error_holdMaxBelowMin)
        }
    }

@get:StringRes
internal val ExerciseType.label: Int
    get() =
        when (this) {
            ExerciseType.Reps -> R.string.exerciseForm_type_reps
            ExerciseType.TimedHold -> R.string.exerciseForm_type_hold
            ExerciseType.Hiit -> R.string.exerciseForm_type_hiit
            ExerciseType.Emom -> R.string.exerciseForm_type_emom
            ExerciseType.Amrap -> R.string.exerciseForm_type_amrap
            ExerciseType.Cardio -> R.string.exerciseForm_type_cardio
            ExerciseType.Rest -> R.string.exerciseForm_type_rest
        }

@get:StringRes
internal val FieldName.label: Int
    get() =
        when (this) {
            FieldName.Work -> R.string.exerciseForm_field_work

            FieldName.Rest -> R.string.exerciseForm_field_rest

            FieldName.Rounds -> R.string.exerciseForm_field_rounds

            FieldName.Interval -> R.string.exerciseForm_field_interval

            FieldName.Total -> R.string.exerciseForm_field_total

            FieldName.TargetReps -> R.string.exerciseForm_field_targetReps

            FieldName.TargetRepsMax -> R.string.exerciseForm_field_targetRepsMax

            FieldName.TimeCap -> R.string.exerciseForm_field_timeCap

            FieldName.Sets -> R.string.exerciseForm_field_sets

            FieldName.Weight -> R.string.exerciseForm_field_weight

            FieldName.Hold -> R.string.exerciseForm_field_hold

            FieldName.HoldMax -> R.string.exerciseForm_field_holdMax

            FieldName.Duration -> R.string.exerciseForm_field_duration

            FieldName.Distance -> R.string.exerciseForm_field_distance

            // These two carry their unit in the label, so a problem names them as the panel shows them.
            FieldName.RestPerExercise -> R.string.overrideEditor_restPerExercise

            FieldName.RestPerRound -> R.string.overrideEditor_restPerRound
        }

private val PillShape = RoundedCornerShape(percent = 50)

/** RN's `opacity: 0.5` on a locked pill. */
private const val DISABLED_ALPHA = 0.5f

private val NOTES_HEIGHT = 90.dp
