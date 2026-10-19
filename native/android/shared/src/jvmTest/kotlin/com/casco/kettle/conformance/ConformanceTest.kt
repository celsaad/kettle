package com.casco.kettle.conformance

import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.emomIntervalCount
import com.casco.kettle.domain.filterByName
import com.casco.kettle.domain.fromDisplayWeight
import com.casco.kettle.domain.parseSessionYaml
import com.casco.kettle.domain.rankByName
import com.casco.kettle.domain.serializeSessionYaml
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.domain.weightStep
import it.krzeminski.snakeyaml.engine.kmp.api.Load
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.abort
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Runs the repo's `conformance/` corpus against `:shared`, the Kotlin half of what
 * `src/domain/__tests__/conformance.test.ts` does for TypeScript. The goldens are generated from
 * the TS implementation, so a case that fails here is the two parsers disagreeing about the format.
 *
 * Suites are discovered from the directory rather than listed, and a suite with no runner below is
 * reported as skipped under its own name, never as passing. Porting a suite is adding its runner.
 */
class ConformanceTest {
    private val runners: Map<String, (Case) -> Any?> =
        mapOf(
            "search" to ::runSearch,
            "session" to ::runSession,
            "units" to ::runUnits,
        )

    @TestFactory
    fun conformance(): List<DynamicContainer> =
        suiteNames().map { suite ->
            val runner = runners[suite]
            dynamicContainer(suite, if (runner == null) unported(suite) else ported(suite, runner))
        }

    /**
     * The README's other half: what a writer emits isn't pinned, but that it reads back is. Every
     * case that parses must survive serialize → parse unchanged, compared as domain values.
     */
    @TestFactory
    fun roundTrips(): List<DynamicContainer> =
        listOf(
            dynamicContainer(
                "session",
                readSuite("session").mapNotNull { case ->
                    val parsed = parseSessionYaml(case.string("input")!!) as? ParseResult.Ok ?: return@mapNotNull null
                    dynamicTest("${case.name} survives serialize → parse") {
                        assertEquals(parsed, parseSessionYaml(serializeSessionYaml(parsed.value)))
                    }
                },
            ),
        )

    private fun unported(suite: String): List<DynamicNode> =
        listOf(dynamicTest("not ported to Kotlin") { abort<Unit>("conformance/$suite has no Kotlin runner yet") })

    private fun ported(
        suite: String,
        runner: (Case) -> Any?,
    ): List<DynamicNode> {
        val cases = readSuite(suite)
        val expected = readGoldens(suite)
        val names = cases.map { it.name }
        return listOf(
            dynamicTest("has unique case names") { assertEquals(names.size, names.toSet().size) },
            // As sets: the README orders lists, not the goldens' keys.
            dynamicTest("has a golden for every case and no golden without a case") {
                assertEquals(names.toSet(), expected.keys)
            },
        ) +
            cases.map { case ->
                dynamicTest(case.name) {
                    assertEquals(normalise(expected[case.name]), normalise(runner(case)))
                }
            }
    }

    private fun runSession(case: Case): Any = golden(parseSessionYaml(case.string("input")!!), ::sessionJson)

    private fun runUnits(case: Case): Any {
        val system = case.string("system")?.let(::unitSystem)
        return when (val op = case.string("op")) {
            "toDisplayWeight" -> toDisplayWeight(case.number("kg"), system!!)
            "fromDisplayWeight" -> fromDisplayWeight(case.number("value"), system!!)
            "weightStep" -> weightStep(system!!)
            "emomIntervalCount" -> emomIntervalCount(case.number("interval_sec"), case.number("total_minutes"))
            else -> error("unknown op $op")
        }
    }

    private fun runSearch(case: Case): Any {
        val names = case.strings("names")
        val query = case.string("query")!!
        return mapOf(
            "ranked" to rankByName(names, query) { it },
            "filtered" to filterByName(names, query) { it },
        )
    }

    private fun unitSystem(value: String): UnitSystem =
        when (value) {
            "metric" -> UnitSystem.METRIC
            "imperial" -> UnitSystem.IMPERIAL
            else -> error("unknown unit system $value")
        }
}

class Case(
    private val fields: Map<String, Any?>,
) {
    val name: String = fields["name"] as String

    fun string(key: String): String? = fields[key] as String?

    // Cast per element, so an unquoted name that loads as a number fails here, naming the value.
    fun strings(key: String): List<String> = (fields[key] as List<*>).map { it as String }

    fun number(key: String): Double = (fields[key] as Number).toDouble()
}

// Lazy so a missing property fails the test with this message, not as the ExceptionInInitializerError
// a top-level initialiser would throw.
private val conformanceDir: File by lazy {
    File(
        requireNotNull(System.getProperty("kettle.conformanceDir")) {
            "kettle.conformanceDir is unset; run this through Gradle, which points it at the repo's conformance/"
        },
    )
}

private fun suiteNames(): List<String> =
    conformanceDir
        .listFiles { file -> file.name.endsWith(".yaml") }
        .orEmpty()
        .map { it.name.removeSuffix(".yaml") }
        .sorted()

// The corpus files themselves, read with the engine's own defaults (the core schema) rather than
// `:shared`'s configured loader, so a bug in that loader can't also misread the inputs it's tested on.
// The goldens go through the same call, since JSON is a subset of YAML 1.2.
private fun load(file: File): Any? = Load().loadOne(file.readText())

/** A parse result in the goldens' shape. The refusal's wording isn't pinned, so it isn't compared. */
private fun <T> golden(
    result: ParseResult<T>,
    value: (T) -> Any?,
): Map<String, Any?> =
    when (result) {
        is ParseResult.Ok -> mapOf("ok" to true, "value" to value(result.value))
        is ParseResult.InvalidYaml -> mapOf("ok" to false, "kind" to "invalidYaml")
        is ParseResult.SchemaMismatch -> mapOf("ok" to false, "kind" to "schemaMismatch", "paths" to result.paths)
    }

/**
 * The camelCase domain model as JSON, written out by hand so that an absent optional is visibly left
 * out (`buildMap` with `?.let`) while a `| null` field is always present.
 */
private fun sessionJson(session: Session): Map<String, Any?> =
    mapOf(
        "version" to session.version,
        "id" to session.id,
        "workout" to session.workout,
        "program" to session.program,
        "programWeek" to session.programWeek,
        "programDay" to session.programDay,
        "startedAt" to session.startedAt,
        "endedAt" to session.endedAt,
        "entries" to session.entries.map(::entryJson),
    )

private fun entryJson(entry: SessionEntry): Map<String, Any?> =
    buildMap {
        put("exercise", entry.exercise)
        when (entry) {
            is SessionEntry.TimedHold -> {
                put("type", "timed_hold")
                put("sets", entry.sets.map { mapOf("holdSec" to it.holdSec, "restTakenSec" to it.restTakenSec) })
            }

            is SessionEntry.Reps -> {
                put("type", "reps")
                put(
                    "sets",
                    entry.sets.map { set ->
                        buildMap {
                            put("reps", set.reps)
                            set.weightKg?.let { put("weightKg", it) }
                            set.rpe?.let { put("rpe", it) }
                            put("restTakenSec", set.restTakenSec)
                        }
                    },
                )
            }

            is SessionEntry.Rest -> {
                put("type", "rest")
                put("restTakenSec", entry.restTakenSec)
            }

            is SessionEntry.Hiit -> {
                put("type", "hiit")
                put("roundsCompleted", entry.roundsCompleted)
            }

            is SessionEntry.Emom -> {
                put("type", "emom")
                put("minutes", entry.minutes.map { minute -> buildMap { minute.reps?.let { put("reps", it) } } })
            }

            is SessionEntry.Amrap -> {
                put("type", "amrap")
                put("roundsCompleted", entry.roundsCompleted)
                entry.extraReps?.let { put("extraReps", it) }
            }

            is SessionEntry.Cardio -> {
                put("type", "cardio")
                entry.durationSec?.let { put("durationSec", it) }
                entry.distanceMeters?.let { put("distanceMeters", it) }
            }
        }
    }

@Suppress("UNCHECKED_CAST")
private fun readSuite(suite: String): List<Case> {
    val document = load(File(conformanceDir, "$suite.yaml")) as Map<String, Any?>
    return (document["cases"] as List<Map<String, Any?>>).map(::Case)
}

@Suppress("UNCHECKED_CAST")
private fun readGoldens(suite: String): Map<String, Any?> =
    load(File(conformanceDir, "$suite.expected.json")) as Map<String, Any?>

/**
 * Makes a value comparable under the corpus README's rules: numbers compare numerically, so `3` and
 * `3.0` are equal, and lists keep their order. Adding `0.0` folds `-0.0` into `0.0`, which JSON
 * can't tell apart and a boxed `Double` comparison otherwise would.
 */
private fun normalise(value: Any?): Any? =
    when (value) {
        is Number -> value.toDouble() + 0.0
        is Map<*, *> -> value.mapValues { (_, entry) -> normalise(entry) }
        is List<*> -> value.map(::normalise)
        else -> value
    }
