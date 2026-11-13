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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 *
 * Back is the engine's Prev, so on a work screen reached straight from a logged step (a set with no rest
 * after it, the next member of a circuit) it takes that step's log back as it returns to it. It has to:
 * going back is going back to redo the step, and logging it again over its old log would record it twice.
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
            style = SlotLabel,
            color = runnerColors.text,
            textAlign = TextAlign.Center,
        )
    }
}

/** The Done side slot: the way out of a step only a tap ends, or one ended early. */
@Composable
internal fun doneAction(context: StepContext) =
    RowAction(stringResource(R.string.session_done), R.drawable.icon_stop, context::advance)

/** The Skip side slot: leaving early a step whose clock would end it. */
@Composable
internal fun skipAction(context: StepContext) =
    RowAction(stringResource(R.string.session_skip), R.drawable.icon_skip, context::advance)

/** Pause, or Resume while paused: the main action on a screen the clock runs, and on a paused rest. */
@Composable
internal fun pauseAction(context: StepContext): RowAction {
    val toggle = { context.send(RunnerCommand.TogglePause) }
    return if (context.paused) {
        RowAction(stringResource(R.string.session_resume), R.drawable.icon_play, toggle)
    } else {
        RowAction(stringResource(R.string.session_pause), R.drawable.icon_pause, toggle)
    }
}

/**
 * The runner's small outlined action, Undo's, Try's, End's and Remove's: a 44dp pill centred in its
 * 48dp target, as the canvas draws them, and never filled, so none can be taken for the main action.
 * Named by its own words.
 */
@Composable
internal fun OutlinedPill(
    label: String,
    onClick: () -> Unit,
    minWidth: Dp = 0.dp,
) = Pill(label, onClick, minWidth, filled = false)

/** The pill filled, for the one place it is a sheet's own main action: the Sets sheet's Done. */
@Composable
internal fun FilledPill(
    label: String,
    onClick: () -> Unit,
) = Pill(label, onClick, 0.dp, filled = true)

/**
 * The pill both draw. The words are centred by the box: a Text stretched to a minimum height draws its
 * line at the top of it.
 */
@Composable
private fun Pill(
    label: String,
    onClick: () -> Unit,
    minWidth: Dp,
    filled: Boolean,
) {
    Box(
        Modifier
            .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = PILL_HEIGHT)
                .widthIn(min = minWidth)
                .clip(CircleShape)
                .then(
                    if (filled) {
                        Modifier.background(runnerColors.accent)
                    } else {
                        Modifier.border(SLOT_BORDER, runnerColors.outline, CircleShape)
                    },
                ).padding(horizontal = Spacing.three),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = SlotLabel, color = if (filled) runnerColors.onAccent else runnerColors.text)
        }
    }
}

/** The canvas's 72dp row: taller than the 48dp minimum, being the controls a hand lands on unlooking. */
private val ROW_HEIGHT = 72.dp
private val SLOT_WIDTH = 64.dp

/** The bottom area's and the End pill's outline: one weight, so the controls read as one set. */
internal val SLOT_BORDER = 1.5.dp

/** The side slots' words and Undo's: one style, so the controls in the bottom area can't drift apart. */
internal val SlotLabel = KettleType.smallMedium.copy(fontWeight = FontWeight.SemiBold)

/** The canvas's pill, inside its 48dp target. */
private val PILL_HEIGHT = 44.dp
private val RowShape = RoundedCornerShape(20.dp)
