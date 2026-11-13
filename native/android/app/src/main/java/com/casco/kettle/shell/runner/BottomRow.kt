package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * The runner's one bottom row (#409): Back on the left, the main action in the middle, and the step's
 * way out (Skip or Done) on the right. On a screen the clock runs, the main action is Pause, the safe
 * thing to hit from the floor, and the way out moves to the side. [back] null leaves its slot out,
 * which only the rest screen does, and only while Undo is there to step back with instead.
 */
@Composable
internal fun BottomRow(
    main: RowAction,
    back: (() -> Unit)?,
    side: RowAction? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) SlotButton(RowAction(stringResource(R.string.session_prev), R.drawable.icon_back, back))
        MainButton(main, Modifier.weight(1f))
        if (side != null) SlotButton(side)
    }
}

/** The filled one, the field's inverse: its ink with the field's colour on it, never orange. */
@Composable
private fun MainButton(
    action: RowAction,
    modifier: Modifier,
) {
    Row(
        modifier
            .heightIn(min = ROW_HEIGHT)
            .clip(RowShape)
            .background(runnerColors.accent)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = Spacing.two, vertical = Spacing.one),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        action.icon?.let { TypeIcon(it, KettleType.heading, runnerColors.onAccent) }
        Text(action.label, style = KettleType.heading, color = runnerColors.onAccent, textAlign = TextAlign.Center)
    }
}

/**
 * A side slot: a symbol over its word, 64dp wide unless a translation needs more, and named by that
 * word, so Voice Control matches what's on screen.
 */
@Composable
private fun SlotButton(action: RowAction) {
    Column(
        Modifier
            .widthIn(min = SLOT_WIDTH)
            .heightIn(min = ROW_HEIGHT)
            .clip(RowShape)
            .border(SLOT_BORDER, runnerColors.outline, RowShape)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = Spacing.one, vertical = Spacing.two),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.one, Alignment.CenterVertically),
    ) {
        action.icon?.let { TypeIcon(it, KettleType.heading, runnerColors.text) }
        Text(
            action.label,
            style = KettleType.smallMedium.copy(fontWeight = FontWeight.SemiBold),
            color = runnerColors.text,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "Logged set 1 · 10 × 48.5 lb" with Undo beside it: a tinted panel, shown only straight after a commit,
 * so Undo is never beside Back for the same step. Undo is the engine's Prev, which takes back exactly
 * what this names and moves to the step it came from.
 */
@Composable
internal fun LoggedPanel(
    logged: LoggedUnit,
    context: StepContext,
) {
    val resources = LocalResources.current
    Row(
        Modifier
            .clip(PanelShape)
            .background(runnerColors.backgroundElement)
            // The canvas's 6dp around the pill, which the target's own 2dp of slack makes up on its right.
            .padding(start = Spacing.three, top = Spacing.half, end = Spacing.half, bottom = Spacing.half),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.one),
        ) {
            Text(
                stringResource(R.string.session_logged_title),
                style = KettleType.default,
                color = runnerColors.textSecondary,
            )
            Text(resources.loggedText(logged, context.units), style = KettleType.default, color = runnerColors.text)
        }
        // A 44dp pill centred in its 48dp target, as the canvas draws it. The words are centred by the
        // box: a Text stretched to a minimum height draws its line at the top of it.
        Box(
            Modifier
                .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
                .clickable(role = Role.Button) { context.send(RunnerCommand.Prev) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .heightIn(min = UNDO_HEIGHT)
                    .clip(CircleShape)
                    .border(SLOT_BORDER, runnerColors.outline, CircleShape)
                    .padding(horizontal = Spacing.three),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.session_undo),
                    style = KettleType.smallMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = runnerColors.text,
                )
            }
        }
    }
}

/** The canvas's 72dp row: taller than the 48dp minimum, being the controls a hand lands on unlooking. */
private val ROW_HEIGHT = 72.dp
private val SLOT_WIDTH = 64.dp
private val SLOT_BORDER = 1.5.dp

/** The canvas's Undo pill, inside its 48dp target. */
private val UNDO_HEIGHT = 44.dp
private val RowShape = RoundedCornerShape(20.dp)
private val PanelShape = RoundedCornerShape(18.dp)
