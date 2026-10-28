package com.casco.kettle.theme

import kotlin.math.roundToInt

/** Where the generated file lives in `:app`, as its package and as the path under a source root. */
const val OUTPUT_PACKAGE = "com.casco.kettle.shell.theme"

/**
 * theme.json as one Kotlin file: `Colors`' two schemes as `LightColors` and `DarkColors`,
 * `RunnerColors` as a fixed object, `Spacing` and `MaxContentWidth` in dp, and `Type` as `KettleType`'s
 * text styles in sp.
 */
fun generateTheme(json: String): String {
    val theme = parseTheme(json)
    return buildString {
        appendLine("// Generated from src/constants/theme.json by :theme. Edit theme.json, not this file.")
        appendLine("package $OUTPUT_PACKAGE")
        appendLine()
        appendLine("import androidx.compose.runtime.Immutable")
        appendLine("import androidx.compose.ui.graphics.Color")
        appendLine("import androidx.compose.ui.text.TextStyle")
        appendLine("import androidx.compose.ui.text.font.FontWeight")
        appendLine("import androidx.compose.ui.unit.dp")
        appendLine("import androidx.compose.ui.unit.sp")
        appendLine()
        appendSchemes(theme.light, theme.dark)
        appendLine("/** `RunnerColors`: the live session runner's, dark whatever the system scheme. */")
        appendObject("RunnerColors", theme.runner) { "Color(${argb(it)})" }
        appendObject("Spacing", theme.spacing) { "$it.dp" }
        appendLine("val MaxContentWidth = ${theme.maxContentWidth}.dp")
        appendLine()
        appendLine("/**")
        appendLine(" * `Type`: ThemedText's types. In the system font, at the weight of the RN font each names, until")
        appendLine(" * the shell bundles RN's fonts.")
        appendLine(" */")
        appendObject("KettleType", theme.type, ::textStyle)
    }
}

/** A `TextStyle` call on one line, negative spacing parenthesised as Kotlin needs before `.sp`. */
private fun textStyle(style: TypeStyle): String {
    val sp = { value: String -> if (value.startsWith("-")) "($value).sp" else "$value.sp" }
    val arguments =
        listOfNotNull(
            "fontWeight = FontWeight.${FONT_WEIGHTS.getValue(style.font)}",
            style.fontSize?.let { "fontSize = ${sp(it)}" },
            style.lineHeight?.let { "lineHeight = ${sp(it)}" },
            style.letterSpacing?.let { "letterSpacing = ${sp(it)}" },
        )
    return "TextStyle(${arguments.joinToString()})"
}

private fun StringBuilder.appendSchemes(
    light: Map<String, String>,
    dark: Map<String, String>,
) {
    appendLine("/** `Colors.light` and `Colors.dark`: one token set, remapped per scheme. */")
    appendLine("@Immutable")
    appendLine("data class KettleColors(")
    for (key in light.keys) appendLine("    val $key: Color,")
    appendLine(") {")
    appendTokens(light.keys)
    appendLine("}")
    appendLine()
    for ((name, scheme) in listOf("LightColors" to light, "DarkColors" to dark)) {
        appendLine("internal val $name =")
        appendLine("    KettleColors(")
        for ((key, css) in scheme) appendLine("        $key = Color(${argb(css)}),")
        appendLine("    )")
        appendLine()
    }
}

private fun <T> StringBuilder.appendObject(
    name: String,
    tokens: Map<String, T>,
    render: (T) -> String,
) {
    appendLine("object $name {")
    for ((key, value) in tokens) appendLine("    val $key = ${render(value)}")
    appendLine()
    appendTokens(tokens.keys)
    appendLine("}")
    appendLine()
}

private fun StringBuilder.appendTokens(keys: Set<String>) {
    appendLine("    /** Every token by its theme.json name, in file order. */")
    appendLine("    internal val tokens =")
    appendLine("        listOf(")
    for (key in keys) appendLine("            \"$key\" to $key,")
    appendLine("        )")
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
