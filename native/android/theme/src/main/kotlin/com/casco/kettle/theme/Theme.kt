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
    val type: Map<String, TypeStyle>,
)

/** One of `Type`'s styles: the RN font role it's set in, and its sizes as written, absent where unset. */
data class TypeStyle(
    val font: String,
    val fontSize: String?,
    val lineHeight: String?,
    val letterSpacing: String?,
)

/**
 * The weight each of RN's font roles (`Fonts` in theme.ts) is drawn at, Space Grotesk's for the
 * display roles and Hanken Grotesk's for the body ones. Native sets these in the system font until the
 * shell bundles both families, so the weight is what carries the hierarchy across meanwhile.
 */
val FONT_WEIGHTS =
    mapOf(
        "display" to "SemiBold",
        "displayMedium" to "Medium",
        "displayBold" to "Bold",
        "body" to "Normal",
        "bodyMedium" to "Medium",
        "bodySemiBold" to "SemiBold",
    )

private const val COLORS = "Colors"
private const val RUNNER_COLORS = "RunnerColors"
private const val SPACING = "Spacing"
private const val MAX_CONTENT_WIDTH = "MaxContentWidth"
private const val TYPE = "Type"
private val GROUPS = listOf(COLORS, RUNNER_COLORS, SPACING, MAX_CONTENT_WIDTH, TYPE)
private val SIZES = listOf("fontSize", "lineHeight", "letterSpacing")

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
        type = objectAt(root, TYPE).mapValues { (key, value) -> typeStyle(key, value) },
    )
}

private fun typeStyle(
    key: String,
    value: kotlinx.serialization.json.JsonElement,
): TypeStyle {
    val name = "$TYPE.$key"
    require(IDENTIFIER.matches(key) && key !in RESERVED) { "$name can't be the name of a Kotlin property" }
    val style = value as? JsonObject ?: refuse("$name isn't an object")
    require(style.keys.all { it == "font" || it in SIZES }) { "$name holds something other than font and $SIZES" }
    val font = (style["font"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    require(font in FONT_WEIGHTS) { "$name.font has to be one of ${FONT_WEIGHTS.keys}" }
    val sizes =
        SIZES.associateWith { size ->
            style[size]?.let {
                (it as? JsonPrimitive)?.takeIf(::isNumber)?.content
                    ?: refuse("$name.$size isn't a number")
            }
        }
    return TypeStyle(font!!, sizes["fontSize"], sizes["lineHeight"], sizes["letterSpacing"])
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
        require(IDENTIFIER.matches(key) && key !in RESERVED) { "$name.$key can't be the name of a Kotlin property" }
        (value as? JsonPrimitive)?.takeIf(accepts)?.content ?: refuse("$name.$key isn't a value this group holds")
    }

private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

/**
 * Kotlin's hard keywords, which can't name a property unescaped, and `tokens`, which the generated
 * file already uses for its own list. Refused here so a bad name fails with its theme.json path rather
 * than as a compile error in a generated file.
 */
private val RESERVED =
    setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
        "tokens",
    )

/** Every refusal is theme.json holding something the generator doesn't translate. */
internal fun refuse(message: String): Nothing = throw IllegalArgumentException(message)
