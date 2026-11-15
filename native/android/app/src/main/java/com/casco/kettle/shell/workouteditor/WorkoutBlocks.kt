package com.casco.kettle.shell.workouteditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.TypeIcon

/** The library's exercises, to draw each block's by its id, and the reader's units to show them in. */
internal class BlockContext(
    val exercises: List<Exercise>,
    val units: UnitSystem,
) {
    fun exercise(id: String) = exercises.firstOrNull { it.id == id }
}

/**
 * Where a block stands, for its grip: [index] of [total], and the moves a screen reader offers.
 * [handle] is the drag, from the reorderable item the block is drawn in.
 */
internal class BlockPlace(
    val index: Int,
    val total: Int,
    val handle: Modifier,
    val moveBy: (Int) -> Unit,
)

/**
 * The ⣿ a block is dragged by, and its screen reader's way to move it, since TalkBack can't drag.
 * "Move up" and "Move down" are custom actions, each left off at its end of the list rather than
 * offered and refused; "Position n of m" is its state, which TalkBack reads on focus and again when a
 * move changes it, so the move is announced. The block's key is stable, so focus stays on the block
 * that moved, and a second "Move up" moves it again rather than its new neighbour, as RN's slot keys
 * did (`docs/research/compose-reorder.md`).
 *
 * No role: Compose has none for a handle, and Button would tell TalkBack a double-tap does something
 * here. The custom actions are what TalkBack announces it by. 48dp, not RN's 44, as the research
 * planned, since the grip is the row's one reorder control.
 */
@Composable
internal fun Grip(
    name: String,
    place: BlockPlace,
) {
    val label = stringResource(R.string.workoutEditor_reorderAccessibility, name)
    val position = stringResource(R.string.workoutEditor_positionOf, place.index + 1, place.total)
    val moveUp = stringResource(R.string.workoutEditor_moveUp)
    val moveDown = stringResource(R.string.workoutEditor_moveDown)
    Box(
        place.handle
            .sizeIn(minWidth = GripSize, minHeight = GripSize)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = position
                customActions =
                    listOfNotNull(
                        CustomAccessibilityAction(moveUp) { true.also { place.moveBy(-1) } }.takeIf { place.index > 0 },
                        CustomAccessibilityAction(moveDown) { true.also { place.moveBy(1) } }
                            .takeIf { place.index < place.total - 1 },
                    )
            },
        contentAlignment = Alignment.Center,
    ) { TypeIcon(R.drawable.icon_drag, KettleType.heading, KettleTheme.colors.textSecondary) }
}

private val GripSize = 48.dp
