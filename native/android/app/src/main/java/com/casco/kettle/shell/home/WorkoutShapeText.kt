package com.casco.kettle.shell.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.WorkoutShape
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import kotlin.reflect.KClass

/**
 * A workout's one-line summary, "5 blocks · mixed reps + hold · ~13 min", as RN's `formatWorkoutShape`
 * was: the next-up card and every row of Home's list read it.
 */
@Composable
fun workoutShapeText(shape: WorkoutShape): String {
    val labels = shape.types.map { stringResource(typeLabel(it)) }
    val types =
        when (labels.size) {
            0 -> stringResource(R.string.format_restOnly)
            1 -> labels.single()
            else -> stringResource(R.string.format_mixed, labels.joinToString(" + "))
        }
    val blocks = pluralText(R.plurals.format_block, R.string.format_block_zero, shape.blockCount)
    return stringResource(R.string.format_workoutShape, blocks, shape.estimatedMinutes, types)
}

@StringRes
private fun typeLabel(type: KClass<out ExerciseConfig>): Int =
    when (type) {
        ExerciseConfig.Hiit::class -> R.string.format_type_hiit
        ExerciseConfig.Emom::class -> R.string.format_type_emom
        ExerciseConfig.Amrap::class -> R.string.format_type_amrap
        ExerciseConfig.Reps::class -> R.string.format_type_reps
        ExerciseConfig.TimedHold::class -> R.string.format_type_timed_hold
        ExerciseConfig.Cardio::class -> R.string.format_type_cardio
        else -> R.string.format_type_rest
    }
