package com.casco.kettle.shell.workouteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.DurationButton
import com.casco.kettle.shell.form.RemoveButton
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * One plain block (#440), a flat row: its grip, its name and target ("3 × 6–12 · rest 1:15"), and ×. A
 * rest has no target line; its length is a time button instead, set for this workout alone, so the
 * Rest exercise every workout shares keeps its own.
 */
@Composable
internal fun BlockRow(
    exercise: Exercise,
    block: WorkoutBlock.ExerciseBlock,
    units: UnitSystem,
    place: BlockPlace,
    actions: BlockActions,
) {
    val colors = KettleTheme.colors
    val rest = exercise.config as? ExerciseConfig.Rest
    Column {
        ListRowSeparator()
        Row(
            Modifier.fillMaxWidth().heightIn(min = ListRowMinHeight),
            horizontalArrangement = Arrangement.spacedBy(Spacing.one),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Grip(exercise.name, place)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name, style = KettleType.rowTitle, color = colors.text)
                // A rest says its length on its button instead.
                val target = if (rest == null) blockTarget(exercise.config, units) else null
                target?.let { Text(it, style = KettleType.small, color = colors.textSecondary) }
            }
            rest?.let {
                DurationButton(
                    stringResource(R.string.workoutEditor_restLength),
                    block.configOverride?.durationSec ?: it.durationSec,
                ) { seconds -> actions.restLength(seconds, it.durationSec) }
            }
            RemoveButton(
                stringResource(R.string.workoutEditor_removeAccessibility, exercise.name),
                onClick = actions.remove,
            )
        }
    }
}
