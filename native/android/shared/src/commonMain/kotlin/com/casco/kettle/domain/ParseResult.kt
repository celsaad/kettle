package com.casco.kettle.domain

/**
 * What reading one of the app's files gives back: the value, or why it was refused, as
 * `ParseResult` in `src/domain/yaml-mapping.ts`.
 *
 * `paths` is the part of a refusal both implementations are held to (`conformance/README.md`). The
 * `detail` beside it is this port's own wording and not compared with anything, which is why it is
 * kept apart rather than built into the paths.
 */
sealed interface ParseResult<out T> {
    data class Ok<out T>(
        val value: T,
    ) : ParseResult<T>

    /** Not YAML the loader accepts: a syntax error, a duplicate key, too many aliases, no document. */
    data class InvalidYaml(
        val detail: String,
    ) : ParseResult<Nothing>

    /**
     * YAML, but not the file's shape. [paths] are the refused fields in dotted form
     * (`entries.0.sets.0.rpe`, or `(root)` for the document), de-duplicated and sorted.
     */
    data class SchemaMismatch(
        val paths: List<String>,
        val detail: String,
    ) : ParseResult<Nothing>
}

/**
 * Loads [text] and hands the tree to [read], the file's schema. The schema reports every refused
 * field rather than stopping at the first, so the paths are the whole list, as zod's are.
 */
internal fun <T : Any> parseYaml(
    text: String,
    read: SchemaReader.(Any?) -> T?,
): ParseResult<T> =
    when (val loaded = loadYaml(text)) {
        is YamlLoad.Invalid -> {
            ParseResult.InvalidYaml(loaded.detail)
        }

        is YamlLoad.Loaded -> {
            val reader = SchemaReader()
            val value = reader.read(loaded.value)
            reader.result(value)
        }
    }
