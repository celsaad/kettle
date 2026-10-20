package com.casco.kettle.domain

/**
 * A library file with every count past its ceiling clamped back to the ceiling, as YAML, or null if
 * there was nothing to repair or nothing parseable to repair it in. `repairLibraryBounds` in
 * `src/domain/yaml-mapping.ts` says why it exists: the ceilings turned files that used to parse into
 * refusals, and a refused library is reseeded, so without this the release that added them would
 * have replaced the user's library with the starter one.
 *
 * Driven by the schema's own complaints ([SchemaReader.ceilings]), so a ceiling added later is
 * repaired without touching this. Only a value past a ceiling is repaired; a zero, a fraction or a
 * missing field was already invalid before the ceilings and is left for quarantine.
 *
 * The tree is rebuilt along each repaired path rather than edited in place, which leaves the
 * engine's own maps alone and gives the same result as TS's in-place edit: a value shared through an
 * alias is reported, and repaired, at every path it appears at.
 */
fun repairLibraryBounds(text: String): String? {
    // A syntax error, not an out-of-range value, leaves no document to walk.
    var document = (loadYaml(text) as? YamlLoad.Loaded)?.value
    val reader = SchemaReader()
    if (document == null || reader.result(reader.library(document)) is ParseResult.Ok) return null

    // After the ceilings, so an EMOM is clamped against the `total_minutes` they already repaired.
    val ceilings = reader.ceilings
    ceilings.forEach { (path, ceiling) -> document = withValue(document, path, ceiling) }
    val emoms = emomClamps(document)
    emoms.forEach { (path, minutes) -> document = withValue(document, path, minutes) }

    return if (ceilings.isEmpty() && emoms.isEmpty()) null else dumpYaml(document)
}

/** [node] with [value] at [path], copying only the maps and lists on the way down. */
private fun withValue(
    node: Any?,
    path: Path,
    value: Double,
): Any? {
    if (path.isEmpty()) return value
    val key = path.first()
    val rest = path.drop(1)
    return when (node) {
        is Map<*, *> -> LinkedHashMap<Any?, Any?>(node).apply { put(key, withValue(node[key], rest, value)) }
        is List<*> -> node.toMutableList().apply { set(key as Int, withValue(node[key], rest, value)) }
        else -> node
    }
}

/**
 * The one ceiling [SchemaReader.ceilings] can't see: an EMOM's interval count is derived, so it is a
 * refinement with no maximum to read. Handled here, and deliberately the only such case. The interval
 * is what the user wrote down, so the block is shortened rather than the interval stretched.
 */
private fun emomClamps(document: Any?): List<Pair<Path, Double>> {
    val exercises = (document as? Map<*, *>)?.get("exercises") as? List<*> ?: return emptyList()
    return exercises.mapIndexedNotNull { index, exercise ->
        clampedMinutes(exercise)?.let { listOf("exercises", index, "config", "total_minutes") to it }
    }
}

private fun clampedMinutes(exercise: Any?): Double? {
    val fields = exercise as? Map<*, *>
    val config = fields?.get("config") as? Map<*, *>
    val intervalSec = (config?.get("interval_sec") as? Number)?.toDouble()
    val totalMinutes = (config?.get("total_minutes") as? Number)?.toDouble()
    return when {
        fields?.get("type") != "emom" || intervalSec == null || totalMinutes == null -> {
            null
        }

        intervalSec <= 0 -> {
            null
        }

        // Written as "at most", as TS writes it: a NaN count is never at most anything, so it is clamped.
        emomIntervalCount(intervalSec, totalMinutes) <= MAX_ROUNDS -> {
            null
        }

        // Checked against the same helper rather than trusted: this inverts a floored division, and
        // the value it gives is the one the repaired file has to parse with.
        else -> {
            val minutes = intervalSec * MAX_ROUNDS / SECONDS_PER_MINUTE
            if (emomIntervalCount(intervalSec, minutes) > MAX_ROUNDS) {
                intervalSec * (MAX_ROUNDS - 1) / SECONDS_PER_MINUTE
            } else {
                minutes
            }
        }
    }
}
