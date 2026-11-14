package com.casco.kettle.domain

// The program editor's week and day moves (#438): the draft stays the program's flat list of entries,
// grouped by week number for the screen. A logged session records its week number, so a move that
// renumbers weeks (duplicate, delete) shifts where an active program stands; agreed on #438.

/** The draft's week numbers, lowest first: the order the program runs them. */
val ProgramDraft.weekNumbers: List<Long> get() = weeks.map { it.week.week }.distinct().sorted()

/** Week [week]'s days, in the draft's order, which is the order they run in. */
fun ProgramDraft.daysOf(week: Long): List<DraftWeek> = weeks.filter { it.week.week == week }

/** Week [week] traded places with the week [by] (−1 or +1) along: the two swap numbers. Nothing at an end. */
fun ProgramDraft.movingWeek(
    week: Long,
    by: Int,
): ProgramDraft {
    val numbers = weekNumbers
    val other = numbers.getOrNull(numbers.indexOf(week) + by) ?: return this
    return renumbered { number ->
        when (number) {
            week -> other
            other -> week
            else -> number
        }
    }
}

/**
 * A copy of week [week] right after it, every later week moved up one to make room, as agreed on #438.
 * The copies get fresh keys and sit after the original's last day, so the file keeps run order.
 */
fun ProgramDraft.duplicatingWeek(week: Long): ProgramDraft {
    val days = daysOf(week)
    if (days.isEmpty()) return this
    val shifted = renumbered { if (it > week) it + 1 else it }
    val copies = days.mapIndexed { i, day -> DraftWeek(nextKey + i, day.week.withShared(week = week + 1)) }
    val after = shifted.weeks.indexOfLast { it.week.week == week } + 1
    return shifted.copy(
        weeks = shifted.weeks.take(after) + copies + shifted.weeks.drop(after),
        nextKey = nextKey + copies.size,
    )
}

/** Week [week] and its days gone, the weeks after it moved down one so the program reads without a gap. */
fun ProgramDraft.deletingWeek(week: Long): ProgramDraft =
    copy(weeks = weeks.filterNot { it.week.week == week }).renumbered { if (it > week) it - 1 else it }

/**
 * A day at the end of week [week], labelled [label] (the screen's first free "Day N", [freeDayLabel], so
 * a new day never takes a label its week already has), running the first of [workouts], as a new week
 * does. The label is written in the editor's language: it's the reader's own default, theirs to change.
 */
fun ProgramDraft.addingDay(
    week: Long,
    label: String,
    workouts: List<Workout>,
): ProgramDraft {
    val day = DraftWeek(nextKey, ProgramWeek.Training(week, label, workouts.firstOrNull()?.id.orEmpty()))
    val after = weeks.indexOfLast { it.week.week == week } + 1
    return copy(weeks = weeks.take(after) + day + weeks.drop(after), nextKey = nextKey + 1)
}

/**
 * The first of [candidate] (1, 2, …) that no day of week [week] already has as its label: "Day 3" after
 * Day 1 was removed from Day 1–3 would be the second Day 3, which Save refuses as one address twice.
 */
fun ProgramDraft.freeDayLabel(
    week: Long,
    candidate: (Int) -> String,
): String {
    // A day with no label is shown as its place ("Day 2"), so that name is taken too.
    val taken =
        daysOf(week)
            .mapIndexed { i, day ->
                day.week.day
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: candidate(i + 1).trim()
            }.toSet()
    return generateSequence(1) { it + 1 }.map(candidate).first { it.trim() !in taken }
}

/** The day keyed [key] traded places with its neighbour [by] (−1 or +1) within its own week. */
fun ProgramDraft.movingDay(
    key: Int,
    by: Int,
): ProgramDraft {
    val from = weeks.indexOfFirst { it.key == key }
    val week = weeks.getOrNull(from)?.week?.week
    val siblings = weeks.indices.filter { weeks[it].week.week == week }
    // An unknown key has no siblings, and an end day has no neighbour: either way, nothing moves.
    val to = siblings.getOrNull(siblings.indexOf(from) + by)?.takeIf { from >= 0 } ?: return this
    val moved = weeks.toMutableList()
    moved[from] = weeks[to]
    moved[to] = weeks[from]
    return copy(weeks = moved)
}

/**
 * Every entry's week renumbered by [number], then the list put back in week order, stably: the file is
 * written in draft order, and a program reads top to bottom as it runs.
 */
private fun ProgramDraft.renumbered(number: (Long) -> Long): ProgramDraft =
    copy(
        weeks =
            weeks
                .map { it.copy(week = it.week.withShared(week = number(it.week.week))) }
                .sortedBy { it.week.week },
    )
