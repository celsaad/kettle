package com.casco.kettle.domain

/**
 * Everything an assistant needs to emit YAML this app will accept (#244), as RN's
 * `assistant-brief.ts`: the format, and the ids already in [library]. Handed out by the share sheet or
 * the clipboard; the app never talks to a model itself.
 *
 * **This carries format and never intent.** No goals, no prescriptions, nothing about the user's body:
 * the app owns the file format, and the user owns what goes in the file (the decision log).
 *
 * [schema] is `src/content/library-schema.json`, generated from the validator and held to it by
 * `library-schema-file.test.ts`, so it can't drift from what the importer accepts.
 */
object AssistantBrief {
    /** The brief's own words, in the reader's language: the screen's to supply, since `:shared` holds no prose. */
    data class Words(
        val prompt: String,
        val inventoryHeading: String,
        val exercisesLabel: String,
        val workoutsLabel: String,
        val programsLabel: String,
        val none: String,
        val schemaHeading: String,
    )

    fun build(
        library: Library,
        words: Words,
        schema: String,
    ): String = listOf(words.prompt, inventory(library, words), schemaSection(words, schema)).joinToString("\n\n")
}

/**
 * The user's own ids, the half a schema can't supply: a generated program only lands if the ids it
 * references exist, and an assistant that can't see the library invents them. Names are user data and
 * go in verbatim, untranslated.
 */
private fun inventory(
    library: Library,
    words: AssistantBrief.Words,
): String =
    (
        listOf(words.inventoryHeading) +
            section(
                words.exercisesLabel,
                library.exercises.map { "  ${it.id} (${it.config.type.fileName}) — ${it.name}" },
                words,
            ) +
            section(words.workoutsLabel, library.workouts.map { "  ${it.id} — ${it.name}" }, words) +
            section(words.programsLabel, library.programs.map { "  ${it.id} — ${it.name}" }, words)
    ).joinToString("\n")

/** A labelled list, or the label and an explicit "none": a silent gap reads as a copy that failed. */
private fun section(
    label: String,
    entries: List<String>,
    words: AssistantBrief.Words,
): List<String> = listOf(label) + entries.ifEmpty { listOf("  ${words.none}") }

private fun schemaSection(
    words: AssistantBrief.Words,
    schema: String,
): String = listOf(words.schemaHeading, "```json", schema.trimEnd('\n'), "```").joinToString("\n")
