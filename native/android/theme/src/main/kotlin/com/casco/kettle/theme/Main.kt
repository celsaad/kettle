package com.casco.kettle.theme

import java.io.File

private val USAGE = listOf("<theme.json>", "<@expo-google-fonts dir>", "<output source dir>", "<output res dir>")

/**
 * theme.json written as one Kotlin source file under the source output, at the path its package names,
 * and each face its `Fonts` names copied out of RN's `@expo-google-fonts` packages into the res
 * output's `font/`. Both outputs are cleared first, so nothing an earlier generator wrote can linger
 * beside them.
 *
 * The faces are read from `node_modules` rather than kept as a second copy here, so RN's package
 * version is the shell's too (see the decision log).
 */
fun main(args: Array<String>) {
    require(args.size == USAGE.size) { "usage: ${USAGE.joinToString(" ")}" }
    // In USAGE's order, which `:app`'s generateTheme task passes them in. Two pairs, since detekt
    // allows three entries to a destructuring.
    val (themeFile, fonts) = args
    val (source, res) = args.drop(2)
    val json = File(themeFile).readText()
    val kotlin = generateTheme(json)
    val faces = fontFaces(parseTheme(json)).map { File(fonts, it.path) to it }
    val missing = faces.filterNot { it.first.isFile }.map { it.first.path }
    require(missing.isEmpty()) {
        "theme.json's Fonts aren't installed; run pnpm install at the repo root. Missing: $missing"
    }

    File(source).deleteRecursively()
    File(source, OUTPUT_PACKAGE.replace('.', '/')).apply { mkdirs() }.resolve("GeneratedTheme.kt").writeText(kotlin)
    val fontDir = File(res).apply { deleteRecursively() }.resolve("font").apply { mkdirs() }
    for ((file, face) in faces) file.copyTo(fontDir.resolve("${face.resource}.ttf"))
}
