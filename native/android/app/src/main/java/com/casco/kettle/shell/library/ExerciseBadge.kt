package com.casco.kettle.shell.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType

/**
 * An exercise's type as a tag, as RN's `ExerciseBadge`. The labels are the format's own abbreviations,
 * untranslated in every locale, as RN's are. HIIT and HOLD take the accent and the calm accent, so the
 * two timed types stand out from the counted ones.
 */
@Composable
fun ExerciseBadge(config: ExerciseConfig) {
    val colors = KettleTheme.colors
    val (label, background, text) =
        when (config) {
            is ExerciseConfig.Hiit -> Triple("HIIT", colors.accentSoft, colors.accentText)
            is ExerciseConfig.TimedHold -> Triple("HOLD", colors.accentCalmSoft, colors.accentCalmText)
            is ExerciseConfig.Reps -> Triple("REPS", colors.backgroundSelected, colors.textSecondary)
            is ExerciseConfig.Cardio -> Triple("CARDIO", colors.backgroundSelected, colors.textSecondary)
            is ExerciseConfig.Emom -> Triple("EMOM", colors.backgroundSelected, colors.textSecondary)
            is ExerciseConfig.Amrap -> Triple("AMRAP", colors.backgroundSelected, colors.textSecondary)
            is ExerciseConfig.Rest -> Triple("REST", colors.backgroundSelected, colors.textSecondary)
        }
    Text(
        label,
        Modifier
            .clip(BadgeShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = KettleType.code,
        color = text,
    )
}

private val BadgeShape = RoundedCornerShape(7.dp)
