package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.Spacing

// The runner's controls. Always dark ([RunnerColors], whatever the scheme), and every one at least
// [MinTarget] tall and its own target, which is #263's fix: the set row's −/+ were 26px, 4px apart,
// with slop that overlapped.

internal val MinTarget = 48.dp

/** The tall buttons along the bottom, where a hand lands without looking. */
private val BigTarget = 56.dp

private val ButtonShape = RoundedCornerShape(12.dp)

/** A quiet control's words: the code face in the secondary colour. */
internal val QuietStyle = KettleType.code.copy(color = RunnerColors.textSecondary)

/** The one filled action on a screen: what ends the step. */
@Composable
internal fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = BigTarget)
            .clip(ButtonShape)
            .background(if (enabled) RunnerColors.accent else RunnerColors.backgroundElement)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.three),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = KettleType.heading,
            color = if (enabled) RunnerColors.background else RunnerColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** An outlined button: Pause, +30s, Prev. [style] carries its colour. */
@Composable
internal fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = KettleType.heading.copy(color = RunnerColors.text),
) {
    Box(
        modifier
            .heightIn(min = BigTarget)
            .clip(ButtonShape)
            .border(1.dp, RunnerColors.border, ButtonShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.three),
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
 * A glyph-only control, which has nothing on screen to take a name from, so [description] is required.
 * Disabled is drawn dimmed and announced so rather than removed, so its row doesn't reflow mid-set.
 */
@Composable
internal fun GlyphButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = MinTarget,
) {
    Box(
        // A minimum, not a fixed size, so the glyph has room at large text sizes (AGENTS.md).
        Modifier
            .sizeIn(minWidth = size, minHeight = size)
            .clip(CircleShape)
            .border(1.dp, RunnerColors.border, CircleShape)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = KettleType.heading, color = RunnerColors.text)
    }
}

/** Back a step, on the screens that keep the clock: an icon, so it carries a label. */
@Composable
internal fun PrevIconButton(onClick: () -> Unit) {
    GlyphButton("◀", stringResource(R.string.session_previousStep), onClick, size = BigTarget)
}

/** A kicker in the code face; [label] is in caps already, as the bundles spell it. */
@Composable
internal fun Caps(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(label, modifier, style = QuietStyle)
}

/** Reserves [label]'s width without drawing or announcing it, so two tracks line up in a column. */
@Composable
internal fun WidthOf(label: String) {
    Text(label, Modifier.alpha(0f).clearAndSetSemantics {}, style = QuietStyle)
}

private const val DISABLED_ALPHA = 0.35f
