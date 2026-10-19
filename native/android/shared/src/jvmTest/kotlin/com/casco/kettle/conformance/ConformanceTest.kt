package com.casco.kettle.conformance

import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.emomIntervalCount
import com.casco.kettle.domain.filterByName
import com.casco.kettle.domain.fromDisplayWeight
import com.casco.kettle.domain.rankByName
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.domain.weightStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.abort
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.schema.CoreSchema
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
            "units" to ::runUnits,
        )

    @TestFactory
    fun conformance(): List<DynamicContainer> =
        suiteNames().map { suite ->
            val runner = runners[suite]
            dynamicContainer(suite, if (runner == null) unported(suite) else ported(suite, runner))
        }

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

// The YAML 1.2 core schema, as the corpus README specifies. The goldens go through the same loader,
// since JSON is a subset of YAML 1.2.
private fun load(file: File): Any? =
    Load(LoadSettings.builder().setSchema(CoreSchema()).build()).loadFromString(file.readText())

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
