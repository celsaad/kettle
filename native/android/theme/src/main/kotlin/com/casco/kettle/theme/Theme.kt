package com.casco.kettle.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** theme.json, every group in file order. Colors are CSS strings and sizes numbers as written. */
data class Theme(
    val light: Map<String, String>,
    val dark: Map<String, String>,
    val runner: Map<String, String>,
    val spacing: Map<String, String>,
    val maxContentWidth: String,
)

private const val COLORS = "Colors"
private const val RUNNER_COLORS = "RunnerColors"
private const val SPACING = "Spacing"
private const val MAX_CONTENT_WIDTH = "MaxContentWidth"
private val GROUPS = listOf(COLORS, RUNNER_COLORS, SPACING, MAX_CONTENT_WIDTH)

/**
 * theme.json, read strictly: a group or token this doesn't know is refused rather than skipped, so
 * something added for RN can't go missing from the Compose theme without failing the build.
 */
fun parseTheme(json: String): Theme {
    val root = Json.parseToJsonElement(json) as? JsonObject ?: refuse("theme.json isn't an object")
    require(root.keys == GROUPS.toSet()) { "theme.json has to hold exactly $GROUPS, not ${root.keys}" }

    val schemes = objectAt(root, COLORS)
    require(schemes.keys.toList() == listOf("light", "dark")) { "$COLORS has to hold light and dark, in that order" }
    val light = primitives(objectAt(schemes, "light"), "$COLORS.light") { it.isString }
    val dark = primitives(objectAt(schemes, "dark"), "$COLORS.dark") { it.isString }
    require(light.keys == dark.keys) { "$COLORS.light and $COLORS.dark have to hold the same tokens" }

    return Theme(
        light = light,
        dark = dark,
        runner = primitives(objectAt(root, RUNNER_COLORS), RUNNER_COLORS) { it.isString },
        spacing = primitives(objectAt(root, SPACING), SPACING, ::isNumber),
        maxContentWidth =
            (root.getValue(MAX_CONTENT_WIDTH) as? JsonPrimitive)?.takeIf(::isNumber)?.content
                ?: refuse("$MAX_CONTENT_WIDTH isn't a number"),
    )
}

private fun isNumber(value: JsonPrimitive) = !value.isString && value.doubleOrNull != null

private fun objectAt(
    parent: JsonObject,
    key: String,
): JsonObject = parent[key] as? JsonObject ?: refuse("$key isn't an object")

private fun primitives(
    group: JsonObject,
    name: String,
    accepts: (JsonPrimitive) -> Boolean,
): Map<String, String> =
    group.mapValues { (key, value) ->
        (value as? JsonPrimitive)?.takeIf(accepts)?.content ?: refuse("$name.$key isn't a value this group holds")
    }

/** Every refusal is theme.json holding something the generator doesn't translate. */
internal fun refuse(message: String): Nothing = throw IllegalArgumentException(message)
