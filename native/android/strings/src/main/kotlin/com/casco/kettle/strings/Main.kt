package com.casco.kettle.strings

import java.io.File

private val USAGE = listOf("<locales dir>", "<output res dir>")

/**
 * Every `<language>.json` in the locales directory, written as `values/strings.xml`,
 * `values-pt/strings.xml` and so on under the output. The output is cleared first, so a dropped
 * language can't linger from an earlier build.
 */
fun main(args: Array<String>) {
    require(args.size == USAGE.size) { "usage: ${USAGE.joinToString(" ")}" }
    val (locales, output) = args
    val bundles =
        File(locales)
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .associate { it.nameWithoutExtension to it.readText() }

    val out = File(output)
    out.deleteRecursively()
    for ((dir, xml) in generateResources(bundles)) {
        File(out, dir).apply { mkdirs() }.resolve("strings.xml").writeText(xml)
    }
}
