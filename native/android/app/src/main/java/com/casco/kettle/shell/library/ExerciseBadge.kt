package com.casco.kettle.shell.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType

/**
 * An exercise's type as a tag. HIIT, EMOM and AMRAP are acronyms, the same in
 * every locale; the other words are translated, and the tag's capitals are drawn on the translated word
 * (#520). HIIT and hold take the accent and the calm accent, so the two timed types stand out from the
 * counted ones.
 */
@Composable
fun ExerciseBadge(
    config: ExerciseConfig,
    /**
     * Shown in place of the type, in the accent: the workout editor's mark on
     * a rest block whose duration the workout sets for itself.
     */
    overrideLabel: String? = null,
) = Badge(
    when (config) {
        is ExerciseConfig.Hiit -> BadgeKind.Hiit
        is ExerciseConfig.TimedHold -> BadgeKind.Hold
        is ExerciseConfig.Reps -> BadgeKind.Reps
        is ExerciseConfig.Cardio -> BadgeKind.Cardio
        is ExerciseConfig.Emom -> BadgeKind.Emom
        is ExerciseConfig.Amrap -> BadgeKind.Amrap
        is ExerciseConfig.Rest -> BadgeKind.Rest
    },
    overrideLabel,
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

private enum class BadgeKind {
    Hiit,
    Hold,
    Reps,
    Cardio,
    Emom,
    Amrap,
    Rest,
}

// Upper-cased in Locale.ROOT, the bundles' casing, not the device's: a Turkish phone falls back to English and
// would read "CARDİO".
@Composable
private fun label(kind: BadgeKind): String =
    when (kind) {
        BadgeKind.Hiit -> "HIIT"
        BadgeKind.Emom -> "EMOM"
        BadgeKind.Amrap -> "AMRAP"
        BadgeKind.Hold -> stringResource(R.string.library_badge_hold)
        BadgeKind.Reps -> stringResource(R.string.library_badge_reps)
        BadgeKind.Cardio -> stringResource(R.string.library_badge_cardio)
        BadgeKind.Rest -> stringResource(R.string.library_badge_rest)
    }.uppercase()

@Composable
private fun Badge(
    kind: BadgeKind,
    overrideLabel: String? = null,
) {
    val colors = KettleTheme.colors
    val (background, text) =
        when {
            overrideLabel != null -> colors.accentSoft to colors.accentText
            kind == BadgeKind.Hiit -> colors.accentSoft to colors.accentText
            kind == BadgeKind.Hold -> colors.accentCalmSoft to colors.accentCalmText
            else -> colors.backgroundSelected to colors.textSecondary
        }
    Text(
        overrideLabel ?: label(kind),
        Modifier
            .clip(BadgeShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = KettleType.code,
        color = text,
    )
}

private val BadgeShape = RoundedCornerShape(7.dp)
