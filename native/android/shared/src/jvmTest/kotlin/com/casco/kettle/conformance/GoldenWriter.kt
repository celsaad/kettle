package com.casco.kettle.conformance

/**
 * A golden as `conformance.test.ts` wrote one, `JSON.stringify(value, null, 2)` and a newline, so a
 * suite whose only implementation is Kotlin can be regenerated without its whole file churning. The
 * `steps` suite is the first (#308): its TS twin went with RN's runner.
 *
 * JS's rules, where they differ from a naive Kotlin print: a whole double is written as an integer
 * (`3`, never `3.0`), an empty object or list stays on one line, and only `"`, `\` and control
 * characters are escaped, never anything outside ASCII.
 */
internal fun goldenJson(value: Any?): String = StringBuilder().also { it.appendJson(value, "") }.append('\n').toString()

private fun StringBuilder.appendJson(
    value: Any?,
    indent: String,
) {
    val inner = "$indent  "
    when (value) {
        null -> {
            append("null")
        }

        is Boolean -> {
            append(value)
        }

        is Int, is Long -> {
            append(value)
        }

        is Number -> {
            appendNumber(value.toDouble())
        }

        is String -> {
            appendString(value)
        }

        is Map<*, *> -> {
            if (value.isEmpty()) {
                append("{}")
                return
            }
            append("{\n")
            value.entries.forEachIndexed { index, (key, entry) ->
                append(inner)
                appendString(key as String)
                append(": ")
                appendJson(entry, inner)
                append(if (index < value.size - 1) ",\n" else "\n")
            }
            append(indent).append('}')
        }

        is List<*> -> {
            if (value.isEmpty()) {
                append("[]")
                return
            }
            append("[\n")
            value.forEachIndexed { index, entry ->
                append(inner)
                appendJson(entry, inner)
                append(if (index < value.size - 1) ",\n" else "\n")
            }
            append(indent).append(']')
        }

        else -> {
            error("no JSON for ${value::class}")
        }
    }
}

private fun StringBuilder.appendNumber(number: Double) {
    require(number.isFinite()) { "JSON has no $number" }
    // Adding 0.0 folds -0.0 into 0.0, which JSON.stringify writes as 0.
    val folded = number + 0.0
    if (folded == Math.rint(folded) && kotlin.math.abs(folded) < 1e21) append(folded.toLong()) else append(folded)
}

private fun StringBuilder.appendString(text: String) {
    append('"')
    for (char in text) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}
