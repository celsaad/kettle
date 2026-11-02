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
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType

/**
 * An exercise's type as a tag, as RN's `ExerciseBadge`. The labels are the format's own abbreviations,
 * untranslated in every locale, as RN's are. HIIT and HOLD take the accent and the calm accent, so the
 * two timed types stand out from the counted ones.
 */
@Composable
fun ExerciseBadge(config: ExerciseConfig) =
    Badge(
        when (config) {
            is ExerciseConfig.Hiit -> BadgeKind.Hiit
            is ExerciseConfig.TimedHold -> BadgeKind.Hold
            is ExerciseConfig.Reps -> BadgeKind.Reps
            is ExerciseConfig.Cardio -> BadgeKind.Cardio
            is ExerciseConfig.Emom -> BadgeKind.Emom
            is ExerciseConfig.Amrap -> BadgeKind.Amrap
            is ExerciseConfig.Rest -> BadgeKind.Rest
        },
    )

/** The same tag for a logged entry, which is of the type its exercise was when it ran. */
@Composable
fun ExerciseBadge(entry: SessionEntry) =
    Badge(
        when (entry) {
            is SessionEntry.Hiit -> BadgeKind.Hiit
            is SessionEntry.TimedHold -> BadgeKind.Hold
            is SessionEntry.Reps -> BadgeKind.Reps
            is SessionEntry.Cardio -> BadgeKind.Cardio
            is SessionEntry.Emom -> BadgeKind.Emom
            is SessionEntry.Amrap -> BadgeKind.Amrap
            is SessionEntry.Rest -> BadgeKind.Rest
        },
    )

private enum class BadgeKind(
    val label: String,
) {
    Hiit("HIIT"),
    Hold("HOLD"),
    Reps("REPS"),
    Cardio("CARDIO"),
    Emom("EMOM"),
    Amrap("AMRAP"),
    Rest("REST"),
}

@Composable
private fun Badge(kind: BadgeKind) {
    val colors = KettleTheme.colors
    val (background, text) =
        when (kind) {
            BadgeKind.Hiit -> colors.accentSoft to colors.accentText
            BadgeKind.Hold -> colors.accentCalmSoft to colors.accentCalmText
            else -> colors.backgroundSelected to colors.textSecondary
        }
    Text(
        kind.label,
        Modifier
            .clip(BadgeShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = KettleType.code,
        color = text,
    )
}

private val BadgeShape = RoundedCornerShape(7.dp)
