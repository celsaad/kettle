package com.casco.kettle.shell.workouteditor

import com.casco.kettle.domain.WorkoutBlock

// What the editor's rows can do (#440), as parameters: the rows draw, and the editor decides.

/** What a plain block's row can do: go, or, a rest, take a new length against its exercise's [own]. */
internal class BlockActions(
    val remove: () -> Unit,
    val restLength: (seconds: Double, own: Double) -> Unit,
)

/** What a circuit can do, and whether its picker is the one open. */
internal class CircuitActions(
    val change: (WorkoutBlock.Circuit) -> Unit,
    val remove: () -> Unit,
    val togglePicker: () -> Unit,
    val picking: Boolean,
)
