package com.casco.kettle.domain

/** Where a value sits in the document: map keys and list indices, from the root down. */
internal typealias Path = List<Any>

internal val ROOT: Path = emptyList()

/** A schema read: one value at one path, null when it's refused (or legitimately absent or null). */
internal typealias Read<T> = SchemaReader.(value: Any?, path: Path) -> T?

/**
 * The Kotlin side of the zod schemas in `src/domain/schema.ts`, as small readers over the loaded
 * tree. Each records its refusal and returns null rather than throwing, so one pass collects every
 * refused field the way zod does, and the caller turns the tree into the domain value only if
 * nothing was refused.
 *
 * Unknown keys are never looked at, which is how they are dropped at every level. And a reader only
 * descends into a value after its type check passes, the property that keeps an alias bomb under an
 * unknown key, or behind one refused element, from ever being walked.
 *
 * A refusal is fatal or not, as zod 4's issues are, and the difference decides which refusals a
 * document reports: a refinement is skipped once anything fatal was refused beneath it ([refined]).
 * A wrong type, a missing key, a fraction where an integer goes and an unknown `type` are fatal. A
 * value out of range (the safe-integer one included), an empty string, a list too short, and a
 * failed refinement itself are not. Read off zod, not reasoned out, and `LibraryTest` holds every rule
 * a document can show: an unknown `type` is fatal too, but no refinement sits above one to tell.
 */
internal class SchemaReader {
    private class Issue(
        val at: Path,
        val message: String,
        val fatal: Boolean,
        val ceiling: Double?,
    ) {
        val path: String = at.joinToString(".").ifEmpty { "(root)" }
    }

    private val issues = mutableListOf<Issue>()

    /**
     * Every value refused only for being past a ceiling, with that ceiling, in the order they were
     * refused: zod's `too_big` issues, which carry the same two things. It is all `repairLibraryBounds`
     * needs, so the ceilings are read off the schema rather than listed a second time.
     */
    val ceilings: List<Pair<Path, Double>>
        get() = issues.mapNotNull { issue -> issue.ceiling?.let { issue.at to it } }

    fun <T : Any> result(value: T?): ParseResult<T> {
        if (issues.isEmpty()) {
            return ParseResult.Ok(checkNotNull(value) { "a schema read returned null without refusing anything" })
        }
        return ParseResult.SchemaMismatch(
            paths = issues.map { it.path }.distinct().sorted(),
            detail = issues.joinToString("; ") { "${it.path}: ${it.message}" },
        )
    }

    /**
     * Records a refusal. It's [fatal] when the value isn't the type at all, so nothing above it can be
     * refined; a range, a length or a refinement refuses a value of the right type, and isn't. A
     * [ceiling] is the maximum a value was refused for exceeding.
     */
    fun refuse(
        path: Path,
        message: String,
        fatal: Boolean = true,
        ceiling: Double? = null,
    ): Nothing? {
        issues += Issue(path, message, fatal, ceiling)
        return null
    }

    /**
     * Reads with [read], then runs [refine] over what was read only if nothing fatal was refused on
     * the way, as zod runs a schema's `.refine` checks. [refine] gets the raw mapping, not the read
     * value: a field refused only for its range reads as null, but the refinement still sees it.
     * Every field is the right type by the time it runs, which is what lets it cast.
     */
    fun <T : Any> refined(
        fields: Fields,
        read: () -> T?,
        refine: SchemaReader.(Fields) -> Unit,
    ): T? {
        val start = issues.size
        val value = read()
        if (issues.drop(start).none { it.fatal }) refine(fields)
        return value
    }

    fun fields(
        value: Any?,
        path: Path,
    ): Fields? = if (value is Map<*, *>) Fields(value, path, this) else refuse(path, "expected a mapping")

    /** [minSize] is checked whatever the elements did, as zod checks a length even past a fatal element. */
    fun <T : Any> list(
        value: Any?,
        path: Path,
        minSize: Int = 0,
        element: Read<T>,
    ): List<T>? {
        if (value !is List<*>) return refuse(path, "expected a list")
        // Every element is read, so each refused one is reported, before any is given up on.
        val elements = value.mapIndexed { index, item -> element(item, path + index) }
        return when {
            value.size < minSize -> refuse(path, "expected at least $minSize", fatal = false)
            null in elements -> null
            else -> elements.filterNotNull()
        }
    }

    fun string(
        value: Any?,
        path: Path,
    ): String? = value as? String ?: refuse(path, "expected a string")

    fun boolean(
        value: Any?,
        path: Path,
    ): Boolean? = value as? Boolean ?: refuse(path, "expected a boolean")

    /** `idSchema`, and `z.string().min(1)` wherever else it's written: a string that isn't empty. */
    fun nonEmpty(
        value: Any?,
        path: Path,
    ): String? = string(value, path)?.let { if (it.isEmpty()) refuse(path, "must not be empty", fatal = false) else it }

    /**
     * zod's `number()`: finite, so `.inf` and `.nan` are refused. An integer too big for a `Long`
     * arrives as a `BigInteger`, which is still a `Number` and becomes the same double JavaScript
     * would have parsed it to.
     */
    fun number(
        value: Any?,
        path: Path,
        bounds: Bounds = Bounds.ANY,
    ): Double? {
        val number = (value as? Number)?.toDouble()
        return if (number == null ||
            !number.isFinite()
        ) {
            refuse(path, "expected a number")
        } else {
            bounds.check(number, path, this)
        }
    }

    /**
     * zod's `int()`, which is JavaScript's safe-integer range, so `3.0` passes and so does any whole
     * number up to 2^53 − 1. That range is why integers are `Long` throughout the domain: an `Int`
     * would refuse values the TypeScript parser accepts.
     */
    fun integer(
        value: Any?,
        path: Path,
        bounds: Bounds = Bounds.ANY,
    ): Long? {
        val number = number(value, path, bounds) ?: return null
        return when {
            number != kotlin.math.floor(number) -> {
                refuse(path, "expected an integer")
            }

            // zod reports the top of the range as a ceiling like any other, so a repair clamps to it.
            number > MAX_SAFE_INTEGER -> {
                refuse(
                    path,
                    "above the safe integer range",
                    fatal = false,
                    ceiling = MAX_SAFE_INTEGER,
                )
            }

            number < -MAX_SAFE_INTEGER -> {
                refuse(path, "below the safe integer range", fatal = false)
            }

            else -> {
                number.toLong()
            }
        }
    }
}

/** The fields of one mapping. Each accessor is one of zod's presence rules. */
internal class Fields(
    private val map: Map<*, *>,
    val path: Path,
    private val reader: SchemaReader,
) {
    private val asked = mutableSetOf<String>()

    /**
     * Every key a presence rule below was asked about, read or absent: the fields this mapping's
     * schema has. Merging an override uses it to tell a patch's known keys from its typos, so the
     * list is the schema's own rather than a second copy of it.
     */
    val known: Set<String> get() = asked

    fun raw(key: String): Any? = map[key]

    fun has(key: String): Boolean = map.containsKey(key)

    // Unread values, for a refinement only: it runs once every field under it is the right type, which
    // is what makes these casts safe.

    fun rawNumber(key: String): Double? = (map[key] as Number?)?.toDouble()

    fun rawString(key: String): String? = map[key] as String?

    /** A list of mappings, each as its own [Fields] at its own path. */
    fun rawItems(key: String): List<Fields> =
        (map[key] as List<*>).mapIndexed { index, item -> Fields(item as Map<*, *>, path + key + index, reader) }

    /** Must be present, and must pass [read]. */
    fun <T : Any> required(
        key: String,
        read: Read<T>,
    ): T? =
        when {
            !present(key) -> reader.refuse(path + key, "required")
            else -> reader.read(map[key], path + key)
        }

    /** `.optional()`: may be absent. A key with nothing after it is null, and null is refused. */
    fun <T : Any> optional(
        key: String,
        read: Read<T>,
    ): T? = if (present(key)) reader.read(map[key], path + key) else null

    /** `.nullable()`: must be present, and may be null. */
    fun <T : Any> nullable(
        key: String,
        read: Read<T>,
    ): T? =
        when {
            !present(key) -> reader.refuse(path + key, "required")
            else -> map[key]?.let { reader.read(it, path + key) }
        }

    /** `.nullable().default(null)`: absent reads as null. */
    fun <T : Any> nullableOrAbsent(
        key: String,
        read: Read<T>,
    ): T? = if (present(key)) map[key]?.let { reader.read(it, path + key) } else null

    /** Whether [key] is in the mapping, recording that the schema has it. */
    private fun present(key: String): Boolean {
        asked += key
        return map.containsKey(key)
    }
}

/** A number's range, as zod's `positive()`, `nonnegative()` and `min()`/`max()` checks. */
internal class Bounds(
    private val min: Double = Double.NEGATIVE_INFINITY,
    private val minInclusive: Boolean = true,
    private val max: Double = Double.POSITIVE_INFINITY,
) {
    fun check(
        number: Double,
        path: Path,
        reader: SchemaReader,
    ): Double? =
        when {
            if (minInclusive) number < min else number <= min -> reader.refuse(path, "too small", fatal = false)
            number > max -> reader.refuse(path, "too big", fatal = false, ceiling = max)
            else -> number
        }

    companion object {
        val ANY = Bounds()
        val POSITIVE = Bounds(min = 0.0, minInclusive = false)
        val NONNEGATIVE = Bounds(min = 0.0)
    }
}

// The number reads every file shares, by their zod spelling.
internal val positive: Read<Double> = { value, path -> number(value, path, Bounds.POSITIVE) }

internal val nonNegative: Read<Double> = { value, path -> number(value, path, Bounds.NONNEGATIVE) }

internal val positiveInteger: Read<Long> = { value, path -> integer(value, path, Bounds.POSITIVE) }

internal const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0

/** A whole number JS prints and stores exactly, so it can be written as a `Long` without changing it. */
internal fun isSafeWhole(number: Double): Boolean =
    number == kotlin.math.floor(number) && kotlin.math.abs(number) <= MAX_SAFE_INTEGER
