package com.casco.kettle.shell.runner

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
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
import com.casco.kettle.domain.StepPreview
import com.casco.kettle.shell.R
import com.casco.kettle.shell.art.ExerciseArt
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.Spacing

// The pieces the runner's screens share that aren't controls: pills, numerals, bars, the Next card.

/** Whether the system has animations off, as RN's `useReducedMotion` reads it. */
@Composable
internal fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** The step's kind as a pill with a live dot, which pulses unless motion is reduced. */
@Composable
internal fun StepPill(
    label: String,
    calm: Boolean = false,
    live: Boolean = true,
) {
    val tint = if (calm) RunnerColors.accentCalm else RunnerColors.accent
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (calm) RunnerColors.accentCalmSoft else RunnerColors.accentSoft)
            .border(1.dp, tint.copy(alpha = SOFT_BORDER_ALPHA), CircleShape)
            .padding(horizontal = PILL_PAD_H, vertical = PILL_PAD_V),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        if (live) LiveDot(tint, if (calm) REST_PULSE_MS else PULSE_MS)
        Text(
            label,
            style = KettleType.code.copy(letterSpacing = CapsTracking),
            color = if (calm) RunnerColors.accentCalmOnSoft else RunnerColors.accentOnSoft,
        )
    }
}

@Composable
private fun LiveDot(
    tint: Color,
    periodMs: Int,
) {
    val alpha =
        if (reducedMotion()) {
            1f
        } else {
            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 1f,
                targetValue = PULSE_LOW,
                animationSpec = infiniteRepeatable(tween(periodMs), RepeatMode.Reverse),
                label = "pulse",
            )
            pulse
        }
    Box(
        Modifier
            .size(DOT)
            .alpha(alpha)
            .clip(CircleShape)
            .background(tint),
    )
}

/** The record badge: words, not colour alone, which is the one signal everyone can read. */
@Composable
internal fun RecordBadge() {
    Box(
        Modifier
            .clip(CircleShape)
            .background(RunnerColors.accentCalmSoft)
            .border(1.dp, RunnerColors.accentCalm.copy(alpha = SOFT_BORDER_ALPHA), CircleShape)
            .padding(horizontal = BADGE_PAD_H, vertical = Spacing.one),
    ) {
        Text(
            stringResource(R.string.session_complete_recordBadge),
            style = KettleType.code.copy(letterSpacing = CapsTracking),
            color = RunnerColors.accentCalmOnSoft,
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
    color: Color = RunnerColors.text,
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
                .background(RunnerColors.text.copy(alpha = TRACK_ALPHA)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(RunnerColors.accent),
            )
        }
        if (marker != null) {
            Row(Modifier.fillMaxSize()) {
                if (marker > 0f) Spacer(Modifier.weight(marker))
                Box(
                    Modifier
                        .width(2.dp)
                        .requiredHeight(MARK_HEIGHT)
                        .background(RunnerColors.textSecondary)
                        .testTag(MARKER_TAG),
                )
                if (marker < 1f) Spacer(Modifier.weight(1f - marker))
            }
        }
    }
}

/**
 * How far through the blocks, or a circuit's members, as one proportional bar of fixed width, so a
 * long workout can't push Finish off the screen. Nothing below two: one step is not progress. Fills
 * through the step on screen, so the first of five reads as under way.
 */
@Composable
internal fun ProgressTrack(
    total: Int,
    activeIndex: Int,
    calm: Boolean = false,
) {
    if (total < 2) return
    val done = minOf(activeIndex + 1, total)
    Box(
        Modifier
            .width(TRACK_WIDTH)
            .height(if (calm) 3.dp else 4.dp)
            .clip(CircleShape)
            .background(RunnerColors.text.copy(alpha = HEADER_TRACK_ALPHA))
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(done.toFloat(), 1f..total.toFloat(), total) },
    ) {
        Box(
            Modifier
                .fillMaxWidth(done.toFloat() / total)
                .fillMaxHeight()
                .background(if (calm) RunnerColors.accentCalm else RunnerColors.accent),
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
        color = RunnerColors.textSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * What's next, with its drawing. Dropped above 1.5× text, where these screens stop fitting, and its
 * drawing first, above 1.2×, being the one part that doesn't shrink.
 */
@Composable
internal fun NextCard(preview: StepPreview?) {
    val scale = LocalDensity.current.fontScale
    if (preview == null || scale > MAX_SCALE_FOR_CARD) return
    val resources = LocalResources.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RunnerColors.backgroundElement)
            .border(1.dp, RunnerColors.border, RoundedCornerShape(18.dp))
            .padding(CARD_PAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CARD_PAD),
    ) {
        Caps(stringResource(R.string.session_next))
        if (scale <= MAX_SCALE_FOR_ART) ExerciseArt(preview.exerciseId, width = 56.dp, color = RunnerColors.text)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.half)) {
            Text(preview.label, style = KettleType.heading, color = RunnerColors.text)
            Text(resources.previewDetail(preview), style = KettleType.small, color = RunnerColors.textSecondary)
        }
    }
}

/** Decorative, so findable only by tag: the hold's tests ask whether a bar and its mark are drawn. */
internal const val BAR_TAG = "fill-bar"
internal const val MARKER_TAG = "fill-bar-marker"

private const val PULSE_LOW = 0.35f
private const val PULSE_MS = 650
private const val REST_PULSE_MS = 800
private const val NUMERAL_SCALE_CAP = 1.3f
private const val MAX_SCALE_FOR_CARD = 1.5f
private const val MAX_SCALE_FOR_ART = 1.2f
private val TRACK_WIDTH = 116.dp
private val BAR_WIDTH = 220.dp
private val MARK_HEIGHT = 16.dp
private val TRACK_HEIGHT = 6.dp
private val PILL_PAD_H = 12.dp
private val PILL_PAD_V = 6.dp
private val DOT_GAP = 7.dp
private val BADGE_PAD_H = 10.dp
private val DOT = 7.dp

/** RN's `Spacing.three - 2`, which several of its cards and rows use. */
internal val CARD_PAD = 14.dp

/** RN's `rgba(243,239,228,0.22)`: the header's tracks, a shade stronger than a step bar's. */
private const val HEADER_TRACK_ALPHA = 0.22f

/** RN's `rgba(243,239,228,0.14)`: a step bar's track. */
private const val TRACK_ALPHA = 0.14f
