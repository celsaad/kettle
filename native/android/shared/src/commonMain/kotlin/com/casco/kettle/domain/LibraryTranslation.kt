package com.casco.kettle.domain

/**
 * A per-language string table for a library the app ships: `LibraryTranslation` in
 * `src/storage/library-translation.ts`, which holds the reasoning, held to it by
 * `conformance/localize.yaml`. The tables are the `<language>.json` files beside each library in
 * `src/content/`.
 *
 * [days] maps an English day label to its replacement, shared by every program in the library.
 * Program week notes are keyed by [libraryWeekKey], since a week has no id.
 */
data class LibraryTranslation(
    val days: Map<String, String>,
    val exercises: Map<String, ExerciseStrings>,
    val workouts: Map<String, String>,
    val programs: Map<String, ProgramStrings>,
)

data class ExerciseStrings(
    val name: String,
    val notes: String? = null,
)

data class ProgramStrings(
    val name: String,
    val weekNotes: Map<String, String>? = null,
)

/** A program week's key in a table: its number and its **English** day label, `1|Day 1`, or `2|`. */
fun libraryWeekKey(
    week: Long,
    day: String?,
): String = "$week|${day.orEmpty()}"

/**
 * [library] with [strings] applied, or unchanged when there is no table. Only names, notes and day
 * labels change. A missing `notes` keeps the English one rather than dropping it, and a program with
 * no entry keeps its English day labels too, since days are applied per program in the table.
 */
fun localizeLibrary(
    library: Library,
    strings: LibraryTranslation?,
): Library {
    if (strings == null) return library
    return library.copy(
        exercises = library.exercises.map { it.localized(strings) },
        workouts = library.workouts.map { it.copy(name = strings.workouts[it.id] ?: it.name) },
        programs = library.programs.map { it.localized(strings) },
    )
}

private fun Exercise.localized(strings: LibraryTranslation): Exercise {
    val text = strings.exercises[id] ?: return this
    return copy(name = text.name, notes = text.notes ?: notes)
}

private fun Program.localized(strings: LibraryTranslation): Program {
    val text = strings.programs[id] ?: return this
    return copy(
        name = text.name,
        weeks =
            weeks.map { week ->
                val day = week.day?.let { strings.days[it] ?: it }
                val notes = text.weekNotes?.get(libraryWeekKey(week.week, week.day)) ?: week.notes
                when (week) {
                    is ProgramWeek.Training -> week.copy(day = day, notes = notes)
                    is ProgramWeek.RestDay -> week.copy(day = day, notes = notes)
                }
            },
    )
}

/**
 * The table for a BCP 47 tag like `pt-BR`, narrowed to its base subtag. Keyed by language, not
 * region, exactly as the locale bundles are. Pass `Locale.toLanguageTag()`, not `toString()`: its
 * `pt_BR` has no `-`, so it would find no table and fall back to English, as the TS does.
 */
fun translationFor(
    tables: Map<String, LibraryTranslation>,
    language: String?,
): LibraryTranslation? = tables[language.orEmpty().split('-').first()]

/**
 * A shipped `<language>.json` table. Read with the YAML loader, since JSON is YAML 1.2, so `:shared`
 * needs no JSON library. **Throws** on a table that isn't one: it is a file the app ships, so a bad one
 * is a build defect with nothing honest to degrade to, and the RN tests read every one.
 */
fun parseLibraryTranslation(text: String): LibraryTranslation {
    val loaded = loadYaml(text)
    require(loaded is YamlLoad.Loaded) { "translation table is not JSON: ${(loaded as YamlLoad.Invalid).detail}" }
    return libraryTranslationOf(loaded.value)
}

/** A table from an already-loaded tree of maps, as a conformance case holds one inline. */
internal fun libraryTranslationOf(tree: Any?): LibraryTranslation {
    val fields = tree.asMap("table")
    return LibraryTranslation(
        days = fields["days"].asStrings("days"),
        exercises =
            fields["exercises"].asMap("exercises").entries.associate { (id, value) ->
                val entry = value.asMap("exercises.$id")
                id as String to
                    ExerciseStrings(
                        requireNotNull(entry.string("exercises.$id.name")) {
                            "exercises.$id.name is missing"
                        },
                        entry.string("exercises.$id.notes"),
                    )
            },
        workouts = fields["workouts"].asStrings("workouts"),
        programs =
            fields["programs"].asMap("programs").entries.associate { (id, value) ->
                val entry = value.asMap("programs.$id")
                id as String to
                    ProgramStrings(
                        requireNotNull(entry.string("programs.$id.name")) { "programs.$id.name is missing" },
                        entry["weekNotes"]?.asStrings("programs.$id.weekNotes"),
                    )
            },
    )
}

/** The field at the last segment of [path], which must be a string where present. */
private fun Map<*, *>.string(path: String): String? =
    this[path.substringAfterLast('.')]?.let { requireNotNull(it as? String) { "$path is not a string" } }

private fun Any?.asMap(path: String): Map<*, *> = requireNotNull(this as? Map<*, *>) { "$path is not an object" }

private fun Any?.asStrings(path: String): Map<String, String> =
    asMap(path).entries.associate { (key, value) -> key as String to requireNotNull(value as? String) { "$path.$key" } }
