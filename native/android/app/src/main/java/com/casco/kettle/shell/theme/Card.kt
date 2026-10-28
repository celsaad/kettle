package com.casco.kettle.shell.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A card's surface: the raised fill, a hairline border, rounded [radius]. One privileged object, per
 * AGENTS.md's card-versus-rows rule; a list of peers is rows instead. Padding is the caller's, since
 * RN's cards each set their own.
 */
@Composable
fun Modifier.card(radius: Dp = CardRadius): Modifier {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(radius)
    return clip(shape)
        .background(colors.backgroundElement)
        .border(1.dp, colors.border, shape)
}

/** RN's `borderRadius: 14` on Stats' tiles and chart cards. */
val CardRadius = 14.dp
