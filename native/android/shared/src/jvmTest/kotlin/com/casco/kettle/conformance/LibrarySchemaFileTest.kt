package com.casco.kettle.conformance

import com.casco.kettle.domain.YamlLoad
import com.casco.kettle.domain.loadYaml
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * `src/content/library-schema.json` is the format's declarative statement, the one the assistant
 * brief carries whole, and it is edited by hand (#349). This holds it to `:shared`'s validator over
 * the corpus's `library` suite, which is the format's authority: every case whose golden is `ok`
 * validates against the file, and every `schemaMismatch` case is refused at exactly the golden's
 * paths. `invalidYaml` cases never reach a schema, so they aren't the file's to answer.
 *
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
                    dynamicTest("${case.name} validates") { assertNull(refusal(document)?.let(::refusedPaths)) }
                }

                case.name in refinements -> {
                    // Asserted rather than skipped, so a rule the file learns to state leaves the list.
                    dynamicTest("${case.name} is a refinement the file can't state, so it validates") {
                        assertNull(
                            refusal(document)?.let(::refusedPaths),
                            "the file refuses it now: drop it from refinements",
                        )
                    }
                }

                golden["kind"] == "schemaMismatch" -> {
                    dynamicTest("${case.name} is refused at its golden's paths") {
                        val refusal = refusal(document) ?: fail("the file accepts it")
                        assertEquals(golden["paths"], refusedPaths(refusal).sorted())
                    }
                }

                else -> {
                    null
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

    /**
     * The `schemaMismatch` cases the file accepts, each a rule `:shared`'s readers check and JSON
     * Schema, as written here, doesn't state. Most compare two fields or look across a list, which
     * JSON Schema has no keyword for; the rest it could state with `dependentRequired` or `if`/`then`,
     * but the zod the file was generated from held them as refinements, and zod's emitter drops
     * refinements. The brief's own prose carries these rules to the assistant.
     */
    private val refinements =
        mapOf(
            "reps-max-below-min" to "target_reps_max ≥ target_reps_min compares two fields",
            "hold-max-without-min" to "hold_sec_max needing hold_sec_min was a zod refinement",
            "hold-max-below-min" to "hold_sec_max ≥ hold_sec_min compares two fields",
            "emom-derived-count-past-ceiling" to "the interval count is total_minutes over interval_sec",
            "emom-tiny-interval" to "the interval count is total_minutes over interval_sec",
            "week-without-workout" to "workout being required turns on rest_day, a zod refinement",
            "rest-week-with-workout" to "workout being refused turns on rest_day, a zod refinement",
            "rest-week-with-overrides" to "overrides being refused turns on rest_day, a zod refinement",
            "program-of-only-rest-weeks" to "at least one training week looks across the list",
            "repeated-week-and-day" to "a unique week and day pair looks across the list",
            "override-naming-both-targets" to "exactly one of exercise and block was a zod refinement",
            "override-naming-no-target" to "exactly one of exercise and block was a zod refinement",
        )

    private fun refusal(document: Any?): ValidationFailure? =
        validator.validate(JsonParser(json(document).toString()).parse())

    /**
     * The golden's form of a refusal: dotted instance paths, a missing property at its own path, as
     * `:shared` reports it. A `oneOf` over exercise or block types reports every branch's failures,
     * so it is narrowed to the branch whose `type` matched; when none did, the refusal is the `type`.
     */
    private fun refusedPaths(failure: ValidationFailure): Set<String> =
        when {
            failure is RequiredValidationFailure -> {
                failure.missingProperties.map { dotted(failure, it) }.toSet()
            }

            failure.keyword == Keyword.ONE_OF -> {
                val discriminator = dotted(failure, "type")
                val matched =
                    failure.causes.filter { branch ->
                        branch.flatten().none { it is ConstValidationFailure && dotted(it) == discriminator }
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

    private fun dotted(
        failure: ValidationFailure,
        vararg below: String,
    ): String = (failure.instance.location.pointer.segments + below).joinToString(".").ifEmpty { "(root)" }

    /** The loaded tree as JSON, which it already is in shape: maps, lists and core-schema scalars. */
    private fun json(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to json(item) })
            is List<*> -> JsonArray(value.map(::json))
            else -> error("the loader built a ${value::class}, which JSON has no form for")
        }

    private val validator by lazy {
        val file = File(conformanceDir.parentFile, "src/content/library-schema.json")
        Validator.forSchema(SchemaLoader(JsonParser(file.readText()).parse()).load())
    }
}
