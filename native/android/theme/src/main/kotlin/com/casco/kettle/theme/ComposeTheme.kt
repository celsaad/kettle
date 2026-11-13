package com.casco.kettle.theme

/** `:app`'s namespace, whose `R` the generated families' font resources are in. */
private const val APP_PACKAGE = "com.casco.kettle.shell"

/** Where the generated file lives in `:app`, as its package and as the path under a source root. */
const val OUTPUT_PACKAGE = "$APP_PACKAGE.theme"

/**
 * theme.json as one Kotlin file: `Colors`' two schemes as `LightColors` and `DarkColors`,
 * `RunnerColors` as an object holding a `RunnerPalette` per field, `Spacing` and `MaxContentWidth` in
 * dp, `Fonts` as a `FontFamily` per typeface, and `Type` as `KettleType`'s text styles in sp, each in
 * its role's family.
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
        appendLine("import androidx.compose.ui.text.font.Font")
        appendLine("import androidx.compose.ui.text.font.FontFamily")
        appendLine("import androidx.compose.ui.text.font.FontWeight")
        appendLine("import androidx.compose.ui.unit.dp")
        appendLine("import androidx.compose.ui.unit.sp")
        appendLine("import $APP_PACKAGE.R")
        appendLine()
        appendSchemes(theme.light, theme.dark)
        appendRunner(theme.runner)
        appendObject("Spacing", theme.spacing) { "$it.dp" }
        appendLine("val MaxContentWidth = ${theme.maxContentWidth}.dp")
        appendLine()
        appendFamilies(fontFaces(theme))
        appendLine("/** `Type`: ThemedText's types, each in its `Fonts` role's family and weight. */")
        appendObject("KettleType", theme.type) { textStyle(it, theme.fonts.getValue(it.font)) }
    }
}

/**
 * Every face `Fonts` names, once each, as the shell's resources: what `main` copies into `res/font`,
 * and the generated families point at.
 */
fun fontFaces(theme: Theme): List<Face> = theme.fonts.values.distinct()

/**
 * A `FontFamily` per typeface, holding each of its faces lightest first, so a `fontWeight` set on top of
 * a role's style in code picks a bundled face where there is one rather than a synthesised one.
 */
private fun StringBuilder.appendFamilies(faces: List<Face>) {
    appendLine("/** `Fonts`' typefaces, each a family of the faces theme.json names in it. */")
    val families = faces.groupBy { it.family }
    for ((index, entry) in families.entries.withIndex()) {
        if (index > 0) appendLine()
        appendLine("private val ${entry.key} =")
        appendLine("    FontFamily(")
        for (face in entry.value.sortedBy { it.weight }) {
            appendLine("        Font(R.font.${face.resource}, FontWeight.${face.weightName}),")
        }
        appendLine("    )")
    }
    appendLine()
}

/** A `TextStyle` call on one line, negative spacing parenthesised as Kotlin needs before `.sp`. */
private fun textStyle(
    style: TypeStyle,
    face: Face,
): String {
    val sp = { value: String -> if (value.startsWith("-")) "($value).sp" else "$value.sp" }
    val arguments =
        listOfNotNull(
            "fontFamily = ${face.family}",
            "fontWeight = FontWeight.${face.weightName}",
            style.fontSize?.let { "fontSize = ${sp(it)}" },
            style.lineHeight?.let { "lineHeight = ${sp(it)}" },
            style.letterSpacing?.let { "letterSpacing = ${sp(it)}" },
        )
    return "TextStyle(${arguments.joinToString()})"
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

internal fun StringBuilder.appendTokens(keys: Set<String>) {
    appendLine("    /** Every token by its theme.json name, in file order. */")
    appendLine("    internal val tokens =")
    appendLine("        listOf(")
    for (key in keys) appendLine("            \"$key\" to $key,")
    appendLine("        )")
}
