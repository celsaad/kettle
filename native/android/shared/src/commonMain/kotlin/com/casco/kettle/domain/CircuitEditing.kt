package com.casco.kettle.domain

// What the workout editor (#242) does to one circuit, and to the picker's selection that builds one:
// RN's `workout-circuit-block.tsx` and `workout-editor.tsx`, inline there.

/** The circuit picker's selection with [exerciseId] added, or taken out if it was already in. */
fun toggledMember(
    selection: List<String>,
    exerciseId: String,
): List<String> = if (exerciseId in selection) selection - exerciseId else selection + exerciseId

/**
 * [rounds] held between 1 and the schema's [MAX_ROUNDS]. The stepper is the only way to set them and
 * Save checks only the name, so this is the one thing between a tap and a library that won't parse.
 */
fun WorkoutBlock.Circuit.withRounds(rounds: Long): WorkoutBlock.Circuit =
    copy(rounds = rounds.coerceIn(1L, MAX_ROUNDS.toLong()))

/** [id] trimmed, or none for a blank one. */
fun WorkoutBlock.Circuit.withId(id: String): WorkoutBlock.Circuit = copy(id = id.trim().ifEmpty { null })

/** Whether a member can go: not from two, since the schema needs two and one isn't a circuit. */
val WorkoutBlock.Circuit.canRemoveMember: Boolean get() = members.size > 2

/**
 * Without the member at [index]. Refused at two members: the control is marked unavailable there, and
 * this is what holds if a caller forgets, not the user's answer. Deleting the block is.
 */
fun WorkoutBlock.Circuit.withoutMember(index: Int): WorkoutBlock.Circuit =
    if (canRemoveMember) copy(members = members.filterIndexed { at, _ -> at != index }) else this

/**
 * A typed rest, as seconds: what RN's `Math.max(0, Number(text) || 0)` made of it. Junk and blank read
 * as 0, and so does a negative, which the schema refuses.
 */
fun restSeconds(text: String): Double = (jsNumberOrNull(text.trim()) ?: 0.0).coerceAtLeast(0.0)
