package com.casco.kettle.domain

/**
 * Session files, as the session half of `src/domain/yaml-mapping.ts`. The schema they are read with
 * is in `SessionSchema.kt`. Both are held to `conformance/session.yaml`.
 */
fun parseSessionYaml(text: String): ParseResult<Session> = parseYaml(text) { session(it) }

/** What [parseSessionYaml] reads back unchanged. An absent optional is left out, never written as null. */
fun serializeSessionYaml(session: Session): String = dumpYaml(session.toRaw())

private fun Session.toRaw(): Map<String, Any?> =
    linkedMapOf(
        "version" to version,
        "id" to id,
        "workout" to workout,
        "program" to program,
        "program_week" to programWeek,
        "program_day" to programDay,
        "started_at" to startedAt,
        "ended_at" to endedAt,
        "entries" to entries.map { it.toRaw() },
    )

private fun SessionEntry.toRaw(): Map<String, Any?> =
    buildMap {
        put("exercise", exercise)
        when (val entry = this@toRaw) {
            is SessionEntry.TimedHold -> {
                put("type", "timed_hold")
                put(
                    "sets",
                    entry.sets.map { linkedMapOf("hold_sec" to it.holdSec, "rest_taken_sec" to it.restTakenSec) },
                )
            }

            is SessionEntry.Reps -> {
                put("type", "reps")
                put(
                    "sets",
                    entry.sets.map { set ->
                        buildMap {
                            put("reps", set.reps)
                            putIfPresent("weight", set.weightKg)
                            putIfPresent("rpe", set.rpe)
                            put("rest_taken_sec", set.restTakenSec)
                        }
                    },
                )
            }

            is SessionEntry.Rest -> {
                put("type", "rest")
                put("rest_taken_sec", entry.restTakenSec)
            }

            is SessionEntry.Hiit -> {
                put("type", "hiit")
                put("rounds_completed", entry.roundsCompleted)
            }

            is SessionEntry.Emom -> {
                put("type", "emom")
                put("minutes", entry.minutes.map { minute -> buildMap { putIfPresent("reps", minute.reps) } })
            }

            is SessionEntry.Amrap -> {
                put("type", "amrap")
                put("rounds_completed", entry.roundsCompleted)
                putIfPresent("extra_reps", entry.extraReps)
            }

            is SessionEntry.Cardio -> {
                put("type", "cardio")
                putIfPresent("duration_sec", entry.durationSec)
                putIfPresent("distance_meters", entry.distanceMeters)
            }
        }
    }

/** An optional field: absent is left out, because `key: null` would read back as a refusal. */
private fun MutableMap<String, Any?>.putIfPresent(
    key: String,
    value: Any?,
) {
    if (value != null) put(key, value)
}
