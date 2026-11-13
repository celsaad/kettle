package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

// The pieces the runner's screens share that aren't controls: numerals, bars, captions.

/** The record badge: words, not colour alone, which is the one signal everyone can read. */
@Composable
internal fun RecordBadge() {
    Box(
        Modifier
            .softPill(runnerColors.accentCalm, runnerColors.accentCalmSoft)
            .padding(horizontal = BADGE_PAD_H, vertical = Spacing.one),
    ) {
        Text(
            stringResource(R.string.session_complete_recordBadge),
            style = KettleType.code.copy(letterSpacing = CapsTracking),
            color = runnerColors.accentCalmOnSoft,
        )
    }
}

/**
 * A big number capped at 1.3× the font scale, so the largest accessibility sizes don't push the
 * screen's controls off the bottom, as RN's `maxFontSizeMultiplier`. Tabular, as RN's numeral type, so
 * a ticking clock doesn't shift sideways as its digits change. Its line is [leading] × its size, as RN
 * sets each one, or the font's own where RN sets none.
 */
@Composable
internal fun Numeral(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = runnerColors.numeral,
    leading: Float? = 1f,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, NUMERAL_SCALE_CAP)),
    ) {
        val line = leading?.let { with(LocalDensity.current) { (size * it).roundToPx() } }
        Text(
            text,
            if (line == null) modifier else modifier.halfLeading(line),
            style = KettleType.numeral.copy(fontSize = size, fontFeatureSettings = "tnum"),
            color = color,
        )
    }
}

/** A kicker in the code face; [label] is in caps already, as the bundles spell it. */
@Composable
internal fun Caps(
    label: String,
    modifier: Modifier = Modifier,
    tracking: TextUnit = CapsTracking,
) {
    Text(label, modifier, style = QuietStyle.copy(letterSpacing = tracking))
}

/** A bar filled to [fraction], 0 to 1, with an optional mark at [marker]. Decorative: no semantics. */
@Composable
internal fun FillBar(
    fraction: Float,
    marker: Float? = null,
) {
    // The box is the track's 6dp, as RN's: the mark stands out of it above and below without taking
    // layout space, so a hold's caption sits where an interval's does.
    Box(Modifier.testTag(BAR_TAG).width(BAR_WIDTH).height(TRACK_HEIGHT)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(runnerColors.track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(runnerColors.accent),
            )
        }
        if (marker != null) {
            Row(Modifier.fillMaxSize()) {
                if (marker > 0f) Spacer(Modifier.weight(marker))
                Box(
                    Modifier
                        .width(2.dp)
                        .requiredHeight(MARK_HEIGHT)
                        .background(runnerColors.textSecondary)
                        .testTag(MARKER_TAG),
                )
                if (marker < 1f) Spacer(Modifier.weight(1f - marker))
            }
        }
    }
}

/**
 * How far through the blocks, as one bar the width of the screen under the top bar (#409). Nothing
 * below two: one step is not progress. Fills through the step on screen, so the first of five reads as
 * under way.
 */
@Composable
internal fun ProgressTrack(
    total: Int,
    activeIndex: Int,
) {
    if (total < 2) return
    val done = minOf(activeIndex + 1, total)
    Box(
        Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .clip(CircleShape)
            .background(runnerColors.track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(done.toFloat(), 1f..total.toFloat(), total) },
    ) {
        Box(
            Modifier
                .fillMaxWidth(done.toFloat() / total)
                .fillMaxHeight()
                .background(runnerColors.accent),
        )
    }
}

/** The author's note, capped at two lines so it can't push what's being counted down the screen. */
@Composable
internal fun StepNotes(notes: String?) {
    if (notes.isNullOrEmpty()) return
    Text(
        notes,
        style = KettleType.small.copy(fontStyle = FontStyle.Italic),
        color = runnerColors.textSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Decorative, so findable only by tag: the hold's tests ask whether a bar and its mark are drawn. */
internal const val BAR_TAG = "fill-bar"
internal const val MARKER_TAG = "fill-bar-marker"

private const val NUMERAL_SCALE_CAP = 1.3f
private val BAR_WIDTH = 220.dp
private val MARK_HEIGHT = 16.dp
private val TRACK_HEIGHT = 6.dp
private val BADGE_PAD_H = 10.dp

/** RN's `Spacing.three - 2`, which several of its cards and rows use. */
internal val CARD_PAD = 14.dp
