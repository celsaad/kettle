package com.casco.kettle.shell.files

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MergeResult
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.libraryWeekKey
import com.casco.kettle.domain.mergeLibraries
import com.casco.kettle.domain.parseLibraryYaml
import com.casco.kettle.domain.programWeekNumbers
import com.casco.kettle.domain.serializeLibraryYaml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the seed and every pack owe alike, in **every language**: a translated library is a real one a
 * real user lands on, so a table that drops a name or reorders a day label breaks that language only,
 * and an English run would keep passing. Each test collects every failure as `<library> <language>:
 * <what>` before asserting, so one run names them all.
 */
@RunWith(RobolectricTestRunner::class)
class ShippedLibrariesTest {
    private val shipped = ShippedLibraries()

    /** One library in one UI language, the way the app reads it, beside its English structure. */
    private data class Reading(
        val name: String,
        val language: String,
        val library: Library,
        val english: Library,
    )

    private fun everyReading(): List<Reading> =
        shipped.all.flatMap { library ->
            val english = library.read("en")
            shipped.languages.map { Reading(library.name, it, library.read(it), english) }
        }

    @Test
    fun readsEveryLibraryInEveryLanguage() {
        // The guard every test below leans on: a list that came back empty would make each one pass.
        assertTrue(shipped.packs.isNotEmpty())
        assertTrue("en" in shipped.languages && shipped.languages.size > 1)
    }

    @Test
    fun roundTripsThroughTheYamlLayerUnchanged() {
        // A library that doesn't survive its own serialize/parse doesn't fail once: the seed reseeds on
        // every launch, and a merged pack reads back as the user's own corrupt file.
        val failures =
            everyReading().mapNotNull { (name, language, library) ->
                val back = parseLibraryYaml(serializeLibraryYaml(library))
                if (back == ParseResult.Ok(library)) null else "$name $language: $back"
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun resolvesEveryReferenceItMakes() {
        // Merging into an empty library validates references against the library alone.
        val empty = Library(1, emptyList(), emptyList(), emptyList())
        val failures =
            everyReading().mapNotNull { (name, language, library) ->
                (mergeLibraries(empty, library) as? MergeResult.Refused)?.let { "$name $language: ${it.error}" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun leavesNoExerciseUnreachableFromAWorkout() {
        // Browsable but not runnable: in curated content, an exercise added and left out of its session.
        val failures =
            everyReading().flatMap { (name, language, library) ->
                library.exercises.filter { it.id !in library.usedExerciseIds }.map { "$name $language: ${it.id}" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun namesEverythingItShows() {
        // An unnamed exercise renders as a blank row.
        val failures =
            everyReading().flatMap { (name, language, library) ->
                val names =
                    library.exercises.map { it.id to it.name } + library.workouts.map { it.id to it.name } +
                        library.programs.map { it.id to it.name }
                names.filter { it.second.isBlank() }.map { "$name $language: ${it.first}" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun tagsEquipmentWithKnownKeysOnly() {
        // Shipped content reads in every language only through known items: an item spelled any other
        // way, `Dumbbell` included, is shown as written, English in a Japanese library.
        val known = shipped.content.knownEquipment().toSet()
        val failures =
            everyReading().flatMap { (name, language, library) ->
                library.exercises.flatMap { exercise ->
                    exercise.equipment.filter { it !in known }.map { "$name $language: ${exercise.id} $it" }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun numbersEveryProgramsWeeksWithNoGap() {
        // Weeks resolve sparsely: a missing week 2 is skipped, not filled in from week 1.
        val failures =
            everyReading().flatMap { (name, language, library) ->
                library.programs
                    .filter { program ->
                        programWeekNumbers(program) != (1L..programWeekNumbers(program).size).toList()
                    }.map { "$name $language: ${it.id} ${programWeekNumbers(it)}" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun walksEveryProgramsWeeksInTrainingOrder() {
        // Home's rotation (`NextUp.kt`) runs the weeks sorted by number with a stable sort and finds its
        // place by week and day, so file order is training order exactly when the numbers never go
        // down and no two weeks share a week and day: in every language, since a translation could
        // give two days one label.
        val failures =
            everyReading().flatMap { (name, language, library) ->
                library.programs
                    .filter { program ->
                        val numbers = program.weeks.map { it.week }
                        numbers != numbers.sorted() ||
                            program.weeks
                                .map { it.week to it.day }
                                .toSet()
                                .size != numbers.size
                    }.map { "$name $language: ${it.id}" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun holdsLibraryYamlAndOneTableForEveryOtherLanguage() {
        // The app reads whatever tables the directory holds, so a language with no table falls back to
        // English silently: a translated app around an English library. A table for a language the app
        // has no UI for is never read.
        val expected = listOf("library.yaml") + shipped.languages.filter { it != "en" }.map { "$it.json" }
        val failures =
            shipped.all.mapNotNull { library ->
                shipped.files(library.dir).takeIf { it != expected.sorted() }?.let { "${library.name}: $it" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun translatesEveryNameNoteAndDayLabel() {
        // A content edit nobody translated, named here rather than shipped as a half-English library. A
        // blank string counts as missing: localize applies it as it is, so the reader sees nothing.
        val failures =
            shipped.all.flatMap { library ->
                val english = library.read("en")
                shipped.tables(library).flatMap { (language, table) ->
                    val missing =
                        english.exercises.filter { it.id !in table.exercises }.map { "exercise ${it.id}" } +
                            english.workouts.filter { it.id !in table.workouts }.map { "workout ${it.id}" } +
                            english.programs.filter { it.id !in table.programs }.map { "program ${it.id}" } +
                            english.exercises
                                .filter { it.notes != null && table.exercises[it.id]?.notes.isNullOrBlank() }
                                .map { "notes of ${it.id}" } +
                            english.programs.flatMap { program ->
                                program.weeks
                                    .filter { week ->
                                        val key = libraryWeekKey(week.week, week.day)
                                        week.notes != null &&
                                            table.programs[program.id]
                                                ?.weekNotes
                                                ?.get(key)
                                                .isNullOrBlank()
                                    }.map { "notes of ${program.id} ${libraryWeekKey(it.week, it.day)}" }
                            } +
                            english.days().filter { table.days[it].isNullOrBlank() }.map { "day $it" }
                    missing.map { "${library.name} $language: $it" }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun keepsNoTableKeyForContentThatIsGone() {
        // The direction a rename breaks: a key for an id that no longer exists silently stops applying,
        // and the language quietly reverts to English for it.
        val failures =
            shipped.all.flatMap { library ->
                val english = library.read("en")
                shipped.tables(library).flatMap { (language, table) ->
                    val exercises = english.exercises.map { it.id }.toSet()
                    val workouts = english.workouts.map { it.id }.toSet()
                    val programs =
                        english.programs.associate { p ->
                            p.id to
                                p.weeks.map { libraryWeekKey(it.week, it.day) }.toSet()
                        }
                    val stale =
                        (table.exercises.keys - exercises).map { "exercise $it" } +
                            (table.workouts.keys - workouts).map { "workout $it" } +
                            (table.programs.keys - programs.keys).map { "program $it" } +
                            (table.days.keys - english.days()).map { "day $it" } +
                            table.programs.flatMap { (id, strings) ->
                                strings.weekNotes
                                    .orEmpty()
                                    .keys
                                    .filter { it !in programs[id].orEmpty() }
                                    .map { "notes of $id $it" }
                            }
                    stale.map { "${library.name} $language: $it" }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun changesNothingButTheStrings() {
        // Structure stays single-sourced in library.yaml; a table that could move a config, a block or a
        // rest day would make each language a different program.
        val failures =
            everyReading().mapNotNull { reading ->
                reading.takeIf { it.library.structure() != it.english.structure() }?.let { "${it.name} ${it.language}" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun readsEveryTableItShipsInItsOwnLanguage() {
        // The other half of the test above: that the table is applied at all. A reader that ignored the
        // language would pass every structure, parity and fallback test, and ship English everywhere.
        val failures =
            everyReading()
                .filter { it.language != "en" }
                .filter { reading ->
                    reading.library.exercises.map { it.name } ==
                        reading.english.exercises.map { it.name }
                }.map { "${it.name} ${it.language}" }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun fallsBackToEnglishAndReadsARegionalTagAsItsLanguage() {
        // A device in a language with no table gets English; a Brazilian one hands over `pt-BR`.
        val failures =
            shipped.all.flatMap { library ->
                listOfNotNull(
                    "de".takeIf { library.read("de") != library.read("en") },
                    "pt-BR".takeIf { library.read("pt-BR") != library.read("pt") },
                ).map { "${library.name} $it" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    private fun Library.days(): Set<String> =
        programs.flatMap { program -> program.weeks.mapNotNull { it.day } }.toSet()
}
