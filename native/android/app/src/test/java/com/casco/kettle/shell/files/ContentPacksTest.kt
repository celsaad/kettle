package com.casco.kettle.shell.files

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MergeResult
import com.casco.kettle.domain.MergeSummary
import com.casco.kettle.domain.mergeLibraries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a pack owes beyond [ShippedLibrariesTest]: it is written into a library somebody already has,
 * by merge, which replaces whole objects by id. So it must add and never replace, and the three places
 * that name a pack must agree on which ones exist.
 */
@RunWith(RobolectricTestRunner::class)
class ContentPacksTest {
    private val shipped = ShippedLibraries()
    private val entries = shipped.content.packs()

    @Test
    fun prefixesEveryIdItShips() {
        // The whole safety argument for merging into a library edited for months: an unprefixed id is
        // a silent overwrite of somebody's work. Checked against the declared prefix, not the content.
        val failures =
            entries.flatMap { pack ->
                shipped.content
                    .pack(pack.id, "en")
                    .ids
                    .filterNot { it.startsWith(pack.idPrefix) }
                    .map { "${pack.id}: $it" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun collidesWithNothingInTheSeed() {
        // What the prefix bought: a fresh install merging the pack adds, and never replaces.
        val seeded =
            shipped.seed
                .read("en")
                .ids
                .toSet()
        val failures =
            shipped.packs.flatMap { pack ->
                pack
                    .read("en")
                    .ids
                    .filter { it in seeded }
                    .map { "${pack.name}: $it" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun mergesIntoTheSeedAddingAndReplacingNothingInEveryLanguage() {
        // What Import does on a tap, and the one check of the two halves together: references resolve
        // against the merged whole, which is what catches a pack reaching for the seed's `rest`.
        val failures =
            shipped.packs.flatMap { pack ->
                shipped.languages.mapNotNull { language ->
                    replaced(shipped.seed.read(language), pack.read(language))?.let { "${pack.name} $language: $it" }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun mergesAllTogetherWithNoPackReplacingAnother() {
        // Nothing stops somebody taking every pack, and two sharing an id would only show up then.
        var library = shipped.seed.read("en")
        val failures = mutableListOf<String>()
        for (pack in shipped.packs) {
            val incoming = pack.read("en")
            replaced(library, incoming)?.let { failures += "${pack.name}: $it" }
            library = (mergeLibraries(library, incoming) as? MergeResult.Ok)?.library ?: library
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun givesEveryPackADistinctIdAndPrefix() {
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertEquals(entries.size, entries.map { it.idPrefix }.toSet().size)
    }

    @Test
    fun listsExactlyThePackDirectoriesInPacksJson() {
        // The app offers what packs.json lists and reads what the directory holds: a pack in one and
        // not the other is either never offered or offered and unreadable.
        // Robolectric lists the files under each directory where a device lists the directories.
        val directories = shipped.files("packs").map { it.substringBefore('/') }.distinct()
        assertTrue("packs.json lists no pack", entries.isNotEmpty())
        assertEquals(directories, entries.map { it.id }.sorted())
    }

    @Test
    fun givesEveryPackAnImportRowAndNoRowToAPackThatIsGone() {
        // The row's text is UI chrome, not pack data, so it lives in the bundles under `import.packs`.
        // English alone: :strings holds every other bundle to English key for key.
        val rows =
            shipped
                .localeBundles()
                .getValue("en")
                .getJSONObject("import")
                .getJSONObject("packs")
        // A row is an object; the screen's own strings (its title, the counts) sit beside them.
        val rowIds =
            rows
                .keys()
                .asSequence()
                .filter { rows.optJSONObject(it) != null }
                .toSet()
        assertEquals(entries.map { it.id }.toSet(), rowIds)
        val failures =
            entries.filterNot { pack ->
                val row = rows.getJSONObject(pack.id)
                row.optString("name").isNotBlank() && row.optString("detail").isNotBlank()
            }
        assertEquals(emptyList<Any>(), failures.map { it.id })
    }

    /** Why merging [incoming] into [existing] isn't a pure addition, or null when it is. */
    private fun replaced(
        existing: Library,
        incoming: Library,
    ): String? =
        when (val result = mergeLibraries(existing, incoming)) {
            is MergeResult.Refused -> {
                "refused: ${result.error}"
            }

            is MergeResult.Ok -> {
                result.summary
                    .updated()
                    .takeIf { it.isNotEmpty() }
                    ?.let { "replaces $it" }
            }
        }

    private fun MergeSummary.updated() = updatedExercises + updatedWorkouts + updatedPrograms
}
