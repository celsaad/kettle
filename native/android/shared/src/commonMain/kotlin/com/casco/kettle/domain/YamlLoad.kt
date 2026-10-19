package com.casco.kettle.domain

import it.krzeminski.snakeyaml.engine.kmp.api.ConstructNode
import it.krzeminski.snakeyaml.engine.kmp.api.Dump
import it.krzeminski.snakeyaml.engine.kmp.api.DumpSettings
import it.krzeminski.snakeyaml.engine.kmp.api.Load
import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Parse
import it.krzeminski.snakeyaml.engine.kmp.common.FlowStyle
import it.krzeminski.snakeyaml.engine.kmp.constructor.ConstructYamlNull
import it.krzeminski.snakeyaml.engine.kmp.constructor.core.ConstructYamlCoreBool
import it.krzeminski.snakeyaml.engine.kmp.constructor.core.ConstructYamlCoreFloat
import it.krzeminski.snakeyaml.engine.kmp.constructor.core.ConstructYamlCoreInt
import it.krzeminski.snakeyaml.engine.kmp.events.AliasEvent
import it.krzeminski.snakeyaml.engine.kmp.events.CollectionEndEvent
import it.krzeminski.snakeyaml.engine.kmp.events.CollectionStartEvent
import it.krzeminski.snakeyaml.engine.kmp.events.DocumentStartEvent
import it.krzeminski.snakeyaml.engine.kmp.events.ScalarEvent
import it.krzeminski.snakeyaml.engine.kmp.exceptions.YamlEngineException
import it.krzeminski.snakeyaml.engine.kmp.nodes.Tag
import it.krzeminski.snakeyaml.engine.kmp.resolver.BaseScalarResolver
import it.krzeminski.snakeyaml.engine.kmp.resolver.CoreScalarResolver
import it.krzeminski.snakeyaml.engine.kmp.resolver.ScalarResolver
import it.krzeminski.snakeyaml.engine.kmp.schema.Schema

/**
 * The YAML loader both parsers share: snakeyaml-engine-kmp, the multiplatform port of
 * snakeyaml-engine, held to what `js-yaml` 5 does in `src/domain/yaml-mapping.ts`. Those are the
 * rules in `conformance/README.md`, and each departure from the engine's defaults below is one of
 * them.
 *
 * Why this library: it is a YAML 1.2 loader, so the core schema is native rather than bolted on, and
 * it builds a plain tree of maps, lists and scalars for the validator to walk. A type-directed
 * decoder (kaml, or kotlinx.serialization over any parser) reads each scalar as the field's type,
 * which accepts an unquoted `300` where a string is required. It is also the one library that
 * reaches the iOS targets M8 needs with the same engine, so there is one loader to keep in line with
 * js-yaml, not two.
 */
internal sealed interface YamlLoad {
    data class Loaded(
        val value: Any?,
    ) : YamlLoad

    data class Invalid(
        val detail: String,
    ) : YamlLoad
}

internal fun loadYaml(text: String): YamlLoad =
    try {
        // A separate pass over the events, before anything is built: an alias bomb is refused while it
        // is still a few hundred bytes, and depth is counted without the composer's recursion.
        checkEvents(text)?.let(YamlLoad::Invalid) ?: YamlLoad.Loaded(Load(loadSettings).loadOne(text))
    } catch (error: YamlEngineException) {
        YamlLoad.Invalid(error.message.orEmpty())
    }

/**
 * Writes a tree of maps, lists and scalars. The layout is not pinned, only that it reads back, so a
 * string that would load as something else (`"300"`, `"yes"`, `"null"`) is quoted by the same
 * resolver the loader uses.
 */
internal fun dumpYaml(value: Any?): String =
    Dump(DumpSettings(schema = YamlCoreSchema, defaultFlowStyle = FlowStyle.BLOCK)).dumpToString(value)

/** js-yaml's `maxAliases`. The engine's own limit counts only aliases to collections. */
private const val MAX_ALIASES = 1000

/**
 * js-yaml's `maxDepth: 100` counts its own parse calls, not nodes, so the depth it allows depends on
 * syntax. Measured under a top-level mapping, which every file the app reads is, it allows 100 nodes
 * deep for flow collections, block mappings and mixed nesting, and 99 for a chain of block
 * sequences. Counting nodes matches all of those but the last. Nobody nests a workout 99 deep; the
 * limit is here so a hostile file can't overflow the composer's recursion.
 */
private const val MAX_DEPTH = 100

private fun checkEvents(text: String): String? {
    var aliases = 0
    var depth = 0
    var deepest = 0
    var sawDocument = false
    // The parser is a state machine, not a recursion, so walking every event of a hostile file is
    // linear in its length and needs no early exit.
    for (event in Parse(loadSettings).parse(text)) {
        when (event) {
            is DocumentStartEvent -> sawDocument = true
            is AliasEvent -> aliases++
            is CollectionStartEvent -> deepest = maxOf(deepest, ++depth)
            is CollectionEndEvent -> depth--
            is ScalarEvent -> deepest = maxOf(deepest, depth + 1)
            else -> Unit
        }
    }
    return when {
        aliases > MAX_ALIASES -> "aliases exceeded $MAX_ALIASES"

        deepest > MAX_DEPTH -> "nesting exceeded $MAX_DEPTH"

        // js-yaml refuses a stream with no document at all, but loads an explicit empty one (`---`)
        // as null, which the schema then refuses at (root). The engine returns null for both.
        !sawDocument -> "expected a document, but the input is empty"

        else -> null
    }
}

/**
 * The YAML 1.2 core schema as js-yaml has it. The engine's `CoreSchema` adds two things js-yaml
 * doesn't do. `<<` merge keys change what a file means (`conformance/session.yaml` has the case).
 * `${NAME}` plain scalars get an environment-variable tag, inert today but read from the process
 * environment the moment anyone sets `envConfig`. Leaving both out of the resolver means no setting
 * can turn them back on.
 */
private object YamlCoreSchema : Schema {
    override val scalarResolver: ScalarResolver = CoreResolver

    override val schemaTagConstructors: Map<Tag, ConstructNode> =
        mapOf(
            Tag.NULL to ConstructYamlNull(),
            Tag.BOOL to ConstructYamlCoreBool(),
            Tag.INT to ConstructYamlCoreInt(),
            Tag.FLOAT to ConstructYamlCoreFloat(),
        )
}

private object CoreResolver : BaseScalarResolver({
    addImplicitResolver(Tag.NULL, EMPTY, null)
    addImplicitResolver(Tag.BOOL, CoreScalarResolver.BOOL, "tfTF")
    // INT before FLOAT, whose pattern also matches integers.
    addImplicitResolver(Tag.INT, CoreScalarResolver.INT, "-+0123456789")
    addImplicitResolver(Tag.FLOAT, CoreScalarResolver.FLOAT, "-+0123456789.")
    addImplicitResolver(Tag.NULL, CoreScalarResolver.NULL, "n\u0000")
})

// Duplicate keys are already refused by default, as js-yaml refuses them. The engine's 3 MB input
// cap is lifted because js-yaml has none, and the text is already in memory by the time it's here.
private val loadSettings =
    LoadSettings(
        schema = YamlCoreSchema,
        maxAliasesForCollections = MAX_ALIASES,
        codePointLimit = Int.MAX_VALUE,
    )
