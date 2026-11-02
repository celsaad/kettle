package com.casco.kettle.art

import java.io.File

private val USAGE = listOf("<art.json>", "<art dir>", "<output res dir>", "<output source dir>")

/**
 * Every `.svg` in the art directory written as `drawable/art_<drawing>.xml` under the res output, and
 * the id lookup as one Kotlin file under the source output. Both outputs are cleared first, so a
 * deleted drawing can't linger from an earlier build. Other files there (the README) are not art.
 */
fun main(args: Array<String>) {
    require(args.size == USAGE.size) { "usage: ${USAGE.joinToString(" ")}" }
    val (inputs, outputs) = args.toList().chunked(2)
    val (artJson, artDir) = inputs
    val (resOutput, sourceOutput) = outputs
    val svgs =
        File(artDir)
            .listFiles { file -> file.extension == "svg" }
            .orEmpty()
            .sortedBy { it.name }
            .associate { it.nameWithoutExtension to it.readText() }
    val art = generateArt(File(artJson).readText(), svgs)

    val drawables = File(resOutput).apply { deleteRecursively() }.resolve("drawable").apply { mkdirs() }
    for ((name, xml) in art.drawables) drawables.resolve("$name.xml").writeText(xml)
    val source = File(sourceOutput).apply { deleteRecursively() }
    File(source, OUTPUT_PACKAGE.replace('.', '/')).apply { mkdirs() }.resolve("ArtDrawables.kt").writeText(art.lookup)
}
