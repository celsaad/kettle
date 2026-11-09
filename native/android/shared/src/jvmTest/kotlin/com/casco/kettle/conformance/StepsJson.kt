package com.casco.kettle.conformance

import com.casco.kettle.domain.CircuitPosition
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.UpcomingItem
import com.casco.kettle.domain.UpcomingMember

/**
 * The step model in the goldens' shape: RN's `RunnerStep` as JSON, an absent optional left out
 * rather than null, which is why each field goes through [putPresent]. In the order [goldenJson]
 * writes, which is what TS wrote for all but a few spread-built steps (#308).
 */
internal fun stepJson(step: RunnerStep): Map<String, Any?> {
    val (kind, fields) =
        when (step) {
            is RunnerStep.Hold -> {
                "hold" to
                    buildMap {
                        put("exerciseName", step.exerciseName)
                        putPresent("holdTargetSec", step.holdTargetSec)
                        putPresent("holdTargetMaxSec", step.holdTargetMaxSec)
                        putPresent("holdEndSec", step.holdEndSec)
                        put("setIndex", step.setIndex)
                        put("setTotal", step.setTotal)
                        putPresent("notes", step.notes)
                    }
            }

            is RunnerStep.Reps -> {
                "reps" to
                    buildMap {
                        put("exerciseName", step.exerciseName)
                        put("targetReps", step.targetReps)
                        putPresent("targetRepsMax", step.targetRepsMax)
                        putPresent("targetWeightKg", step.targetWeightKg)
                        put("setIndex", step.setIndex)
                        put("setTotal", step.setTotal)
                        putPresent("notes", step.notes)
                    }
            }

            is RunnerStep.Interval -> {
                "interval" to
                    buildMap {
                        put("exerciseName", step.exerciseName)
                        put("variant", step.variant.json)
                        put("targetSec", step.targetSec)
                        put("countUp", step.countUp)
                        put("setIndex", step.setIndex)
                        put("setTotal", step.setTotal)
                        putPresent("targetReps", step.targetReps)
                        putPresent("cardioDistanceMeters", step.cardioDistanceMeters)
                        putPresent("notes", step.notes)
                    }
            }

            is RunnerStep.Rest -> {
                "rest" to
                    buildMap {
                        put("standalone", step.standalone)
                        put("seconds", step.seconds)
                    }
            }
        }
    return buildMap {
        put("kind", kind)
        put("blockIndex", step.blockIndex)
        put("memberKey", step.memberKey)
        put("exerciseId", step.exerciseId)
        putAll(fields)
        putPresent("circuit", step.circuit?.let(::circuitJson))
    }
}

private fun circuitJson(position: CircuitPosition): Map<String, Any> =
    mapOf(
        "round" to position.round,
        "rounds" to position.rounds,
        "member" to position.member,
        "memberTotal" to position.memberTotal,
    )

private fun MutableMap<String, Any?>.putPresent(
    key: String,
    value: Any?,
) {
    if (value != null) put(key, value)
}

/** `build`'s output; `brief` swaps the whole list for its first and last three. */
internal fun buildJson(
    truncated: Boolean,
    steps: List<RunnerStep>,
    brief: Boolean,
): Map<String, Any?> =
    if (brief) {
        mapOf(
            "truncated" to truncated,
            "length" to steps.size,
            "head" to steps.take(BRIEF).map(::stepJson),
            "tail" to steps.takeLast(BRIEF).map(::stepJson),
        )
    } else {
        mapOf("truncated" to truncated, "length" to steps.size, "steps" to steps.map(::stepJson))
    }

private const val BRIEF = 3

private fun memberJson(member: UpcomingMember): Map<String, Any?> =
    mapOf("memberKey" to member.memberKey, "step" to stepJson(member.step))

internal fun upcomingJson(item: UpcomingItem): Map<String, Any?> =
    when (item) {
        is UpcomingItem.Exercise -> {
            mapOf(
                "kind" to "exercise",
                "memberKey" to item.memberKey,
                "step" to stepJson(item.step),
                "left" to item.left,
                "unit" to item.unit?.json,
                "started" to item.started,
            )
        }

        is UpcomingItem.Rest -> {
            mapOf("kind" to "rest", "seconds" to item.seconds)
        }

        is UpcomingItem.Round -> {
            mapOf(
                "kind" to "round",
                "blockIndex" to item.blockIndex,
                "round" to item.round,
                "rounds" to item.rounds,
                "members" to item.members.map(::memberJson),
            )
        }

        is UpcomingItem.MoreRounds -> {
            mapOf("kind" to "moreRounds", "blockIndex" to item.blockIndex, "count" to item.count, "names" to item.names)
        }

        is UpcomingItem.Circuit -> {
            mapOf("kind" to "circuit", "blockIndex" to item.blockIndex, "rounds" to item.rounds, "names" to item.names)
        }
    }
