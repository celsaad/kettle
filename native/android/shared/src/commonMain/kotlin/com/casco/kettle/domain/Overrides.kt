package com.casco.kettle.domain

/**
 * What happened to one program-week override, as `OverrideReport` in `src/domain/yaml-mapping.ts`,
 * whose comment says why each part has the granularity it has. [refused] is the whole patch: it is
 * validated merged, as a unit, and none of it applies when that fails. [unknownKeys] is per key,
 * since the known keys beside a typo still apply.
 */
data class OverrideReport(
    val refused: Boolean,
    val unknownKeys: List<String>,
)

/**
 * One override merge, as `OverrideMerge` in TS. [effective] is what the runner gets: the patched
 * target, or the target untouched when the schema refuses the patch.
 *
 * [asAuthored] is **for editing only**, so that a patch the schema now refuses can still be read and
 * corrected: the target's overridable fields with the patch laid over them, raw and unchecked, keyed
 * snake_case like the patch itself. That is the one departure from TS, where it is a whole `T`: an
 * unchecked value like `sets: "abc"` has no home in a typed model, so it stays a map. A plain exercise
 * block has no fields of its own to override, so its map is empty.
 */
data class OverrideMerge<out T>(
    val effective: T,
    val asAuthored: Map<String, OverrideValue>,
    val report: OverrideReport,
)

/**
 * A week's patch to an exercise's config, re-validated merged, as `mergeExerciseOverride` in TS: the
 * patch is typed only as numbers and strings, so the merged config is the first point anything
 * checks `sets: 200000` or `rest_sec: -5`. A refusal falls back to the exercise as the library has
 * it, since this resolves during render and a week that loses its patch is a smaller loss than a
 * program that can't open.
 *
 * Which keys are known is read off the type's own schema ([Fields.known]), as TS reads it off the
 * writer, so there is no list to keep in step. Unknown keys go to the reader too, which never looks
 * at them, and that is how they are dropped.
 */
fun mergeExerciseOverride(
    exercise: Exercise,
    config: Map<String, OverrideValue>,
): OverrideMerge<Exercise> {
    val (type, raw) = exercise.config.toRaw()
    val merged = raw + config.mapValues { (_, value) -> value.toRaw() }
    val reader = SchemaReader()
    val fields = Fields(merged, ROOT, reader)
    val read = reader.result(reader.exerciseConfig(type, fields))
    val report = OverrideReport(read !is ParseResult.Ok, config.keys.filterNot(fields.known::contains))
    return OverrideMerge(
        effective = if (read is ParseResult.Ok) exercise.copy(config = read.value) else exercise,
        asAuthored = overrideValues(merged.filterKeys(fields.known::contains)),
        report = report,
    )
}

/**
 * A week's patch to a circuit's own repeat and rest params, as `mergeBlockOverride` in TS. A plain
 * exercise block has none, so every key is unknown and nothing changes; those are patched with an
 * exercise override instead.
 */
fun mergeBlockOverride(
    block: WorkoutBlock,
    config: Map<String, OverrideValue>,
): OverrideMerge<WorkoutBlock> {
    if (block !is WorkoutBlock.Circuit) {
        return OverrideMerge(block, emptyMap(), OverrideReport(refused = false, unknownKeys = config.keys.toList()))
    }
    val (patch, unknown) = config.entries.partition { it.key in BLOCK_OVERRIDE_KEYS }
    val merged = block.toRaw() + patch.associate { it.key to it.value.toRaw() }
    val reader = SchemaReader()
    val read = reader.result(reader.block(merged, ROOT))
    return OverrideMerge(
        effective = (read as? ParseResult.Ok)?.value ?: block,
        asAuthored = overrideValues(merged.filterKeys { it in BLOCK_OVERRIDE_KEYS }),
        report = OverrideReport(read !is ParseResult.Ok, unknown.map { it.key }),
    )
}

/**
 * The keys a block override may set, spelled out rather than read off the block, which also carries
 * `type`, `id` and `exercises`. A patch is a free record, so `config: { exercises: 2 }` imports
 * cleanly; laid over the raw block it would replace the member list with a number.
 */
private val BLOCK_OVERRIDE_KEYS =
    setOf(REST_BETWEEN_EXERCISES, REST_BETWEEN_ROUNDS, ROUNDS)

private const val ROUNDS = "rounds"
private const val REST_BETWEEN_EXERCISES = "rest_between_exercises_sec"
private const val REST_BETWEEN_ROUNDS = "rest_between_rounds_sec"

/** The validated half of [mergeExerciseOverride], for code that runs what it gets. */
fun applyExerciseOverride(
    exercise: Exercise,
    config: Map<String, OverrideValue>,
): Exercise = mergeExerciseOverride(exercise, config).effective

/** The validated half of [mergeBlockOverride]. */
fun applyBlockOverride(
    block: WorkoutBlock,
    config: Map<String, OverrideValue>,
): WorkoutBlock = mergeBlockOverride(block, config).effective

/**
 * The inverse of [applyExerciseOverride]: the snake_case config keys whose value [edited] changed
 * from [base], ready to store as an override's `config`. A key [edited] leaves absent is never
 * emitted, because there is no patch value that means "remove".
 */
fun diffExerciseOverride(
    base: Exercise,
    edited: Exercise,
): Map<String, OverrideValue> {
    val before = overrideValues(base.config.toRaw().second)
    return overrideValues(edited.config.toRaw().second).filter { (key, value) -> value != before[key] }
}

/**
 * The inverse of [applyBlockOverride], for two circuits; anything else has no params to differ in.
 * A rest [edited] doesn't declare is never emitted, for the same reason as [diffExerciseOverride].
 */
fun diffBlockOverride(
    base: WorkoutBlock,
    edited: WorkoutBlock,
): Map<String, OverrideValue> {
    if (base !is WorkoutBlock.Circuit || edited !is WorkoutBlock.Circuit) return emptyMap()
    return buildMap {
        if (edited.rounds != base.rounds) put(ROUNDS, OverrideValue.Numeric(edited.rounds.toDouble()))
        changed(edited.restBetweenExercisesSec, base.restBetweenExercisesSec)?.let { put(REST_BETWEEN_EXERCISES, it) }
        changed(edited.restBetweenRoundsSec, base.restBetweenRoundsSec)?.let { put(REST_BETWEEN_ROUNDS, it) }
    }
}

private fun changed(
    edited: Double?,
    base: Double?,
): OverrideValue? = edited?.takeIf { it != base }?.let(OverrideValue::Numeric)

/** A patch value as the raw tree holds it, which is what a schema reader reads. */
internal fun OverrideValue.toRaw(): Any =
    when (this) {
        is OverrideValue.Numeric -> value
        is OverrideValue.Text -> value
    }

/** Raw values back to a patch's: the writer's numbers, or whatever a patch laid over them. */
private fun overrideValues(raw: Map<String, Any?>): Map<String, OverrideValue> =
    raw.mapValues { (_, value) ->
        when (value) {
            is Number -> OverrideValue.Numeric(value.toDouble())
            else -> OverrideValue.Text(value as String)
        }
    }
