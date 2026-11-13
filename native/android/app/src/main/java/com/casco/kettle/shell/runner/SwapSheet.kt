package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.SwapChoices
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.rankByName
import com.casco.kettle.shell.R
import com.casco.kettle.shell.library.exerciseSummary
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The swap sheet (#417), as the canvas draws it: what's swapped and for how long, search always there,
 * the exercise's own type ("Also reps") apart from every other type, each row with its plan, and "Keep
 * Goblet Squat" as the way out. A swap is the rest of this exercise, not the session: the same exercise
 * later in the workout keeps its own (`RunnerEdits.kt`'s `swapped`), which the line under the title says.
 */
@Composable
internal fun SwapSheet(
    choices: SwapChoices,
    units: UnitSystem,
    onCancel: () -> Unit,
    onSelect: (Exercise) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val same = remember(choices, query) { rankByName(choices.same, query) { it.name } }
    val other = remember(choices, query) { rankByName(choices.other, query) { it.name } }
    val name = choices.current.name
    val title = stringResource(R.string.session_swap_title, name)
    // The backdrop still closes it, but unnamed: Keep below is the same way out, as on End early.
    OverlaySheet(null, onCancel, title) {
        Column {
            Text(title, Modifier.semantics { heading() }, style = KettleType.heading, color = runnerColors.text)
            Text(
                stringResource(R.string.session_swap_replacing),
                style = KettleType.small,
                color = runnerColors.textSecondary,
            )
        }
        PickerSearch(query) { query = it }
        val alsoLabel = stringResource(alsoLabel(choices.current.config))
        val otherLabel = stringResource(R.string.session_swap_otherTypes)
        LazyColumn(Modifier.heightIn(max = OPTIONS_MAX_HEIGHT)) {
            if (same.isEmpty() && other.isEmpty()) {
                item { NoMatches(query) }
            }
            group(alsoLabel, same, units, typed = false, onSelect)
            group(otherLabel, other, units, typed = true, onSelect)
        }
        OutlineButton(
            stringResource(R.string.session_swap_keep, name),
            onCancel,
            Modifier.fillMaxWidth(),
            look = ButtonLook.Compact,
        )
    }
}

/**
 * One group under its heading, nothing when search leaves it empty. [typed] rows say their type before
 * their plan, being of another type than the one swapped out.
 */
private fun LazyListScope.group(
    label: String,
    exercises: List<Exercise>,
    units: UnitSystem,
    typed: Boolean,
    onSelect: (Exercise) -> Unit,
) {
    if (exercises.isEmpty()) return
    item(key = label) {
        Text(
            label,
            Modifier.padding(top = Spacing.two, bottom = Spacing.one).semantics { heading() },
            style = KettleType.smallMedium,
            color = runnerColors.textSecondary,
        )
    }
    itemsIndexed(exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
        PickerRow(exercise.name, first = index == 0, planText(exercise.config, units, typed)) { onSelect(exercise) }
    }
}

/** The exercise's plan as the library lists it, after its type where the type differs. */
@Composable
private fun planText(
    config: ExerciseConfig,
    units: UnitSystem,
    typed: Boolean,
): String {
    val summary = exerciseSummary(config, units)
    return if (typed) {
        stringResource(
            R.string.session_swap_otherMeta,
            summary,
            stringResource(typeLabel(config)),
        )
    } else {
        summary
    }
}

private fun alsoLabel(config: ExerciseConfig): Int =
    when (config) {
        is ExerciseConfig.Reps -> R.string.session_swap_also_reps

        is ExerciseConfig.TimedHold -> R.string.session_swap_also_hold

        is ExerciseConfig.Hiit -> R.string.session_swap_also_hiit

        is ExerciseConfig.Emom -> R.string.session_swap_also_emom

        is ExerciseConfig.Amrap -> R.string.session_swap_also_amrap

        // A rest is never swapped (swapChoices leaves it out); cardio is what's left.
        is ExerciseConfig.Cardio, is ExerciseConfig.Rest -> R.string.session_swap_also_cardio
    }

private fun typeLabel(config: ExerciseConfig): Int =
    when (config) {
        is ExerciseConfig.Reps -> R.string.exerciseForm_type_reps
        is ExerciseConfig.TimedHold -> R.string.exerciseForm_type_hold
        is ExerciseConfig.Hiit -> R.string.exerciseForm_type_hiit
        is ExerciseConfig.Emom -> R.string.exerciseForm_type_emom
        is ExerciseConfig.Amrap -> R.string.exerciseForm_type_amrap
        is ExerciseConfig.Cardio -> R.string.exerciseForm_type_cardio
        is ExerciseConfig.Rest -> R.string.exerciseForm_type_rest
    }

private val OPTIONS_MAX_HEIGHT = 360.dp
