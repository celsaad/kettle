package com.casco.kettle.shell.art

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme

/**
 * The bundled drawing for exercise [id], or nothing at all, as RN's `ExerciseArt`. Nothing is the
 * important half: most of a real library is the user's own, and an empty frame or a placeholder on
 * every one of them would be worse than the silence.
 *
 * The drawing is app content, not user data, so it has a description to give, in the reader's
 * language: the movement, not the picture. Every drawing strokes in one colour, which [color] tints,
 * so one drawable serves both schemes and the always-dark runner.
 */
@Composable
fun ExerciseArt(
    id: String,
    modifier: Modifier = Modifier,
    width: Dp = DEFAULT_WIDTH,
    color: Color = KettleTheme.colors.textSecondary,
) {
    val drawable = artDrawables[id] ?: return
    Image(
        painterResource(drawable),
        contentDescription = artDescriptions[id]?.let { stringResource(it) },
        modifier = modifier.width(width).aspectRatio(ART_ASPECT),
        colorFilter = ColorFilter.tint(color),
    )
}

/** RN's default size. */
private val DEFAULT_WIDTH = 152.dp

/** Width over height: every drawing shares the `0 0 120 96` canvas. */
private const val ART_ASPECT = 120f / 96f
