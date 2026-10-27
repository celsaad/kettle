package com.casco.kettle.domain

/**
 * `rawProgramSchema` from `src/domain/schema.ts`, whose comments say why each rule exists. Every
 * refinement here is gated by [SchemaReader.refined], so which of them a document reports matches zod.
 */
internal fun SchemaReader.program(
    value: Any?,
    path: Path,
): Program? {
    val fields = fields(value, path) ?: return null
    return refined(fields, {
        val id = fields.required("id", SchemaReader::nonEmpty)
        val name = fields.required("name", SchemaReader::nonEmpty)
        val weeks = fields.required("weeks") { v, p -> list(v, p, minSize = 1) { item, at -> week(item, at) } }
        if (id != null && name != null && weeks != null) Program(id, name, weeks) else null
    }) {
        val weeks = it.rawItems("weeks")
        val at = it.path + "weeks"
        if (weeks.map(::weekKey).let { keys -> keys.size != keys.toSet().size }) {
            refuse(at, "must not repeat the same (week, day) pair", fatal = false)
        }
        if (weeks.all(::isRestDay)) refuse(at, "needs at least one week that runs a workout", fatal = false)
    }
}

/**
 * What makes two weeks the same slot: `${week}::${day ?? ''}` in TS, so `1` and `1.0` are one week,
 * `0` and `-0.0` too, and an absent day is the empty one. Adding `0.0` folds the negative zero that a
 * boxed `Double` comparison would otherwise keep apart.
 */
private fun weekKey(week: Fields): Pair<Double, String> =
    (week.rawNumber("week")!! + 0.0) to week.rawString("day").orEmpty()

private fun isRestDay(week: Fields): Boolean = week.raw("rest_day") == true

private fun SchemaReader.week(
    value: Any?,
    path: Path,
): ProgramWeek? {
    val fields = fields(value, path) ?: return null
    return refined(fields, { readWeek(fields) }) {
        val rest = isRestDay(it)
        val workout = it.path + "workout"
        if (!rest && !it.has("workout")) refuse(workout, "needs a workout unless rest_day: true", fatal = false)
        if (rest && it.has("workout")) refuse(workout, "a rest_day week cannot name a workout", fatal = false)
        if (rest &&
            it.has("overrides")
        ) {
            refuse(it.path + "overrides", "a rest_day week has nothing to override", fatal = false)
        }
    }
}

/**
 * Both shapes read every key, so a key the other shape forbids is still type-checked. `rest_day:
 * false` is a training week, and the writer never emits it.
 */
private fun SchemaReader.readWeek(fields: Fields): ProgramWeek? {
    val week = fields.required("week", positiveInteger)
    val day = fields.optional("day", SchemaReader::string)
    val workout = fields.optional("workout", SchemaReader::nonEmpty)
    val restDay = fields.optional("rest_day", SchemaReader::boolean)
    val notes = fields.optional("notes", SchemaReader::string)
    val overrides = fields.optional("overrides") { v, p -> list(v, p) { item, at -> override(item, at) } }
    return when {
        week == null -> null

        restDay == true -> ProgramWeek.RestDay(week, day, notes)

        // The refinement refuses a training week without a workout, so this null is never the result.
        workout == null -> null

        else -> ProgramWeek.Training(week, day, workout, notes, overrides)
    }
}

/** Targets exactly one of an exercise or a circuit block. An empty id counts as no target, as in TS. */
private fun SchemaReader.override(
    value: Any?,
    path: Path,
): ProgramOverride? {
    val fields = fields(value, path) ?: return null
    return refined(fields, {
        val exercise = fields.optional("exercise", SchemaReader::nonEmpty)
        val block = fields.optional("block", SchemaReader::nonEmpty)
        val config = fields.required("config", overrideConfig)
        when {
            config == null -> null
            block != null -> ProgramOverride.BlockOverride(block, config)
            exercise != null -> ProgramOverride.ExerciseOverride(exercise, config)
            else -> null
        }
    }) {
        val targets = listOf("exercise", "block").count { key -> it.rawString(key).orEmpty().isNotEmpty() }
        if (targets != 1) refuse(it.path, "must specify exactly one of exercise or block", fatal = false)
    }
}

/**
 * `z.record(z.string(), z.union([z.number(), z.string()]))`: a free-form patch, checked only when it
 * is applied. Keys arrive as whatever the loader typed them; js-yaml makes every key a string first,
 * so [jsKey] does the same. The loader has already refused two keys that spell the same, so no value
 * is lost doing it.
 */
private val overrideConfig: Read<Map<String, OverrideValue>> = { value, path ->
    if (value !is Map<*, *>) {
        refuse(path, "expected a mapping")
    } else {
        val entries =
            value.entries.map { (key, item) ->
                val name = jsKey(key)
                name to overrideValue(item, path + name)
            }
        if (entries.any { it.second == null }) null else entries.associate { (name, item) -> name to item!! }
    }
}

private fun SchemaReader.overrideValue(
    value: Any?,
    path: Path,
): OverrideValue? =
    when {
        value is String -> OverrideValue.Text(value)
        value is Number && value.toDouble().isFinite() -> OverrideValue.Numeric(value.toDouble())
        else -> refuse(path, "expected a number or a string")
    }
