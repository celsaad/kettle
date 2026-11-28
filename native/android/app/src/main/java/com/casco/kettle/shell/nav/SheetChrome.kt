package com.casco.kettle.shell.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing

/** The row the grabber and × centre on, as RN's `ModalHeader` measured it: its button set its height. */
private val ROW = 32.dp

/**
 * The sheet's strip: the row's top padding, the row and its bottom padding. The chrome draws over it,
 * and a sheet's content starts below it (#197).
 */
internal val SheetStripHeight: Dp = Spacing.three + ROW + (Spacing.three - 2.dp)

/**
 * Where the grabber's drag goes: [onDrag] follows it down the screen in px, [onStopped] gets the fling
 * velocity in px/s, and [onStarted] hears a grab, which [immediately] takes without touch slop (a finger
 * landing on a sheet still settling).
 */
internal class GrabberDrag(
    val onDrag: (dy: Float) -> Unit,
    val onStarted: () -> Unit,
    val onStopped: (velocity: Float) -> Unit,
    val immediately: Boolean,
)

/**
 * The sheet's grabber and ×, drawn over the top of the sheet's strip (#155, #179): a row [ROW] tall,
 * [Spacing.three] down, the × at the end of the [MaxContentWidth] column.
 *
 * Neither dismisses anything itself. [onBack] is back, which the navigator takes; [drag] is where the
 * grabber's drag goes.
 *
 * TalkBack reads the × first, then the grabber, then the sheet's content: the two carry a
 * [traversalIndex] below the content's, inside the sheet's traversal group.
 */
@Composable
internal fun SheetChrome(
    onBack: () -> Unit,
    drag: GrabberDrag,
    modifier: Modifier = Modifier,
) {
    // The row's centre, where RN's grabber and button centred, and each 48dp target centred on it.
    val targetTop = Spacing.three + ROW / 2 - TARGET / 2
    Box(modifier.fillMaxWidth()) {
        Grabber(onBack, drag, Modifier.align(Alignment.TopCenter).padding(top = targetTop))
        // The column the content lines up on, so the ×'s circle ends where RN's did, its target past it.
        Box(Modifier.align(Alignment.TopCenter).widthIn(max = MaxContentWidth).fillMaxWidth()) {
            Close(onBack, Modifier.align(Alignment.TopEnd).padding(top = targetTop, end = Spacing.three - OVERHANG))
        }
    }
}

/**
 * The ×, a button that goes back. Its circle is RN's header's pairing (backgroundElement, border,
 * textSecondary), so its contrast holds; the glyph is one em of 20sp, growing with the text size as
 * the glyph it replaced did, up to the target it sits in.
 */
@Composable
private fun Close(
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val colors = KettleTheme.colors
    val label = stringResource(R.string.common_close)
    val icon = with(LocalDensity.current) { CLOSE_ICON.toDp() }.coerceAtMost(TARGET)
    Box(
        modifier
            .sizeIn(minWidth = TARGET, minHeight = TARGET)
            .semantics { traversalIndex = CLOSE_ORDER }
            .clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(CIRCLE)
                .background(colors.backgroundElement, CircleShape)
                .border(1.dp, colors.border, CircleShape),
        )
        Icon(
            painterResource(R.drawable.icon_close),
            contentDescription = null,
            modifier = Modifier.size(icon).testTag(CLOSE_ICON_TAG),
            tint = colors.textSecondary,
        )
    }
}

/**
 * The grabber, the only place a drag starts that can close the sheet: one from the content would drop an
 * edit, as a scrim tap would, and the editors have no confirm. A finger's tap does nothing for the same
 * reason. TalkBack's click, dismiss and collapse each close it, as Material's handle offers them: the
 * sheet has one height, always fully open, so collapsing it is closing it. Wider than the bar, so the
 * drag is easy to start.
 */
@Composable
private fun Grabber(
    onBack: () -> Unit,
    drag: GrabberDrag,
    modifier: Modifier,
) {
    val label = stringResource(R.string.common_dragHandle)
    val close = {
        onBack()
        true
    }
    Box(
        modifier
            .sizeIn(minWidth = GRABBER_WIDTH, minHeight = TARGET)
            .semantics {
                traversalIndex = GRABBER_ORDER
                contentDescription = label
                role = Role.Button
                onClick(action = close)
                dismiss(action = close)
                collapse(action = close)
            }.draggable(
                rememberDraggableState(drag.onDrag),
                Orientation.Vertical,
                startDragImmediately = drag.immediately,
                onDragStarted = { drag.onStarted() },
                onDragStopped = { drag.onStopped(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(
                    BAR_WIDTH,
                    BAR_HEIGHT,
                ).background(KettleTheme.colors.border, RoundedCornerShape(BAR_HEIGHT / 2)),
        )
    }
}

/** #155: both controls are 48dp targets, past Kettle's 44 minimum. */
private val TARGET = 48.dp
private val CIRCLE = 32.dp

/** How far the ×'s 48dp target reaches past the 32dp circle it holds, on each side. */
private val OVERHANG = (TARGET - CIRCLE) / 2
private val BAR_WIDTH = 40.dp
private val BAR_HEIGHT = 5.dp

/** Wider than the bar, so the drag is easy to start; the same height as the × target. */
private val GRABBER_WIDTH = 120.dp
private val CLOSE_ICON = 20.sp

/** Before the content's own index, 0, in the sheet's traversal group: the × first, then the grabber. */
private const val CLOSE_ORDER = -2f
private const val GRABBER_ORDER = -1f

/** The ×'s glyph, for a test to measure. */
internal const val CLOSE_ICON_TAG = "sheet-close-icon"
