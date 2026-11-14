package com.casco.kettle.domain

/**
 * Today's "Off the plan" (#412): the two workouts run most recently, newest first, leaving out
 * [except], the one the next-up card already offers. Short of that from the log, it is filled up from
 * the library in file order, so someone with no history still has something to start. Workouts the log
 * names but the library no longer has are passed over. [sessions] is newest first, as `SessionLog.logged`.
 */
fun offThePlan(
    library: Library,
    sessions: List<Session>,
    except: String?,
): List<Workout> {
    val byId = library.workouts.associateBy { it.id }
    val recent = sessions.mapNotNull { it.workout?.let(byId::get) }
    return (recent + library.workouts).distinctBy { it.id }.filter { it.id != except }.take(OFF_THE_PLAN_COUNT)
}

/** How many workouts "Off the plan" shows: two, as the canvas draws it, under the next-up card. */
private const val OFF_THE_PLAN_COUNT = 2
