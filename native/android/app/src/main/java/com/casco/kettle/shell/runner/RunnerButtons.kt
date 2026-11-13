package com.casco.kettle.shell.runner

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

// The runner's controls, in the field's colours ([runnerColors]), and every one at least
// [MinTarget] tall and its own target, which is #263's fix: the set row's −/+ were 26px, 4px apart,
// with slop that overlapped.

/** An action in the bottom row: its words, a symbol beside or above them, and what a tap does. */
internal class RowAction(
    val label: String,
    @DrawableRes val icon: Int?,
    val onClick: () -> Unit,
)

internal val MinTarget = 48.dp

/** RN's outline on the runner's bigger controls, heavier than a hairline. */
private val HeavyBorder = 1.5.dp

/**
 * A button's height, corners and outline at RN's three sizes. A [PrimaryButton] is filled with the
 * field's accent, its ink, so it is the field's inverse on every one (#418); an [OutlineButton] isn't.
 */
internal data class ButtonLook(
    val minHeight: Dp,
    val shape: Shape,
    val border: Dp,
    val pad: Dp = Spacing.two,
) {
    companion object {
        /** The tall buttons along the bottom, where a hand lands without looking. */
        val Big = ButtonLook(64.dp, RoundedCornerShape(20.dp), HeavyBorder)

        /** A sheet's actions, and the one-choice buttons that sit centred. */
        val Compact = ButtonLook(52.dp, RoundedCornerShape(15.dp), 1.dp)
    }
}

private val CompactMinWidth = 160.dp

/** RN's caps labels are tracked out; most at 1.4, the quieter controls (Finish, SWAP) at 1. */
internal val CapsTracking = 1.4.sp
private val QuietTracking = 1.sp

/** A quiet control's words: the code face in the field's secondary colour. */
internal val QuietStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = KettleType.code.copy(color = runnerColors.textSecondary, letterSpacing = QuietTracking)

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
            .background(runnerColors.accent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = look.pad, vertical = Spacing.one),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = KettleType.heading, color = runnerColors.onAccent, textAlign = TextAlign.Center)
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
    style: TextStyle = KettleType.heading.copy(color = runnerColors.text),
    look: ButtonLook = ButtonLook.Big,
) {
    Box(
        modifier
            .heightIn(min = look.minHeight)
            .clip(look.shape)
            .border(look.border, runnerColors.outline, look.shape)
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
 * touch (#263: RN's set row drew 26px with overlapping slop), the type its symbol is sized by, whether
 * it's the accented one of a pair, as a stepper's + is, whether it is [filled] with the field, and where
 * in its target it sits. The symbol is the field's secondary colour, or its accent: read as it's drawn,
 * since the field changes under a look that doesn't.
 */
internal data class SymbolLook(
    val size: Dp,
    val symbolType: TextStyle = KettleType.heading,
    val border: Dp = 1.dp,
    val accent: Boolean = false,
    val filled: Boolean = false,
    val placement: CirclePlacement = CirclePlacement.CENTRE,
    val shape: Shape = CircleShape,
) {
    companion object {
        /** The set row's −/+, 26 drawn. */
        val Small = SymbolLook(26.dp, KettleType.smallMedium)

        /** The load's −/+, the canvas's rounded squares beside the 40sp load (#410). */
        val Load =
            SymbolLook(56.dp, KettleType.title.copy(fontSize = 22.sp), HeavyBorder, shape = RoundedCornerShape(16.dp))

        /** Coming up's ×: the app's sheet close, in runner colours. */
        val Close = SymbolLook(32.dp, KettleType.code.copy(fontSize = KettleType.heading.fontSize), filled = true)

        /** An interval's steppers; [accent] for the +. */
        val Stepper = SymbolLook(44.dp, KettleType.title.copy(fontSize = 22.sp), HeavyBorder)

        /** Reps' big −/+. */
        val Big = SymbolLook(60.dp, KettleType.title.copy(fontSize = 28.sp), HeavyBorder)
    }
}

/**
 * Where a round control's circle sits in its target: centred, or against one edge with [PAIR_INSET]
 * kept on that side, for two buttons that should read as one control. The set row's −/+ have nothing
 * between them, so centred circles leave as much space inside the pair as beside it (#372); pressing
 * each against the other moves that space outward without shrinking or overlapping a target (#263).
 */
internal enum class CirclePlacement(
    val alignment: Alignment,
    val startInset: Dp,
    val endInset: Dp,
) {
    CENTRE(Alignment.Center, 0.dp, 0.dp),
    START(Alignment.CenterStart, PAIR_INSET, 0.dp),
    END(Alignment.CenterEnd, 0.dp, PAIR_INSET),
}

/**
 * A symbol-only control, which has nothing on screen to take a name from, so [description] is required.
 * Disabled is drawn dimmed and announced so rather than removed, so its row doesn't reflow mid-set.
 * The touch target is at least [MinTarget] whatever [look] draws.
 */
@Composable
internal fun SymbolButton(
    @DrawableRes symbol: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    look: SymbolLook = SymbolLook.Small,
) {
    RoundButton(description, onClick, enabled, look) {
        TypeIcon(symbol, look.symbolType, if (look.accent) runnerColors.accent else runnerColors.textSecondary)
    }
}

@Composable
private fun RoundButton(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    look: SymbolLook,
    content: @Composable () -> Unit,
) {
    Box(
        // A minimum, not a fixed size, so the symbol has room at large text sizes (AGENTS.md).
        Modifier
            .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = look.placement.alignment,
    ) {
        val accentBorder =
            if (look.accent) {
                runnerColors.accent.copy(
                    alpha = SOFT_BORDER_ALPHA,
                )
            } else {
                runnerColors.outline
            }
        val fill = if (look.filled) runnerColors.background else Color.Transparent
        Box(
            Modifier
                // The inset is inside the target, so a circle grown by large text takes the far side's
                // space first and the target only widens once that's gone.
                .padding(start = look.placement.startInset, end = look.placement.endInset)
                .sizeIn(minWidth = look.size, minHeight = look.size)
                .clip(look.shape)
                .background(if (look.accent) runnerColors.accentSoft else fill)
                .border(look.border, accentBorder, look.shape),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

private val PAIR_INSET = 4.dp
private const val DISABLED_ALPHA = 0.4f
private val OUTLINE_PAD = 12.dp
