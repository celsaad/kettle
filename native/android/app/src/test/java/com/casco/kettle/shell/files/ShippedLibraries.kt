package com.casco.kettle.shell.files

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.LibraryTranslation
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.parseLibraryTranslation
import org.json.JSONObject
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The libraries the app ships, read as it reads them, for the tests that hold their content: the seed
 * a first launch lands on, and every pack Import offers. Both are written into somebody's library
 * without ever passing through the import path's checks, so a content edit that breaks one fails in
 * the app rather than where it was made, unless these catch it.
 */
internal class ShippedLibraries {
    private val assets = RuntimeEnvironment.getApplication().assets
    val content = ShippedContent(assets)

    /** One shipped library: [dir] under `content/`, and how the app reads it in a language. */
    class Shipped(
        val name: String,
        val dir: String,
        val read: (String?) -> Library,
    )

    val seed = Shipped("seed", "seed") { content.seed(it) }

    val packs = content.packs().map { pack -> Shipped(pack.id, "packs/${pack.id}") { content.pack(pack.id, it) } }

    val all = listOf(seed) + packs

    /** The app's UI languages, one locale bundle each; every shipped library carries a table for each but English. */
    val languages: List<String> = localeBundles().keys.sorted()

    /** The locale bundles by language, read in place from the repo the way `:strings` reads them. */
    fun localeBundles(): Map<String, JSONObject> =
        File(checkNotNull(System.getProperty("kettle.localesDir")) { "set by Gradle" })
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .associate { it.nameWithoutExtension to JSONObject(it.readText()) }

    fun files(dir: String): List<String> = assets.list(dir).orEmpty().sorted()

    /** [library]'s translation tables, as the files hold them, by language. */
    fun tables(library: Shipped): Map<String, LibraryTranslation> =
        files(library.dir).filter { it.endsWith(".json") }.associate { file ->
            file.removeSuffix(".json") to
                parseLibraryTranslation(assets.open("${library.dir}/$file").bufferedReader().use { it.readText() })
        }
}

internal val Library.ids: List<String>
    get() = exercises.map { it.id } + workouts.map { it.id } + programs.map { it.id }

internal val Library.usedExerciseIds: Set<String>
    get() =
        workouts
            .flatMap { it.blocks }
            .flatMap { block ->
                when (block) {
                    is WorkoutBlock.ExerciseBlock -> listOf(block.exerciseId)
                    is WorkoutBlock.Circuit -> block.members.map { it.exerciseId }
                }
            }.toSet()

/**
 * [this] with every string a translation may change blanked: names, notes and day labels. What is
 * left is what a translation must leave alone, ids, types, configs, blocks, week numbers, rest days
 * and overrides, so a field added later is held to that by default rather than by remembering to.
 */
internal fun Library.structure(): Library =
    copy(
        exercises = exercises.map { it.copy(name = "", notes = null) },
        workouts = workouts.map { it.copy(name = "") },
        programs =
            programs.map { program ->
                program.copy(
                    name = "",
                    weeks =
                        program.weeks.map { week ->
                            when (week) {
                                is ProgramWeek.Training -> week.copy(day = null, notes = null)
                                is ProgramWeek.RestDay -> week.copy(day = null, notes = null)
                            }
                        },
                )
            },
    )
