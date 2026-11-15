package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.casco.kettle.domain.RecordKind
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.InkBadge
import com.casco.kettle.shell.theme.KettleType

// The pieces the runner's screens share that aren't controls: numerals, bars, captions.

/**
 * The record badge: words, not colour alone, which is the one signal everyone can read. The shared
 * [InkBadge], History's too, on the field's own ink (#475), where this one was rest-blue. [label] names the
 * record on the receipt ("PR · heaviest yet"); a set's screen says "PR" alone.
 */
@Composable
internal fun RecordBadge(label: String = stringResource(R.string.session_complete_recordBadge)) =
    InkBadge(label, runnerColors.text, runnerColors.background)

/** What a receipt's badge says of [kind]: "PR · heaviest yet", the record it set. */
@Composable
internal fun recordLabel(kind: RecordKind): String =
    stringResource(
        when (kind) {
            RecordKind.HeaviestSet -> R.string.session_complete_recordHeaviest
            RecordKind.MostReps -> R.string.session_complete_recordMostReps
            RecordKind.LongestHold -> R.string.session_complete_recordLongestHold
            RecordKind.MostRounds -> R.string.session_complete_recordMostRounds
        },
    )

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

/**
 * A bar the column's width (#471), filled to [fraction], 0 to 1, in [fill], with an optional range band
 * (#417) outlined from [bandStart] to its end: a hold's range, the bar running to its top. Decorative: no
 * semantics.
 */
@Composable
internal fun FillBar(
    fraction: Float,
    bandStart: Float? = null,
    fill: Color = runnerColors.accent,
) {
    // The box is the track's 6dp, as RN's: the band stands out of it above and below without taking
    // layout space, so a hold's caption sits where an interval's does.
    Box(Modifier.testTag(BAR_TAG).fillMaxWidth().height(TRACK_HEIGHT)) {
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
                    .background(fill),
            )
        }
        if (bandStart != null && bandStart < 1f) {
            Row(Modifier.fillMaxSize()) {
                if (bandStart > 0f) Spacer(Modifier.weight(bandStart))
                Box(
                    Modifier
                        .weight(1f - bandStart)
                        .requiredHeight(BAND_HEIGHT)
                        .border(BAND_STROKE, runnerColors.text, BandShape)
                        .testTag(BAND_TAG),
                )
            }
        }
    }
}

/**
 * How far through the session's work, as one bar the width of the screen under the top bar (#409):
 * [done] work steps of [total], Coming up's own count (#476), where it counted blocks and filled through
 * the one on screen. Nothing below two steps: one is not progress.
 */
@Composable
internal fun ProgressTrack(
    done: Int,
    total: Int,
) {
    if (total < 2) return
    Box(
        Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .clip(CircleShape)
            .background(runnerColors.track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(done.toFloat(), 0f..total.toFloat(), total) },
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

/** Decorative, so findable only by tag: the hold's tests ask whether a bar and its band are drawn. */
internal const val BAR_TAG = "fill-bar"
internal const val BAND_TAG = "fill-bar-band"

private const val NUMERAL_SCALE_CAP = 1.3f
private val BAND_HEIGHT = 16.dp
private val BAND_STROKE = 2.dp
private val BandShape = RoundedCornerShape(4.dp)
private val TRACK_HEIGHT = 6.dp

/** RN's `Spacing.three - 2`, which several of its cards and rows use. */
internal val CARD_PAD = 14.dp
