package com.casco.kettle.domain

/**
 * Session files, as the session half of `src/domain/yaml-mapping.ts`. The schema they are read with
 * is in `SessionSchema.kt`. Both are held to `conformance/session.yaml`.
 */
fun parseSessionYaml(text: String): ParseResult<Session> = parseYaml(text) { session(it) }

/** What [parseSessionYaml] reads back unchanged. An absent optional is left out, never written as null. */
fun serializeSessionYaml(session: Session): String = dumpYaml(session.toRaw())

/**
 * The whole log as one document, `serializeSessionArchiveYaml` in `src/domain/yaml-mapping.ts`: the
 * history export and the backup's `kettle-history.yaml` (#237), since a share hands over one file. Each
 * session is what its own file holds. Sorted oldest-first here, where RN's two callers each sort: an
 * archive read top to bottom in some other app runs forward in time. [exportedAt] is the caller's clock.
 */
fun serializeSessionArchiveYaml(
    sessions: List<Session>,
    exportedAt: String,
): String =
    dumpYaml(
        linkedMapOf(
            "version" to 1L,
            "exported_at" to exportedAt,
            "sessions" to sessions.sortedBy { it.startedAt }.map { it.toRaw() },
        ),
    )

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
                put("type", ExerciseType.TimedHold.yamlName)
                put(
                    "sets",
                    entry.sets.map { linkedMapOf("hold_sec" to it.holdSec, "rest_taken_sec" to it.restTakenSec) },
                )
            }

            is SessionEntry.Reps -> {
                put("type", ExerciseType.Reps.yamlName)
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
                put("type", ExerciseType.Rest.yamlName)
                put("rest_taken_sec", entry.restTakenSec)
            }

            is SessionEntry.Hiit -> {
                put("type", ExerciseType.Hiit.yamlName)
                put("rounds_completed", entry.roundsCompleted)
            }

            is SessionEntry.Emom -> {
                put("type", ExerciseType.Emom.yamlName)
                put("minutes", entry.minutes.map { minute -> buildMap { putIfPresent("reps", minute.reps) } })
            }

            is SessionEntry.Amrap -> {
                put("type", ExerciseType.Amrap.yamlName)
                put("rounds_completed", entry.roundsCompleted)
                putIfPresent("extra_reps", entry.extraReps)
            }

            is SessionEntry.Cardio -> {
                put("type", ExerciseType.Cardio.yamlName)
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
