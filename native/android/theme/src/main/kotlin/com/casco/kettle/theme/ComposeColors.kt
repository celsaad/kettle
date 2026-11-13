package com.casco.kettle.theme

import kotlin.math.roundToInt

// theme.json's two colour groups as Compose: a data class per token set, an instance per scheme or field.

internal fun StringBuilder.appendSchemes(
    light: Map<String, String>,
    dark: Map<String, String>,
) {
    appendLine("/** `Colors.light` and `Colors.dark`: one token set, remapped per scheme. */")
    appendColorClass("KettleColors", light.keys)
    for ((name, scheme) in listOf("LightColors" to light, "DarkColors" to dark)) {
        appendLine("internal val $name =")
        appendColorCall("KettleColors", scheme, "")
        appendLine()
    }
}

/**
 * `RunnerColors`: the runner's fields, each one token set. Which one a screen is drawn in is the step's
 * clock's to decide, not the system scheme's: the runner is the same in either.
 */
internal fun StringBuilder.appendRunner(fields: Map<String, Map<String, String>>) {
    appendLine("/** `RunnerColors`' fields: one token set, remapped per field. */")
    appendColorClass("RunnerPalette", fields.values.first().keys)
    appendLine("/** `RunnerColors`: the live session runner's fields, the same whatever the system scheme. */")
    appendLine("object RunnerColors {")
    for ((field, tokens) in fields) {
        appendLine("    val $field =")
        appendColorCall("RunnerPalette", tokens, "    ")
        appendLine()
    }
    appendLine("    /** Every field by its theme.json name, in file order. */")
    appendLine("    internal val fields =")
    appendLine("        listOf(")
    for (field in fields.keys) appendLine("            \"$field\" to $field,")
    appendLine("        )")
    appendLine("}")
    appendLine()
}

private fun StringBuilder.appendColorClass(
    name: String,
    keys: Set<String>,
) {
    appendLine("@Immutable")
    appendLine("data class $name(")
    for (key in keys) appendLine("    val $key: Color,")
    appendLine(") {")
    appendTokens(keys)
    appendLine("}")
    appendLine()
}

private fun StringBuilder.appendColorCall(
    name: String,
    tokens: Map<String, String>,
    indent: String,
) {
    appendLine("$indent    $name(")
    for ((key, css) in tokens) appendLine("$indent        $key = Color(${argb(css)}),")
    appendLine("$indent    )")
}

private val HEX = Regex("#([0-9a-fA-F]{6})")
private val RGBA = Regex("""rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)""")

private const val OPAQUE = 0xFF
private const val CHANNEL_MAX = 255

/**
 * A CSS color as Compose's ARGB `Long` literal. Alpha rounds the way React Native's
 * `normalizeColor` does, `Math.round(alpha * 255)`, so a translucent token composites to the same
 * byte on both sides.
 */
private fun argb(css: String): String {
    val hex = HEX.matchEntire(css)
    val rgba = RGBA.matchEntire(css)
    val channels =
        when {
            hex != null -> {
                listOf(OPAQUE) + hex.groupValues[1].chunked(2).map { it.toInt(radix = 16) }
            }

            rgba != null -> {
                val values = rgba.groupValues.drop(1)
                val alpha = values.last().toDouble()
                require(alpha in 0.0..1.0) { "$css: alpha has to be between 0 and 1" }
                listOf((alpha * CHANNEL_MAX).roundToInt()) + values.dropLast(1).map { it.toInt() }
            }

            else -> {
                refuse("$css: only #rrggbb and rgba(r, g, b, a) colors are understood")
            }
        }
    require(channels.all { it in 0..CHANNEL_MAX }) { "$css: a channel is out of range" }
    return "0x" + channels.joinToString("") { "%02X".format(it) }
}
