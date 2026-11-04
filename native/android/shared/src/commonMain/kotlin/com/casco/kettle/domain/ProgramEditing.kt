package com.casco.kettle.domain

// What the program editor (#243) makes of its draft on Save, and what it does to the library: the
// library store's `saveProgram` and `deleteProgram`. The draft and its moves are `ProgramDraft.kt`'s.

/** Why Save refused, in the order RN checked. */
sealed interface ProgramProblem {
    data object NameRequired : ProgramProblem

    data object NoWeeks : ProgramProblem

    data object WeekNeedsWorkout : ProgramProblem

    /** Nothing but rest days, which could never queue anything, as the schema refuses it. */
    data object NoTrainingWeek : ProgramProblem

    /** Two weeks at `week + day`, the address a session records; [day] as Save would write it. */
    data class DuplicateWeek(
        val week: Long,
        val day: String?,
    ) : ProgramProblem

    /** A name with no letter or digit in it, which leaves nothing to make an id from. */
    data object NoId : ProgramProblem
}

sealed interface ProgramSubmit {
    data class Built(
        val program: Program,
    ) : ProgramSubmit

    data class Refused(
        val problem: ProgramProblem,
    ) : ProgramSubmit
}

/**
 * What Save makes of [draft]. [existing] is the program being edited, null for a new one: it keeps its
 * id, since sessions record it and a rename would orphan them. A new program's id is its name's
 * [slugify].
 */
fun submitProgram(
    draft: ProgramDraft,
    existing: Program?,
): ProgramSubmit {
    val weeks = draft.weeks.map { it.week.trimmed() }
    val problem =
        when {
            draft.name.isBlank() -> ProgramProblem.NameRequired
            weeks.isEmpty() -> ProgramProblem.NoWeeks
            weeks.any { it is ProgramWeek.Training && it.workoutId.isEmpty() } -> ProgramProblem.WeekNeedsWorkout
            weeks.none { it is ProgramWeek.Training } -> ProgramProblem.NoTrainingWeek
            else -> duplicate(weeks)
        }
    val id = existing?.id ?: slugify(draft.name)
    return when {
        problem != null -> ProgramSubmit.Refused(problem)
        id.isEmpty() -> ProgramSubmit.Refused(ProgramProblem.NoId)
        else -> ProgramSubmit.Built(Program(id, draft.name.trim(), weeks))
    }
}

private fun duplicate(weeks: List<ProgramWeek>): ProgramProblem.DuplicateWeek? {
    val seen = mutableSetOf<Pair<Long, String?>>()
    return weeks.firstOrNull { !seen.add(it.week to it.day) }?.let { ProgramProblem.DuplicateWeek(it.week, it.day) }
}

/** The week as the file holds it: day and notes trimmed, and absent when blank. */
private fun ProgramWeek.trimmed(): ProgramWeek =
    withShared(day = day?.trim()?.ifEmpty { null }, notes = notes?.trim()?.ifEmpty { null })

/** The library with [program] in it: in place of the one with its id, or else at the end. */
fun Library.withProgram(program: Program): Library {
    val at = programs.indexOfFirst { it.id == program.id }
    val edited = if (at < 0) programs + program else programs.toMutableList().apply { set(at, program) }
    return copy(programs = edited)
}

/** The library without program [id]. Nothing in it points at a program, so nothing is left dangling. */
fun Library.withoutProgram(id: String): Library = copy(programs = programs.filterNot { it.id == id })
