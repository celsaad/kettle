package com.casco.kettle.shell.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The width the widest of [texts] takes in [style] at the reader's text size, plus [FIT_SLACK]: for a
 * layout that decides whether its words fit before laying them out (#469, #480), tabs, the runner's
 * row, the receipt. The slack is the rounding between the estimate and the drawing: a word measured
 * as just fitting, then laid out a rounded pixel narrower, breaks inside itself, so a check against
 * this width errs toward the arrangement that has room.
 */
@Composable
fun rememberWidestText(
    texts: List<String>,
    style: TextStyle,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(texts, style, density) {
        val widest = texts.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        with(density) { widest.toDp() } + FIT_SLACK
    }
}

private val FIT_SLACK = 2.dp
