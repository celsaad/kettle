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
import it.krzeminski.snakeyaml.engine.kmp.constructor.core.ConstructYamlCoreInt
import it.krzeminski.snakeyaml.engine.kmp.events.AliasEvent
import it.krzeminski.snakeyaml.engine.kmp.events.CollectionEndEvent
import it.krzeminski.snakeyaml.engine.kmp.events.CollectionStartEvent
import it.krzeminski.snakeyaml.engine.kmp.events.DocumentStartEvent
import it.krzeminski.snakeyaml.engine.kmp.events.ScalarEvent
import it.krzeminski.snakeyaml.engine.kmp.exceptions.ConstructorException
import it.krzeminski.snakeyaml.engine.kmp.exceptions.YamlEngineException
import it.krzeminski.snakeyaml.engine.kmp.nodes.Node
import it.krzeminski.snakeyaml.engine.kmp.nodes.ScalarNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Tag
import it.krzeminski.snakeyaml.engine.kmp.resolver.BaseScalarResolver
import it.krzeminski.snakeyaml.engine.kmp.resolver.CoreScalarResolver
import it.krzeminski.snakeyaml.engine.kmp.resolver.ScalarResolver
import it.krzeminski.snakeyaml.engine.kmp.schema.Schema

/**
 * The YAML loader for the app's files: snakeyaml-engine-kmp, the multiplatform port of
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
    Dump(DumpSettings(schema = YamlCoreSchema, defaultFlowStyle = FlowStyle.BLOCK)).dumpToString(wholeNumbers(value))

/**
 * Writes a whole `Double` as the integer it is, `rest_sec: 90` rather than `90.0`, as js-yaml writes a
 * JS number. The files are hand-edited, and a save shouldn't rewrite every number in them. Both read
 * back as the same `Double`. Negative zero is left alone, since `0` would read back as positive zero.
 */
private fun wholeNumbers(value: Any?): Any? =
    when (value) {
        is Double -> {
            val negativeZero = value == 0.0 && 1 / value < 0
            if (isSafeWhole(value) && !negativeZero) value.toLong() else value
        }

        is Map<*, *> -> {
            value.mapValues { (_, item) -> wholeNumbers(item) }
        }

        is List<*> -> {
            value.map(::wholeNumbers)
        }

        else -> {
            value
        }
    }

/**
 * A mapping for [dumpYaml], in the order given, with every null left out: each nullable a writer hands
 * it is an optional, and `key: null` would read back as a refusal.
 */
internal fun optionals(vararg entries: Pair<String, Any?>): Map<String, Any?> =
    entries.filter { it.second != null }.toMap(LinkedHashMap())

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
 * The YAML 1.2 core schema as js-yaml has it, which the engine's `CoreSchema` departs from four ways:
 * - `<<` merge keys, which change what a file means (`conformance/session.yaml` has the case).
 * - `${NAME}` plain scalars get an environment-variable tag, inert today but read from the process
 *   environment the moment anyone sets `envConfig`. Out of the resolver, no setting can turn it on.
 * - `!!set`, and that env tag written out explicitly, still construct. js-yaml refuses both as
 *   unknown tags, so they are registered here as refusals.
 * - Floats: see [coreFloat].
 */
private object YamlCoreSchema : Schema {
    override val scalarResolver: ScalarResolver = CoreResolver

    override val schemaTagConstructors: Map<Tag, ConstructNode> =
        mapOf(
            Tag.NULL to ConstructYamlNull(),
            Tag.BOOL to ConstructYamlCoreBool(),
            Tag.INT to ConstructYamlCoreInt(),
            Tag.FLOAT to ConstructCoreFloat,
            Tag.SET to UnknownTag,
            Tag.ENV_TAG to UnknownTag,
        )
}

private object CoreResolver : ScalarResolver {
    private val patterns =
        object : BaseScalarResolver({
            addImplicitResolver(Tag.NULL, EMPTY, null)
            addImplicitResolver(Tag.BOOL, CoreScalarResolver.BOOL, "tfTF")
            // INT before FLOAT, whose pattern also matches integers.
            addImplicitResolver(Tag.INT, CoreScalarResolver.INT, "-+0123456789")
            addImplicitResolver(Tag.FLOAT, CoreScalarResolver.FLOAT, "-+0123456789.")
            addImplicitResolver(Tag.NULL, CoreScalarResolver.NULL, "n\u0000")
        }) {}

    override fun resolve(
        value: String,
        implicit: Boolean,
    ): Tag {
        val tag = patterns.resolve(value, implicit)
        return if (tag == Tag.FLOAT && coreFloat(value) == null) Tag.STR else tag
    }
}

/**
 * js-yaml's core float, which differs from the engine's in two places. A literal that overflows
 * (`1e400`) isn't a float at all, so it stays the string it was written as; only `.inf` spells
 * infinity. And `+.inf` is infinity, where the engine's constructor throws on the sign. Null when
 * [text] isn't a core float.
 */
private fun coreFloat(text: String): Double? {
    if (!CoreScalarResolver.FLOAT.matches(text)) return null
    val sign = if (text.startsWith("-")) -1.0 else 1.0
    return when (val unsigned = text.lowercase().removePrefix("-").removePrefix("+")) {
        ".inf" -> sign * Double.POSITIVE_INFINITY
        ".nan" -> Double.NaN
        else -> (sign * unsigned.toDouble()).takeIf { it.isFinite() }
    }
}

/**
 * Builds every float, plain ones included. It can only throw on an explicit `!!float`, because the
 * resolver tags a plain scalar a float only when [coreFloat] accepts it.
 */
private object ConstructCoreFloat : ConstructNode {
    override fun construct(node: Node?): Any {
        val text = (node as ScalarNode).value
        return coreFloat(text)
            ?: throw ConstructorException(null, null, "cannot resolve $text as a float", node.startMark)
    }
}

private object UnknownTag : ConstructNode {
    override fun construct(node: Node?): Any =
        throw ConstructorException(null, null, "unknown tag ${node?.tag}", node?.startMark)
}

// Duplicate keys are already refused by default, as js-yaml refuses them. The engine's 3 MB input
// cap is lifted because js-yaml has none, and the text is already in memory by the time it's here.
private val loadSettings =
    LoadSettings(
        schema = YamlCoreSchema,
        maxAliasesForCollections = MAX_ALIASES,
        codePointLimit = Int.MAX_VALUE,
    )
