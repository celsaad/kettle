package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.RunnerColors

// The two surfaces RN's runner repeats: a raised card, and a soft-tinted pill with an outline.

/** A raised card: the element fill, a hairline round [radius] corners, RN's 14dp inside. */
internal fun Modifier.runnerCard(radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape)
        .background(RunnerColors.backgroundElement)
        .border(1.dp, RunnerColors.border, shape)
        .padding(CARD_PAD)
}

/** A round shape filled with a soft [fill] and outlined in its [tint] at RN's 40%. */
internal fun Modifier.softPill(
    tint: Color,
    fill: Color,
): Modifier =
    clip(CircleShape)
        .background(fill)
        .border(1.dp, tint.copy(alpha = SOFT_BORDER_ALPHA), CircleShape)

/** RN's 0.4 outline on a soft fill, `rgba(207,106,55,0.4)` or the calm blue's: the tint at 40%. */
internal const val SOFT_BORDER_ALPHA = 0.4f
