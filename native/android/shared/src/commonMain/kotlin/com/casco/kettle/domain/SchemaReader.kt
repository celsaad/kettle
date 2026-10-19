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
 */
internal class SchemaReader {
    private val issues = mutableListOf<Pair<String, String>>()

    fun <T : Any> result(value: T?): ParseResult<T> {
        if (issues.isEmpty()) {
            return ParseResult.Ok(checkNotNull(value) { "a schema read returned null without refusing anything" })
        }
        return ParseResult.SchemaMismatch(
            paths = issues.map { it.first }.distinct().sorted(),
            detail = issues.joinToString("; ") { (path, message) -> "$path: $message" },
        )
    }

    fun refuse(
        path: Path,
        message: String,
    ): Nothing? {
        issues += path.joinToString(".").ifEmpty { "(root)" } to message
        return null
    }

    fun fields(
        value: Any?,
        path: Path,
    ): Fields? = if (value is Map<*, *>) Fields(value, path, this) else refuse(path, "expected a mapping")

    fun <T : Any> list(
        value: Any?,
        path: Path,
        element: Read<T>,
    ): List<T>? {
        if (value !is List<*>) return refuse(path, "expected a list")
        // Every element is read, so each refused one is reported, before any is given up on.
        val elements = value.mapIndexed { index, item -> element(item, path + index) }
        return if (null in elements) null else elements.filterNotNull()
    }

    fun string(
        value: Any?,
        path: Path,
    ): String? = value as? String ?: refuse(path, "expected a string")

    /** `idSchema`: a string that isn't empty. */
    fun id(
        value: Any?,
        path: Path,
    ): String? = string(value, path)?.let { if (it.isEmpty()) refuse(path, "must not be empty") else it }

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
            number != kotlin.math.floor(number) -> refuse(path, "expected an integer")
            kotlin.math.abs(number) > MAX_SAFE_INTEGER -> refuse(path, "outside the safe integer range")
            else -> number.toLong()
        }
    }
}

/** The fields of one mapping. Each accessor is one of zod's presence rules. */
internal class Fields(
    private val map: Map<*, *>,
    private val path: Path,
    private val reader: SchemaReader,
) {
    fun raw(key: String): Any? = map[key]

    /** Must be present, and must pass [read]. */
    fun <T : Any> required(
        key: String,
        read: Read<T>,
    ): T? = if (map.containsKey(key)) reader.read(map[key], path + key) else reader.refuse(path + key, "required")

    /** `.optional()`: may be absent. A key with nothing after it is null, and null is refused. */
    fun <T : Any> optional(
        key: String,
        read: Read<T>,
    ): T? = if (map.containsKey(key)) reader.read(map[key], path + key) else null

    /** `.nullable()`: must be present, and may be null. */
    fun <T : Any> nullable(
        key: String,
        read: Read<T>,
    ): T? =
        when {
            !map.containsKey(key) -> reader.refuse(path + key, "required")
            else -> map[key]?.let { reader.read(it, path + key) }
        }

    /** `.nullable().default(null)`: absent reads as null. */
    fun <T : Any> nullableOrAbsent(
        key: String,
        read: Read<T>,
    ): T? = map[key]?.let { reader.read(it, path + key) }
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
            if (minInclusive) number < min else number <= min -> reader.refuse(path, "too small")
            number > max -> reader.refuse(path, "too big")
            else -> number
        }

    companion object {
        val ANY = Bounds()
        val POSITIVE = Bounds(min = 0.0, minInclusive = false)
        val NONNEGATIVE = Bounds(min = 0.0)
    }
}

private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0
