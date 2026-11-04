package com.casco.kettle.shell.workouteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.dashedBorder
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.library.ExerciseBadge
import com.casco.kettle.shell.library.exerciseSummary
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * One plain block, as RN's `WorkoutBlockRow`. A rest is dashed, a gap in the workout rather than a
 * thing in it, and a rest whose duration this workout overrides says the override's seconds.
 */
@Composable
internal fun BlockRow(
    exercise: Exercise,
    block: WorkoutBlock.ExerciseBlock,
    units: UnitSystem,
    place: BlockPlace,
    onRemove: () -> Unit,
) {
    val colors = KettleTheme.colors
    val isRest = exercise.config is ExerciseConfig.Rest
    val overrideSec = block.configOverride?.durationSec?.takeIf { it != 0.0 }
    val summary =
        if (isRest && overrideSec != null) overrideSeconds(overrideSec) else exerciseSummary(exercise.config, units)
    val surface =
        if (isRest) {
            Modifier.dashedBorder(1.dp, colors.border, BlockRadius)
        } else {
            Modifier
                .clip(BlockShape)
                .background(colors.backgroundElement)
                .border(1.dp, colors.border, BlockShape)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .then(surface)
            .padding(start = Spacing.one, end = Spacing.one, top = Spacing.two, bottom = Spacing.two),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Grip(exercise.name, place)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(exercise.name, style = if (isRest) KettleType.default else KettleType.heading, color = colors.text)
            Text(summary, style = KettleType.small, color = colors.textSecondary)
        }
        ExerciseBadge(
            exercise.config,
            overrideLabel = overrideSec?.let { stringResource(R.string.workoutEditor_overrideBadge) },
        )
        RemoveButton(stringResource(R.string.workoutEditor_removeAccessibility, exercise.name), onClick = onRemove)
    }
}

/**
 * A rest override's seconds, as RN's `overrideSeconds` plural read them: a fractional one shown as
 * it is, under the plural's other form, which is the one i18next picks for a fraction in all three
 * languages. Truncating it to pick the form would show 2.5 s as 2.
 */
@Composable
private fun overrideSeconds(seconds: Double): String {
    val whole = seconds.toInt()
    if (whole.toDouble() == seconds) {
        return pluralText(R.plurals.workoutEditor_overrideSeconds, R.string.workoutEditor_overrideSeconds_zero, whole)
    }
    LocalConfiguration.current
    return LocalResources.current.getQuantityString(
        R.plurals.workoutEditor_overrideSeconds,
        OTHER_COUNT,
        jsNumber(seconds),
    )
}

/** A count every bundled language files under its plural's other form. */
private const val OTHER_COUNT = 2
