package com.casco.kettle.shell.runner

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

// Where RN's layout and Compose's part ways, the adjustments that put things where RN draws them.

/**
 * RN's line height on Android (`CustomLineHeightSpan`), which is CSS half-leading: a line box of
 * exactly [line] px, the difference from the font's own height split evenly above and below, and the
 * glyphs free to draw past it. Compose's `lineHeight` can't shrink a single line: it pads the first
 * and last line back out so nothing clips, which left every numeral the font's full height and a
 * wider gap under each clock than RN's. So the text is laid out at its own height and trimmed here.
 */
internal fun Modifier.halfLeading(line: Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        layout(placeable.width, line) { placeable.place(0, (line - placeable.height) / 2) }
    }

/**
 * Lets 48dp targets overhang the space around them by [top] and [bottom], so their words sit where RN
 * draws them: RN's controls are often just text with hitSlop for the rest, and laid out at 48dp they
 * push everything below them down. The targets keep their size; only the layout height shrinks. Use it
 * where the overhang lands on space nothing else can be tapped in: a gap, padding, or plain text.
 */
internal fun Modifier.bleed(
    top: Dp,
    bottom: Dp,
): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val above = top.roundToPx()
        layout(placeable.width, maxOf(placeable.height - above - bottom.roundToPx(), 0)) {
            placeable.place(0, -above)
        }
    }

/**
 * Hangs a target's empty edges out past its place in the row by [start] and [end], so what it draws
 * lines up where RN's does: RN's controls are often smaller than 48dp, or pad only one side. Only
 * over space nothing else can be tapped in: the screen's margin, or a card's padding.
 */
internal fun Modifier.hang(
    start: Dp = 0.dp,
    end: Dp = 0.dp,
): Modifier =
    layout { measurable, constraints ->
        val before = start.roundToPx()
        val total = before + end.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = total))
        layout(maxOf(placeable.width - total, 0), placeable.height) { placeable.place(-before, 0) }
    }

/** Reserves [label]'s width without drawing or announcing it, so two tracks line up in a column. */
@Composable
internal fun WidthOf(label: String) {
    Text(label, Modifier.alpha(0f).clearAndSetSemantics {}, style = QuietStyle)
}
