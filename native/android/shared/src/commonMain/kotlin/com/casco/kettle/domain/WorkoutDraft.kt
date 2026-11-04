package com.casco.kettle.domain

// The workout editor's draft (#242): its blocks, keyed, and the moves on them, which RN kept inline in
// `workout-editor.tsx`. The circuits' own rules are `CircuitEditing.kt`'s; Save is `WorkoutEditing.kt`'s.

/**
 * One block of the draft, with a [key] that stays with it through every move. A `WorkoutBlock` has no
 * id and the same exercise can appear twice, so RN keyed rows by position, which left a screen
 * reader's focus on the slot rather than the block that moved (`docs/research/compose-reorder.md`).
 */
data class DraftBlock(
    val key: Int,
    val block: WorkoutBlock,
)

/** The workout being edited, as the editor's own copy: nothing reaches the library until Save. */
data class WorkoutDraft(
    val name: String = "",
    val blocks: List<DraftBlock> = emptyList(),
    /** The key the next added block gets, so no two blocks of one draft ever share one. */
    val nextKey: Int = blocks.size,
)

/** [workout]'s draft, or an empty one for a new workout. */
fun workoutDraftOf(workout: Workout?): WorkoutDraft =
    WorkoutDraft(workout?.name.orEmpty(), workout?.blocks.orEmpty().mapIndexed(::DraftBlock))

/** [block] at the end, under a key no block of this draft has had. */
fun WorkoutDraft.adding(block: WorkoutBlock): WorkoutDraft =
    copy(blocks = blocks + DraftBlock(nextKey, block), nextKey = nextKey + 1)

/** Exercise [exerciseId] as a plain block at the end, as the block picker adds it. */
fun WorkoutDraft.adding(exerciseId: String): WorkoutDraft = adding(WorkoutBlock.ExerciseBlock(exerciseId))

/** Without the block keyed [key]. */
fun WorkoutDraft.removing(key: Int): WorkoutDraft = copy(blocks = blocks.filterNot { it.key == key })

/**
 * The block at index [from] moved to index [to], the others closing up behind it: what a drag reports,
 * once per slot it crosses, while the finger is still down.
 */
fun WorkoutDraft.moving(
    from: Int,
    to: Int,
): WorkoutDraft = copy(blocks = blocks.toMutableList().apply { add(to, removeAt(from)) })

/**
 * The block keyed [key] moved [by] places, toward the start for a negative one: a screen reader's
 * "Move up" and "Move down". Clamped rather than wrapped, so the first block moving up stays first.
 */
fun WorkoutDraft.movingBy(
    key: Int,
    by: Int,
): WorkoutDraft {
    val from = blocks.indexOfFirst { it.key == key }
    if (from < 0) return this
    val to = (from + by).coerceIn(0, blocks.lastIndex)
    return if (to == from) this else moving(from, to)
}

/**
 * A circuit of [memberIds] at the end, with RN's defaults for its rounds and rests. Fewer than two
 * leaves the draft as it was: a round-robin of one is a plain block, and the schema refuses it.
 */
fun WorkoutDraft.addingCircuit(memberIds: List<String>): WorkoutDraft =
    if (memberIds.size < 2) {
        this
    } else {
        adding(
            WorkoutBlock.Circuit(
                rounds = DEFAULT_CIRCUIT_ROUNDS,
                restBetweenExercisesSec = DEFAULT_REST_BETWEEN_EXERCISES_SEC,
                restBetweenRoundsSec = DEFAULT_REST_BETWEEN_ROUNDS_SEC,
                members = memberIds.map(::CircuitMember),
            ),
        )
    }

/** The circuit keyed [key], [change]d. Any other block, or a plain one under [key], is left alone. */
fun WorkoutDraft.withCircuit(
    key: Int,
    change: (WorkoutBlock.Circuit) -> WorkoutBlock.Circuit,
): WorkoutDraft =
    copy(
        blocks =
            blocks.map { draft ->
                val block = draft.block
                if (draft.key == key && block is WorkoutBlock.Circuit) draft.copy(block = change(block)) else draft
            },
    )

private const val DEFAULT_CIRCUIT_ROUNDS = 3L
private const val DEFAULT_REST_BETWEEN_EXERCISES_SEC = 15.0
private const val DEFAULT_REST_BETWEEN_ROUNDS_SEC = 60.0
