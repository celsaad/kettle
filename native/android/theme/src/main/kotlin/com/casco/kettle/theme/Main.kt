package com.casco.kettle.theme

import java.io.File

private val USAGE = listOf("<theme.json>", "<output source dir>")

/**
 * theme.json written as one Kotlin source file under the output, at the path its package names. The
 * output is cleared first, so nothing an earlier generator wrote can linger beside it.
 */
fun main(args: Array<String>) {
    require(args.size == USAGE.size) { "usage: ${USAGE.joinToString(" ")}" }
    val (theme, output) = args
    val kotlin = generateTheme(File(theme).readText())

    val out = File(output)
    out.deleteRecursively()
    File(out, OUTPUT_PACKAGE.replace('.', '/')).apply { mkdirs() }.resolve("GeneratedTheme.kt").writeText(kotlin)
}
