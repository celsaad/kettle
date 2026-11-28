package com.casco.kettle.repochecks

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MergeResult
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.mergeLibraries
import com.casco.kettle.domain.parseLibraryYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The files in `site/examples/` are complete libraries published for readers to import as they are:
 * the one thing on the site that isn't prose about the format but the format itself, handed over whole.
 * Parsing alone is the corpus's (`conformance/library.yaml`'s `source:` cases); what is held here is
 * what a reader's import does next, in the order it would bite them:
 *
 * - each file imports into a library that already has the seed, since parsing doesn't check
 *   references and a typo'd `exercise:` is refused only when the merged whole is validated;
 * - and it replaces nothing there, nor in another example. Every example prefixes its ids so that
 *   importing one can never overwrite what a reader has, and the examples page says so as a promise.
 */
class SiteExamplesTest {
    private val files =
        File(repoRoot, "site/examples")
            .listFiles { file -> file.extension == "yaml" }
            .orEmpty()
            .sortedBy { it.name }

    private fun library(file: File): Library =
        when (val result = parseLibraryYaml(file.readText())) {
            is ParseResult.Ok -> result.value
            else -> error("${file.name} does not parse: $result")
        }

    @Test
    fun `imports each example into the seed, replacing nothing`() {
        assertTrue(files.isNotEmpty(), "found no examples to check")
        val failures = files.mapNotNull { file -> replaced(seed, library(file))?.let { "${file.name}: $it" } }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `lets every example be imported together, none replacing another`() {
        var library = seed
        val failures = mutableListOf<String>()
        for (file in files) {
            val incoming = library(file)
            replaced(library, incoming)?.let { failures += "${file.name}: $it" }
            library = (mergeLibraries(library, incoming) as? MergeResult.Ok)?.library ?: library
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `tags equipment with known keys only`() {
        // A known item reads in the importer's language; any other spelling is shown exactly as written,
        // so a published example tagged `Dumbbell` would stay English for a Japanese reader.
        val known = vocabulary.toSet()
        val failures =
            files.flatMap { file ->
                library(file).exercises.flatMap { exercise ->
                    exercise.equipment.filter { it !in known }.map { "${file.name}: ${exercise.id} $it" }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    /**
     * The page and the directory are kept by hand in different files: an example added without a card
     * is published but unreachable, and a card pointing at a renamed file is a 404 on both its buttons.
     */
    @Test
    fun `is exactly what examples html offers`() {
        val offered =
            Regex("""examples/([\w-]+\.yaml)""")
                .findAll(repoText("site/examples.html"))
                .map { it.groupValues[1] }
                .toSortedSet()
                .toList()
        assertEquals(files.map { it.name }, offered)
    }

    private fun replaced(
        existing: Library,
        incoming: Library,
    ): String? =
        when (val result = mergeLibraries(existing, incoming)) {
            is MergeResult.Refused -> {
                "refused: ${result.error}"
            }

            is MergeResult.Ok -> {
                val summary = result.summary
                (summary.updatedExercises + summary.updatedWorkouts + summary.updatedPrograms)
                    .takeIf { it.isNotEmpty() }
                    ?.let { "replaces $it" }
            }
        }
}
