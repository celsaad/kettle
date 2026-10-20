package com.casco.kettle.strings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * One resource from a bundle: its i18next key (`today.nextUp`) and its Android name (`today_nextUp`).
 * Resource names take letters, digits and `_` only, and content pack ids are hyphenated
 * (`import.packs.steady-strength.name`), so a hyphen becomes `_` along with the dots.
 */
sealed interface Entry {
    val key: String
    val name: String get() = key.replace('.', '_').replace('-', '_')
}

data class Text(
    override val key: String,
    val value: String,
) : Entry

/** An i18next plural, its forms keyed by CLDR category (`one`, `other`, and `zero` where a bundle has one). */
data class Plural(
    override val key: String,
    val forms: Map<String, String>,
) : Entry

private val PLURAL_SUFFIX = Regex("_(zero|one|two|few|many|other)$")

/** The bundle's entries in file order, plurals gathered under their base key. */
fun parseBundle(json: String): List<Entry> {
    val flat = LinkedHashMap<String, String>()
    flatten(Json.parseToJsonElement(json).jsonObject, "", flat)

    val entries = LinkedHashMap<String, Entry>()
    for ((path, value) in flat) {
        val match = PLURAL_SUFFIX.find(path)
        if (match == null) {
            entries[path] = Text(path, value)
        } else {
            val base = path.removeRange(match.range)
            val plural = entries[base] as? Plural ?: Plural(base, emptyMap())
            entries[base] = plural.copy(forms = plural.forms + (match.groupValues[1] to value))
        }
    }
    return entries.values.toList()
}

private fun flatten(
    node: JsonObject,
    prefix: String,
    into: MutableMap<String, String>,
) {
    for ((key, value) in node) {
        when (value) {
            is JsonObject -> flatten(value, "$prefix$key.", into)
            is JsonPrimitive -> into["$prefix$key"] = value.content
            else -> error("$prefix$key: arrays aren't a bundle shape i18next is used with here")
        }
    }
}

/**
 * Every bundle as an Android `strings.xml`, keyed by its resource directory: the default language goes
 * in `values`, and each other in `values-<language>`.
 *
 * Argument numbers come from the default language, so a call site passes the same arguments in the same
 * order whatever the locale: alphabetical by placeholder name, with a plural's `count` always first.
 * Alphabetical rather than order of appearance because English gets reworded, and a reworded sentence
 * that renumbered its arguments would swap them at every call site without failing anything.
 */
fun generateResources(
    bundles: Map<String, String>,
    defaultLanguage: String,
): Map<String, String> {
    val parsed = bundles.mapValues { (_, json) -> parseBundle(json).also(::checkNames) }
    val arguments = parsed.getValue(defaultLanguage).associate { it.key to argumentsOf(it) }
    return parsed.entries.associate { (language, entries) ->
        val dir = if (language == defaultLanguage) "values" else "values-$language"
        dir to render(language, entries, arguments)
    }
}

private val PLACEHOLDER = Regex("""\{\{(.*?)}}""")
private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

private fun placeholders(
    key: String,
    value: String,
): Set<String> =
    PLACEHOLDER
        .findAll(value)
        .map { it.groupValues[1] }
        .onEach {
            require(IDENTIFIER.matches(it)) { "$key: {{$it}} is i18next syntax the generator can't translate" }
        }.toSet()

private fun argumentsOf(entry: Entry): List<String> =
    when (entry) {
        is Text -> {
            placeholders(entry.key, entry.value).sorted()
        }

        is Plural -> {
            listOf("count") +
                entry.forms.values
                    .flatMap { placeholders(entry.key, it) }
                    .toSet()
                    .minus("count")
                    .sorted()
        }
    }

private fun render(
    language: String,
    entries: List<Entry>,
    arguments: Map<String, List<String>>,
): String =
    buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<!-- Generated from src/i18n/locales by :strings. Edit the JSON bundles, not this file. -->")
        appendLine("<resources>")
        for (entry in entries) {
            val args = arguments[entry.key].orEmpty()

            fun format(value: String) = formatValue(language, entry.key, value, args)
            if (args.isNotEmpty()) {
                appendLine("    <!-- ${args.withIndex().joinToString { (i, name) -> "%${i + 1}\$s = $name" }} -->")
            }
            when (entry) {
                is Text -> {
                    val unformatted = if (args.isEmpty() && '%' in entry.value) """ formatted="false"""" else ""
                    appendLine("""    <string name="${entry.name}"$unformatted>${format(entry.value)}</string>""")
                }

                is Plural -> {
                    appendLine("""    <plurals name="${entry.name}">""")
                    for (category in CATEGORIES.filter { it in entry.forms }) {
                        appendLine(
                            """        <item quantity="$category">${format(entry.forms.getValue(category))}</item>""",
                        )
                    }
                    appendLine("    </plurals>")
                    val zero = entry.forms[ZERO]
                    val note = if (zero == null) " Empty: this bundle has no _zero." else ""
                    appendLine("    <!-- What pluralText shows for a count of 0, ahead of the plural rule.$note -->")
                    appendLine("""    <string name="${entry.zeroName}">${zero?.let(::format).orEmpty()}</string>""")
                }
            }
        }
        appendLine("</resources>")
    }

/**
 * i18next consults `_zero` before the plural rule whenever `count` is 0, in any language. Android has no
 * such step: it selects `zero` only where CLDR's rule has that category, and Portuguese files 0 under
 * `one`, which rendered "0 sessão". So `_zero` becomes a string of its own rather than a plural item, and
 * every plural gets one, empty where the bundle has none, so `pluralText` can always ask for it.
 */
private const val ZERO = "zero"

// CLDR's order, `zero` left out for the reason above.
private val CATEGORIES = listOf("one", "two", "few", "many", "other")

private val Plural.zeroName get() = "${name}_$ZERO"

private val RESOURCE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

/** Flattening joins with `_`, which keys use too, so two keys can land on one name; aapt2 would keep either. */
private fun checkNames(entries: List<Entry>) {
    val seen = HashMap<String, String>()
    for (entry in entries) {
        val names = if (entry is Plural) listOf(entry.name, entry.zeroName) else listOf(entry.name)
        for (name in names) {
            require(RESOURCE_NAME.matches(name)) { "${entry.key}: $name isn't a valid resource name" }
            val other = seen.put(name, entry.key)
            require(other == null) { "$other and ${entry.key} would both be the resource $name" }
        }
    }
}

/**
 * A value as Android reads it back: escaped, and with each `{{name}}` turned into its positional
 * argument. A value with arguments goes through `String.format` at the call site, so its own `%` is
 * doubled there; one without is never formatted and keeps it as written.
 */
private fun formatValue(
    language: String,
    key: String,
    value: String,
    args: List<String>,
): String {
    val used = placeholders(key, value)
    for (name in used) {
        require(name in args) { "$language $key: {{$name}} isn't a placeholder in English" }
    }
    var text = escape(value)
    if (args.isNotEmpty()) text = text.replace("%", "%%")
    // Escaping leaves braces and identifiers alone, so the placeholders are still intact to replace.
    return PLACEHOLDER.replace(text) { "%${args.indexOf(it.groupValues[1]) + 1}\$s" }
}

private val SYNTAX =
    mapOf(
        '\\' to "\\\\",
        '\'' to "\\'",
        '"' to "\\\"",
        '\n' to "\\n",
        '\t' to "\\t",
        '&' to "&amp;",
        '<' to "&lt;",
        '>' to "&gt;",
    )

/**
 * A value as aapt2 will read it back verbatim. `<c>` is the one tag the bundles use (react-i18next's
 * `Trans` renders it as code on the help screens), so it stays markup: `getText` keeps it as a span and
 * `getString` drops it, where escaping it would print the tag. Any other `<` is text.
 */
private fun escape(value: String): String {
    var text = value.map { SYNTAX[it] ?: it.toString() }.joinToString("")
    text = text.replace("&lt;c&gt;", "<c>").replace("&lt;/c&gt;", "</c>")
    if (text.startsWith('@') || text.startsWith('?')) text = "\\$text"
    // Unquoted, aapt2 trims the ends and collapses runs of spaces; quoted, it keeps them.
    val fragile = value.startsWith(' ') || value.endsWith(' ') || "  " in value
    return if (fragile) "\"$text\"" else text
}
