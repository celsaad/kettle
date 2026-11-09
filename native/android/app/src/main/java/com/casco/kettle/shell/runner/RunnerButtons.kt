package com.casco.kettle.shell.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.Spacing

// The runner's controls. Always dark ([RunnerColors], whatever the scheme), and every one at least
// [MinTarget] tall and its own target, which is #263's fix: the set row's −/+ were 26px, 4px apart,
// with slop that overlapped.

internal val MinTarget = 48.dp

/** RN's outline on the runner's bigger controls, heavier than a hairline. */
private val HeavyBorder = 1.5.dp

/**
 * A button's height, corners and outline at RN's three sizes, and the [fill] a [PrimaryButton] takes;
 * an [OutlineButton] has none.
 */
internal data class ButtonLook(
    val minHeight: Dp,
    val shape: Shape,
    val border: Dp,
    val fill: Color = RunnerColors.accent,
    val pad: Dp = Spacing.two,
) {
    companion object {
        /** The tall buttons along the bottom, where a hand lands without looking. */
        val Big = ButtonLook(64.dp, RoundedCornerShape(20.dp), HeavyBorder)

        /** The rest row's: a step shorter and less rounded, three buttons sharing it. */
        val Rest = ButtonLook(60.dp, RoundedCornerShape(18.dp), HeavyBorder)

        /** Rest's Skip: light rather than accent, a rest being the calm screen. */
        val RestSkip = Rest.copy(fill = RunnerColors.text)

        /** A sheet's actions, and the one-choice buttons that sit centred. */
        val Compact = ButtonLook(52.dp, RoundedCornerShape(15.dp), 1.dp)
    }
}

private val CompactMinWidth = 160.dp

/** RN's caps labels are tracked out; most at 1.4, the quieter controls (Finish, SWAP) at 1. */
internal val CapsTracking = 1.4.sp
private val QuietTracking = 1.sp

/** A quiet control's words: the code face in the secondary colour. */
internal val QuietStyle = KettleType.code.copy(color = RunnerColors.textSecondary, letterSpacing = QuietTracking)

/**
 * The one filled action on a screen: what ends the step. Only as wide as its caller makes it. Disabled
 * is dimmed, as RN's, and only the pad's Set is ever disabled.
 */
@Composable
internal fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    look: ButtonLook = ButtonLook.Big,
) {
    Box(
        modifier
            .heightIn(min = look.minHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(look.shape)
            .background(look.fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = look.pad, vertical = Spacing.one),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = KettleType.heading, color = RunnerColors.background, textAlign = TextAlign.Center)
    }
}

/** [PrimaryButton] at RN's compact size, sized to its words above a floor: Done, a notice's action. */
@Composable
internal fun CompactButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryButton(
        label,
        onClick,
        modifier.widthIn(min = CompactMinWidth),
        // RN's centred one-choice buttons pad wider than its sheet actions, for a longer translation.
        look = ButtonLook.Compact.copy(pad = Spacing.three),
    )
}

/** An outlined button: Pause, +30s, the pad's Cancel. [style] carries its colour. */
@Composable
internal fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = KettleType.heading.copy(color = RunnerColors.text),
    look: ButtonLook = ButtonLook.Big,
) {
    Box(
        modifier
            .heightIn(min = look.minHeight)
            .clip(look.shape)
            .border(look.border, RunnerColors.border, look.shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = OUTLINE_PAD, vertical = Spacing.one),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style, textAlign = TextAlign.Center)
    }
}

/**
 * A text control with no frame: Finish, ADD, SWAP, Cancel, a countdown's Skip. Named by its own words
 * unless [description] says more than they do, as last time's adopt does: it saves the load too.
 */
@Composable
internal fun TextControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = QuietStyle,
    description: String? = null,
) {
    Box(
        modifier
            .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = Spacing.two),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style)
    }
}

/**
 * How a round control is drawn: its circle's [size], which can be smaller than the 48dp it takes to
 * touch (#263: RN's set row drew 26px with overlapping slop), its glyph, and whether it's the accented
 * one of a pair, as a stepper's + is.
 */
internal data class GlyphLook(
    val size: Dp,
    val glyph: TextStyle = KettleType.heading.copy(color = RunnerColors.textSecondary),
    val border: Dp = 1.dp,
    val accent: Boolean = false,
    val fill: Color = Color.Transparent,
) {
    companion object {
        /** The set row's −/+, 26 drawn. */
        val Small = GlyphLook(26.dp, KettleType.smallMedium.copy(color = RunnerColors.textSecondary))

        /** The load's −/+. */
        val Load = GlyphLook(30.dp)

        /** Coming up's ×: the app's sheet close, in runner colours. */
        val Close =
            GlyphLook(32.dp, QuietStyle.copy(fontSize = KettleType.heading.fontSize), fill = RunnerColors.background)

        /** An interval's steppers; [accent] for the +. */
        val Stepper =
            GlyphLook(44.dp, KettleType.title.copy(fontSize = 22.sp, color = RunnerColors.textSecondary), HeavyBorder)

        /** Reps' big −/+. */
        val Big =
            GlyphLook(60.dp, KettleType.title.copy(fontSize = 28.sp, color = RunnerColors.textSecondary), HeavyBorder)

        /** Prev, beside Pause: its triangle is drawn, so it has no glyph style of its own. */
        val Prev = GlyphLook(56.dp, border = HeavyBorder)
    }
}

/**
 * A glyph-only control, which has nothing on screen to take a name from, so [description] is required.
 * Disabled is drawn dimmed and announced so rather than removed, so its row doesn't reflow mid-set.
 * The touch target is at least [MinTarget] whatever [look] draws.
 */
@Composable
internal fun GlyphButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    look: GlyphLook = GlyphLook.Small,
) {
    RoundButton(description, onClick, enabled, look) {
        Text(glyph, style = if (look.accent) look.glyph.copy(color = RunnerColors.accent) else look.glyph)
    }
}

@Composable
private fun RoundButton(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    look: GlyphLook,
    content: @Composable () -> Unit,
) {
    Box(
        // A minimum, not a fixed size, so the glyph has room at large text sizes (AGENTS.md).
        Modifier
            .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val accentBorder = if (look.accent) RunnerColors.accent.copy(alpha = SOFT_BORDER_ALPHA) else RunnerColors.border
        Box(
            Modifier
                .sizeIn(minWidth = look.size, minHeight = look.size)
                .clip(CircleShape)
                .background(if (look.accent) RunnerColors.accentSoft else look.fill)
                .border(look.border, accentBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** A small outlined pill with words, SWAP's: named by them, and 48dp to touch whatever it draws. */
@Composable
internal fun PillControl(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = GlyphLook.Small.size)
                .clip(CircleShape)
                .border(1.dp, RunnerColors.border, CircleShape)
                .padding(horizontal = PILL_PAD),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = QuietStyle)
        }
    }
}

/**
 * Back a step, on the screens that keep the clock: an icon, so it carries a label. The triangle is
 * drawn, as RN's CSS one, rather than a text glyph that changes size and weight with the font.
 */
@Composable
internal fun PrevIconButton(onClick: () -> Unit) {
    RoundButton(stringResource(R.string.session_previousStep), onClick, enabled = true, look = GlyphLook.Prev) {
        Canvas(Modifier.size(width = 9.dp, height = 12.dp)) {
            // Back points the way the layout reads.
            scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
                drawPath(
                    Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(0f, size.height / 2)
                        lineTo(size.width, size.height)
                        close()
                    },
                    RunnerColors.textSecondary,
                )
            }
        }
    }
}

/** Prev as a word, in a circle, on the screens with no clock to pause: named by its text. */
@Composable
internal fun PrevTextButton(
    onClick: () -> Unit,
    size: Dp = GlyphLook.Prev.size,
) {
    Box(
        // A minimum, as GlyphButton's, so the word has room at large text sizes.
        Modifier
            .sizeIn(minWidth = size, minHeight = size)
            .clip(CircleShape)
            .border(HeavyBorder, RunnerColors.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.session_prev), style = PrevStyle, textAlign = TextAlign.Center)
    }
}

private const val DISABLED_ALPHA = 0.4f
private val PILL_PAD = 10.dp
private val OUTLINE_PAD = 12.dp

/** Prev's word is RN's plain code face, untracked, unlike the other quiet controls. */
private val PrevStyle = KettleType.code.copy(color = RunnerColors.textSecondary)
