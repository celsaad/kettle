package com.casco.kettle.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

// The runner's state file (#295): the snapshot as versioned JSON, in app-private, no-backup storage,
// deleted when the session ends. Native only, with no TS twin, so nothing in `conformance/` holds it.
//
// Written by hand rather than through the serialization plugin, so the file's shape is spelled out
// here and changes only on purpose. A change that an older reader would misread bumps the version;
// a reader refuses a version it doesn't know rather than guess at it.
//
// Version 2 (#410): effort became optional, written as null when empty. A version 1 reader takes that
// null for a corrupt file, which is why it's a bump; this reader still takes version 1, whose effort
// is always a number, so a session running across the update resumes.

private const val SNAPSHOT_VERSION = 2L
private val READABLE_VERSIONS = setOf(1L, SNAPSHOT_VERSION)

fun encodeRunnerSnapshot(snapshot: RunnerSnapshot): String =
    buildJsonObject {
        put("version", SNAPSHOT_VERSION)
        put("sessionId", snapshot.sessionId)
        put("workoutBlockCount", snapshot.workoutBlockCount)
        put("stepIndex", snapshot.stepIndex)
        put("visit", snapshot.visit)
        put("phaseStartedAt", snapshot.clock.phaseStartedAt)
        put("pausedAt", snapshot.clock.pausedAt)
        put("pausedMs", snapshot.clock.pausedMs)
        put("stepEndSec", snapshot.clock.stepEndSec)
        put("reps", snapshot.inputs.reps)
        put("weightKg", snapshot.inputs.weightKg)
        put("rpe", snapshot.inputs.rpe)
        put("roundsCompleted", snapshot.inputs.roundsCompleted)
        put("extraReps", snapshot.inputs.extraReps)
        put("entryIndex", JsonObject(snapshot.entryIndex.mapValues { JsonPrimitive(it.value) }))
        put("droppedLastSet", JsonArray(snapshot.droppedLastSet.sorted().map(::JsonPrimitive)))
        put("swapCount", snapshot.swapCount)
        put("adhocCount", snapshot.adhocCount)
        put("updatedAt", snapshot.updatedAt)
        put("steps", JsonArray(snapshot.steps.map(::stepJson)))
    }.toString()

/** [text] as a snapshot. Never throws: whatever can't be read is [SnapshotRead.Corrupt]. */
fun decodeRunnerSnapshot(text: String): SnapshotRead =
    try {
        val root = JsonFields(Json.parseToJsonElement(text).jsonObject)
        val version = root.long("version")
        if (version in READABLE_VERSIONS) SnapshotRead.Read(snapshotOf(root)) else SnapshotRead.UnknownVersion(version)
    } catch (_: IllegalArgumentException) {
        // kotlinx's parse and accessor failures are all IllegalArgumentExceptions, and so are ours.
        SnapshotRead.Corrupt
    }

private fun snapshotOf(root: JsonFields): RunnerSnapshot {
    val steps = root.array("steps").map { stepOf(JsonFields(it.jsonObject)) }
    val entryIndex = root.obj("entryIndex").mapValues { it.value.jsonPrimitive.int }
    val snapshot =
        RunnerSnapshot(
            sessionId = root.string("sessionId"),
            workoutBlockCount = root.optional("workoutBlockCount")?.jsonPrimitive?.int,
            steps = steps,
            stepIndex = root.int("stepIndex"),
            visit = root.long("visit"),
            clock =
                StepClock(
                    root.long("phaseStartedAt"),
                    root.optional("pausedAt")?.jsonPrimitive?.long,
                    root.long("pausedMs"),
                    root.double("stepEndSec"),
                ),
            inputs =
                StepInputs(
                    root.long("reps"),
                    root.double("weightKg"),
                    root.optionalDouble("rpe"),
                    root.long("roundsCompleted"),
                    root.long("extraReps"),
                ),
            entryIndex = entryIndex,
            droppedLastSet = root.array("droppedLastSet").map { JsonFields.stringOf(it, "droppedLastSet") }.toSet(),
            swapCount = root.int("swapCount"),
            adhocCount = root.int("adhocCount"),
            updatedAt = root.long("updatedAt"),
        )
    // An ad-hoc session parks one past its last step, so the index may equal the length.
    require(snapshot.stepIndex in 0..steps.size && entryIndex.values.all { it >= 0 }) { "out of range" }
    return snapshot
}

private fun stepJson(step: RunnerStep): JsonObject =
    buildJsonObject {
        put("blockIndex", step.blockIndex)
        put("memberKey", step.memberKey)
        put("exerciseId", step.exerciseId)
        put("circuit", step.circuit?.let(::circuitJson) ?: JsonNull)
        if (step is WorkStep) {
            put("exerciseName", step.exerciseName)
            put("setIndex", step.setIndex)
            put("setTotal", step.setTotal)
            put("notes", step.notes)
        }
        when (step) {
            is RunnerStep.Hold -> {
                put("kind", "hold")
                put("holdTargetSec", step.holdTargetSec)
                put("holdTargetMaxSec", step.holdTargetMaxSec)
                put("holdEndSec", step.holdEndSec)
            }

            is RunnerStep.Reps -> {
                put("kind", "reps")
                put("targetReps", step.targetReps)
                put("targetRepsMax", step.targetRepsMax)
                put("targetWeightKg", step.targetWeightKg)
            }

            is RunnerStep.Interval -> {
                put("kind", "interval")
                put("variant", step.variant.json)
                put("targetSec", step.targetSec)
                put("countUp", step.countUp)
                put("targetReps", step.targetReps)
                put("cardioDistanceMeters", step.cardioDistanceMeters)
            }

            is RunnerStep.Rest -> {
                put("kind", "rest")
                put("standalone", step.standalone)
                put("seconds", step.seconds)
            }
        }
    }

private fun circuitJson(position: CircuitPosition): JsonObject =
    buildJsonObject {
        put("round", position.round)
        put("rounds", position.rounds)
        put("member", position.member)
        put("memberTotal", position.memberTotal)
    }

/** What every step carries, read before the kind decides the rest. */
private data class StepBase(
    val blockIndex: Int,
    val memberKey: String,
    val exerciseId: String,
    val circuit: CircuitPosition?,
)

private fun stepOf(fields: JsonFields): RunnerStep {
    val circuit =
        fields.optional("circuit")?.let {
            val position = JsonFields(it.jsonObject)
            CircuitPosition(
                position.long("round"),
                position.long("rounds"),
                position.int("member"),
                position.int("memberTotal"),
            )
        }
    val base = StepBase(fields.int("blockIndex"), fields.string("memberKey"), fields.string("exerciseId"), circuit)
    return when (val kind = fields.string("kind")) {
        "rest" -> {
            RunnerStep.Rest(
                base.blockIndex,
                base.memberKey,
                base.exerciseId,
                standalone = fields.boolean("standalone"),
                seconds = fields.double("seconds"),
                circuit = base.circuit,
            )
        }

        "hold", "reps", "interval" -> {
            workStepOf(kind, fields, base)
        }

        else -> {
            throw IllegalArgumentException(kind)
        }
    }
}

private fun workStepOf(
    kind: String,
    fields: JsonFields,
    base: StepBase,
): WorkStep {
    val name = fields.string("exerciseName")
    val setIndex = fields.long("setIndex")
    val setTotal = fields.long("setTotal")
    return when (kind) {
        "hold" -> {
            RunnerStep.Hold(
                base.blockIndex,
                base.memberKey,
                base.exerciseId,
                name,
                holdTargetSec = fields.optionalDouble("holdTargetSec"),
                holdTargetMaxSec = fields.optionalDouble("holdTargetMaxSec"),
                holdEndSec = fields.optionalDouble("holdEndSec"),
                setIndex = setIndex,
                setTotal = setTotal,
                notes = fields.notes(),
                circuit = base.circuit,
            )
        }

        "reps" -> {
            RunnerStep.Reps(
                base.blockIndex,
                base.memberKey,
                base.exerciseId,
                name,
                targetReps = fields.long("targetReps"),
                targetRepsMax = fields.optional("targetRepsMax")?.jsonPrimitive?.long,
                targetWeightKg = fields.optionalDouble("targetWeightKg"),
                setIndex = setIndex,
                setTotal = setTotal,
                notes = fields.notes(),
                circuit = base.circuit,
            )
        }

        else -> {
            val variant = fields.string("variant")
            RunnerStep.Interval(
                base.blockIndex,
                base.memberKey,
                base.exerciseId,
                name,
                variant = requireNotNull(IntervalVariant.entries.firstOrNull { it.json == variant }) { variant },
                targetSec = fields.double("targetSec"),
                countUp = fields.boolean("countUp"),
                setIndex = setIndex,
                setTotal = setTotal,
                targetReps = fields.optional("targetReps")?.jsonPrimitive?.long,
                cardioDistanceMeters = fields.optionalDouble("cardioDistanceMeters"),
                notes = fields.notes(),
                circuit = base.circuit,
            )
        }
    }
}

private fun JsonFields.notes(): String? = optional("notes")?.let { JsonFields.stringOf(it, "notes") }

/** One JSON object's fields, each read strictly: missing, or of the wrong type, is an IllegalArgumentException. */
private class JsonFields(
    private val fields: JsonObject,
) {
    fun long(key: String): Long = field(key).jsonPrimitive.long

    fun int(key: String): Int = field(key).jsonPrimitive.int

    fun double(key: String): Double = field(key).jsonPrimitive.double

    fun string(key: String): String = stringOf(field(key), key)

    fun boolean(key: String): Boolean = requireNotNull(field(key).jsonPrimitive.booleanOrNull) { key }

    fun obj(key: String): JsonObject = field(key).jsonObject

    fun array(key: String): JsonArray = field(key).jsonArray

    /** Null for a JSON null, which is how an absent optional is written. */
    fun optional(key: String): JsonElement? = field(key).takeUnless { it is JsonNull }

    fun optionalDouble(key: String): Double? = optional(key)?.jsonPrimitive?.double

    private fun field(key: String): JsonElement = requireNotNull(fields[key]) { key }

    companion object {
        fun stringOf(
            element: JsonElement,
            key: String,
        ): String = element.jsonPrimitive.takeIf { it.isString }?.content ?: throw IllegalArgumentException(key)
    }
}
