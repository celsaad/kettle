package com.casco.kettle.shell.library

/** What a piece of a YAML line is, for its colour: the board's four, in existing tokens. */
internal enum class YamlPart { Punctuation, Key, Number, Text }

/**
 * A line of the app's own YAML, cut into coloured pieces (#444). Not a YAML parser: the serializer
 * writes one shape, `indent`, an optional `- `, `key: value`, and folded text under `>-`, so one
 * pattern reads all of it. A line that isn't a key is the folded text's.
 */
internal fun yamlParts(line: String): List<Pair<String, YamlPart>> {
    val match = KEY_LINE.matchEntire(line) ?: return listOf(line to YamlPart.Text)
    val (lead, key, colon) = match.destructured
    val value = match.groupValues[VALUE_GROUP]
    return listOfNotNull(
        (lead to YamlPart.Punctuation).takeIf { lead.isNotEmpty() },
        key to YamlPart.Key,
        colon to YamlPart.Punctuation,
        (value to valuePart(value)).takeIf { value.isNotEmpty() },
    )
}

private fun valuePart(value: String): YamlPart =
    when {
        value.toDoubleOrNull() != null -> YamlPart.Number
        value == "[]" || value == ">-" || value == "|" || value == "|-" -> YamlPart.Punctuation
        else -> YamlPart.Text
    }

private val KEY_LINE = Regex("""^(\s*(?:- )?)([A-Za-z_][A-Za-z0-9_]*)(: ?)(.*)$""")

private const val VALUE_GROUP = 4
