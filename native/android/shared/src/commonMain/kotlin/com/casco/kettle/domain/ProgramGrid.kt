package com.casco.kettle.domain

/**
 * Program detail's weeks (#437): every week of a program, each slot placed against where the program
 * stands, by the rule Today's strip follows (#412): position, not completion.
 */
data class GridWeek(
    val week: Long,
    val slots: List<GridSlot>,
)

/** One slot: its place in the rotation, and the program's own entry for it. */
data class GridSlot(
    val index: Int,
    val slot: ProgramWeek,
    val place: SlotPlace,
) {
    val rest: Boolean get() = slot is ProgramWeek.RestDay

    /** A day whose workout the program changes for it, which the grid marks with a dot. */
    val changed: Boolean get() = (slot as? ProgramWeek.Training)?.overrides?.isNotEmpty() == true
}

/**
 * Where [program] stands for someone opening it while Today runs another: the slot after the one its
 * newest session ran. For the program Today runs, Program detail takes Today's own position instead,
 * which also moves past rest days already served. Null for a program with no weeks.
 */
fun programPosition(
    program: Program,
    sessions: List<Session>,
): ProgramPosition? {
    if (program.weeks.isEmpty()) return null
    return ProgramPosition(program, nextIndexAfter(rotation(program), program, sessions))
}

/** Every week of [position]'s program in rotation order, each slot Behind, Current or Ahead of it. */
fun programGrid(position: ProgramPosition): List<GridWeek> =
    rotation(position.program)
        .withIndex()
        .groupBy { it.value.week }
        .map { (week, slots) ->
            GridWeek(
                week,
                slots.map { (index, slot) ->
                    val place =
                        when {
                            index < position.index -> SlotPlace.Behind
                            index == position.index -> SlotPlace.Current
                            else -> SlotPlace.Ahead
                        }
                    GridSlot(index, slot, place)
                },
            )
        }
