package com.casco.kettle.conformance

import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.YamlLoad
import com.casco.kettle.domain.dumpYaml
import com.casco.kettle.domain.loadYaml
import com.casco.kettle.domain.parseLibraryYaml
import com.github.erosb.jsonsKema.ConstValidationFailure
import com.github.erosb.jsonsKema.JsonParser
import com.github.erosb.jsonsKema.Keyword
import com.github.erosb.jsonsKema.RequiredValidationFailure
import com.github.erosb.jsonsKema.SchemaLoader
import com.github.erosb.jsonsKema.ValidationFailure
import com.github.erosb.jsonsKema.Validator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.math.abs

/** A key no reader knows, which every level of the format drops. */
private const val UNKNOWN_KEY = "zz_unknown"

/**
 * `content/library-schema.json` is the format's declarative statement, the one the assistant
 * brief carries whole, and it is edited by hand (#349). This holds it to `:shared`'s validator twice:
 *
 * - over the corpus's `library` suite, the format's authority: every case whose golden is `ok`
 *   validates against the file, and every `schemaMismatch` case is refused at exactly the golden's
 *   paths. `invalidYaml` cases never reach a schema, so they aren't the file's to answer.
 * - over variants of `every-shape`, the case written to hold every type, block, override and week
 *   kind: each key dropped and each value swapped for a probe, with the file and `:shared` refusing
 *   at the same paths. The corpus pins one refusal per rule it thought to write a case for; this is
 *   what catches a required field made optional, or a bound dropped, that no case happens to hit.
 *
 * Both set aside `:shared`'s refinements, the rules the file doesn't state (see [refinements]).
 * The file is only ever this test's input: nothing that ships validates against it, which is why the
 * validator is a test dependency.
 */
class LibrarySchemaFileTest {
    @TestFactory
    fun libraryCases(): List<DynamicNode> {
        val goldens = readGoldens("library")
        return readSuite("library").mapNotNull { case ->
            val golden = goldens.getValue(case.name) as Map<*, *>
            val document = (loadYaml(libraryText(case)) as? YamlLoad.Loaded)?.value
            when {
                golden["ok"] == true -> {
                    dynamicTest("${case.name} validates") { assertEquals(emptyList<String>(), fileRefuses(document)) }
                }

                case.name in refinements -> {
                    // Asserted rather than skipped, so a rule the file learns to state leaves the list.
                    dynamicTest("${case.name} is a refinement the file doesn't state, so it validates") {
                        assertEquals(
                            emptyList<String>(),
                            fileRefuses(document),
                            "the file refuses it now: drop it from refinements",
                        )
                    }
                }

                golden["kind"] == "schemaMismatch" -> {
                    dynamicTest("${case.name} is refused at its golden's paths") {
                        assertEquals(golden["paths"], fileRefuses(document).ifEmpty { fail("the file accepts it") })
                    }
                }

                golden["kind"] == "invalidYaml" -> {
                    null
                }

                else -> {
                    dynamicTest(
                        "${case.name} has a golden this test knows",
                    ) { fail("unknown golden kind ${golden["kind"]}") }
                }
            }
        }
    }

    @Test
    fun `every refinement names a schemaMismatch case`() {
        val goldens = readGoldens("library")
        refinements.keys.forEach { name ->
            assertEquals("schemaMismatch", (goldens[name] as Map<*, *>?)?.get("kind"), "refinement $name")
        }
    }

    @Test
    fun `the file and shared refuse every variant of every-shape at the same paths`() {
        val case = readSuite("library").single { it.name == "every-shape" }
        val document = (loadYaml(libraryText(case)) as YamlLoad.Loaded).value
        val results =
            edits(document, emptyList()).map { (path, edit) ->
                val variant = applyEdit(document, path, edit)
                Triple("${path.joinToString(".")} = ${edit ?: "null"}", sharedRefuses(variant), fileRefuses(variant))
            }
        val disagreements =
            results.filter { (_, shared, file) -> shared != file }.map { (edit, shared, file) ->
                "$edit: :shared $shared, file $file"
            }
        // Vacuity guard: the probes must actually provoke refusals, or agreeing proves nothing.
        assertTrue(
            results.count { (_, shared, _) -> shared.isNotEmpty() } > results.size / 2,
        )
        assertEquals(emptyList<String>(), disagreements)
    }

    /**
     * The `schemaMismatch` cases the file accepts, each a rule `:shared`'s readers check and the file
     * doesn't state. Most compare two fields or look across a list, which JSON Schema has no keyword
     * for. The rest it could state (`dependentRequired`, `if`/`then`) and doesn't yet: zod, which the
     * file was generated from until #349, held them as refinements and its emitter dropped them.
     * The brief tells the assistant only that such rules exist; Import's refusal and repair loop is
     * what teaches them.
     */
    private val refinements =
        mapOf(
            "reps-max-below-min" to "target_reps_max ≥ target_reps_min compares two fields",
            "hold-max-without-min" to "hold_sec_max needing hold_sec_min is statable (dependentRequired), not stated",
            "hold-max-below-min" to "hold_sec_max ≥ hold_sec_min compares two fields",
            "emom-derived-count-past-ceiling" to "the interval count is total_minutes over interval_sec",
            "emom-tiny-interval" to "the interval count is total_minutes over interval_sec",
            "week-without-workout" to "workout required unless rest_day is statable (if/then), not stated",
            "rest-week-with-workout" to "workout refused on a rest_day is statable (if/then), not stated",
            "rest-week-with-overrides" to "overrides refused on a rest_day is statable (if/then), not stated",
            "program-of-only-rest-weeks" to "at least one training week looks across the list",
            "repeated-week-and-day" to "a unique week and day pair looks across the list",
            "override-naming-both-targets" to "exactly one of exercise and block is statable (oneOf), not stated",
            "override-naming-no-target" to "exactly one of exercise and block is statable (oneOf), not stated",
        )

    /**
     * The messages of `:shared`'s refinements (`ExerciseSchema.kt`, `ProgramSchema.kt`), as prefixes.
     * A refusal with one of these is set aside before comparing. A reworded message stops matching
     * and shows up as a disagreement, never as a silent pass.
     */
    private val refinementMessages =
        listOf(
            "must be >= target_reps_min",
            "needs hold_sec_min",
            "must be >= hold_sec_min",
            "must come to at most",
            "needs a workout unless rest_day: true",
            "a rest_day week cannot name a workout",
            "a rest_day week has nothing to override",
            "needs at least one week that runs a workout",
            "must not repeat the same (week, day) pair",
            "must specify exactly one of exercise or block",
        )

    /** `:shared`'s refused paths for [document], its refinements set aside. */
    private fun sharedRefuses(document: Any?): List<String> =
        when (val result = parseLibraryYaml(dumpYaml(document))) {
            is ParseResult.Ok -> {
                emptyList()
            }

            is ParseResult.SchemaMismatch -> {
                result.detail
                    .split("; ")
                    .map { it.split(": ", limit = 2) }
                    .filter { (_, message) -> refinementMessages.none(message::startsWith) }
                    .map { (path, _) -> path }
                    .distinct()
                    .sorted()
            }

            is ParseResult.InvalidYaml -> {
                fail("a variant written by dumpYaml didn't load: ${result.detail}")
            }
        }

    /** The file's refused paths for [document], sorted; empty when it validates. */
    private fun fileRefuses(document: Any?): List<String> =
        validator
            .validate(JsonParser(json(document).toString()).parse())
            ?.let(::refusedPaths)
            ?.sorted()
            .orEmpty()

    /**
     * Every single edit [edits] makes: each key dropped, an unknown key added to each mapping, a list
     * cut short by one, a mapping or list replaced by a probe, and each scalar replaced by every
     * probe. Paths are keys and indices. The unknown key is what catches the file refusing extra
     * keys (`additionalProperties: false`) that `:shared` drops.
     */
    private fun edits(
        node: Any?,
        path: List<Any>,
    ): List<Pair<List<Any>, Any?>> =
        buildList {
            when (node) {
                is Map<*, *> -> {
                    collectionProbes.forEach { add(path to it) }
                    add(path + UNKNOWN_KEY to 1)
                    node.forEach { (key, value) ->
                        add(path + key!! to Drop)
                        addAll(edits(value, path + key))
                    }
                }

                is List<*> -> {
                    collectionProbes.forEach { add(path to it) }
                    if (node.isNotEmpty()) add(path to node.dropLast(1))
                    node.forEachIndexed { index, item -> addAll(edits(item, path + index)) }
                }

                else -> {
                    scalarProbes.forEach { add(path to it) }
                }
            }
        }

    /**
     * A wrong type, empty, and each side of every bound the file states: below and at zero, a
     * fraction, past the stated ceilings, and past the safe-integer one (2^53 − 1).
     */
    private val scalarProbes = listOf(null, "", "x", true, -1, 0, 0.5, 1e12, 1e16)
    private val collectionProbes = listOf(null, "x")

    private data object Drop {
        override fun toString() = "(dropped)"
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyEdit(
        node: Any?,
        path: List<Any>,
        edit: Any?,
    ): Any? =
        when {
            path.isEmpty() -> {
                edit
            }

            node is Map<*, *> -> {
                val map = node as Map<Any?, Any?>
                val key = path.first()
                if (path.size == 1 &&
                    edit === Drop
                ) {
                    map - key
                } else {
                    map + (key to applyEdit(map[key], path.drop(1), edit))
                }
            }

            else -> {
                (node as List<Any?>).toMutableList().also {
                    val index = path.first() as Int
                    it[index] = applyEdit(it[index], path.drop(1), edit)
                }
            }
        }

    /**
     * The golden's form of a refusal: dotted instance paths, a missing property at its own path, as
     * `:shared` reports it. A `oneOf` (or `anyOf`) over exercise or block types reports every
     * branch's failures, so it is narrowed to the branch whose `type` matched; when none did, because
     * `type` is unknown or missing, the refusal is the `type`.
     */
    private fun refusedPaths(failure: ValidationFailure): Set<String> =
        when {
            failure is RequiredValidationFailure -> {
                failure.missingProperties.map { dotted(failure, it) }.toSet()
            }

            failure.keyword == Keyword.ONE_OF || failure.keyword == Keyword.ANY_OF -> {
                val discriminator = dotted(failure, "type")
                val matched =
                    failure.causes.filterNot { branch ->
                        branch.flatten().any { refusesType(it, discriminator) }
                    }
                if (matched.isEmpty()) setOf(discriminator) else matched.flatMap(::refusedPaths).toSet()
            }

            failure.causes.isEmpty() -> {
                setOf(dotted(failure))
            }

            else -> {
                failure.causes.flatMap(::refusedPaths).toSet()
            }
        }

    private fun refusesType(
        failure: ValidationFailure,
        discriminator: String,
    ): Boolean =
        (failure is ConstValidationFailure && dotted(failure) == discriminator) ||
            (
                failure is RequiredValidationFailure &&
                    failure.missingProperties.any { dotted(failure, it) == discriminator }
            )

    private fun dotted(
        failure: ValidationFailure,
        vararg below: String,
    ): String = (failure.instance.location.pointer.segments + below).joinToString(".").ifEmpty { "(root)" }

    /**
     * The loaded tree as JSON, which it already is in shape: maps, lists and core-schema scalars. A
     * non-finite number (`.inf`, `.nan`) has no JSON form, so it goes over as a string, refused as
     * not a number at its own path, as `:shared` refuses it.
     */
    private fun json(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Double -> number(value)
            is Number -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to json(item) })
            is List<*> -> JsonArray(value.map(::json))
            else -> error("the loader built a ${value::class}, which JSON has no form for")
        }

    /**
     * A whole number goes over as the integer it is, as a JS writer prints it: json-sKema reads
     * `1.0E12` as a fraction, and would refuse it where an integer goes before any `maximum` could.
     */
    private fun number(value: Double): JsonPrimitive =
        when {
            !value.isFinite() -> JsonPrimitive(value.toString())
            value % 1.0 == 0.0 && abs(value) < Long.MAX_VALUE -> JsonPrimitive(value.toLong())
            else -> JsonPrimitive(value)
        }

    private val validator by lazy {
        val file = File(conformanceDir.parentFile, "content/library-schema.json")
        Validator.forSchema(SchemaLoader(JsonParser(file.readText()).parse()).load())
    }
}
