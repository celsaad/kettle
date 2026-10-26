package com.casco.kettle.conformance

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.diffBlockOverride
import com.casco.kettle.domain.diffExerciseOverride
import com.casco.kettle.domain.emomIntervalCount
import com.casco.kettle.domain.filterByName
import com.casco.kettle.domain.findProgramWeek
import com.casco.kettle.domain.fromDisplayWeight
import com.casco.kettle.domain.mergeBlockOverride
import com.casco.kettle.domain.mergeExerciseOverride
import com.casco.kettle.domain.mergeLibraries
import com.casco.kettle.domain.parseLibraryYaml
import com.casco.kettle.domain.parsePreferences
import com.casco.kettle.domain.parseSessionYaml
import com.casco.kettle.domain.programWeekNumbers
import com.casco.kettle.domain.rankByName
import com.casco.kettle.domain.repairLibraryBounds
import com.casco.kettle.domain.resolveWorkoutForWeek
import com.casco.kettle.domain.serializeLibraryYaml
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
            "library" to ::runLibrary,
            "merge" to ::runMerge,
            "overrides" to ::runOverrides,
            "preferences" to ::runPreferences,
            "program" to ::runProgram,
            "repair" to ::runRepair,
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
            // Unlike the TS round-trip, the `source` cases are in too: the published examples are the
            // largest real libraries the corpus has, and nothing about reading them from a file
            // changes what the writer owes.
            dynamicContainer(
                "library",
                readSuite("library").mapNotNull { case ->
                    val parsed = parseLibraryYaml(libraryText(case)) as? ParseResult.Ok ?: return@mapNotNull null
                    dynamicTest("${case.name} survives serialize → parse") {
                        assertEquals(parsed, parseLibraryYaml(serializeLibraryYaml(parsed.value)))
                    }
                },
            ),
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

    private fun runLibrary(case: Case): Any = golden(parseLibraryYaml(libraryText(case)), ::libraryJson)

    private fun runMerge(case: Case): Any =
        mergeJson(
            mergeLibraries(mustParseLibrary(case.string("existing")!!), mustParseLibrary(case.string("incoming")!!)),
        )

    private fun runRepair(case: Case): Any =
        when (val repaired = repairLibraryBounds(case.string("input")!!)) {
            null -> mapOf("repaired" to false)
            else -> mapOf("repaired" to true, "result" to golden(parseLibraryYaml(repaired), ::libraryJson))
        }

    private fun runProgram(case: Case): Any {
        val library = mustParseLibrary(case.string("library")!!)
        return mapOf(
            "weekNumbers" to library.programs.associate { it.id to programWeekNumbers(it) },
            "queries" to
                case.maps("queries").map { query ->
                    val program = library.programs.single { it.id == query["program"] }
                    val week = (query["week"] as Number).toLong()
                    val day = query["day"] as String?
                    mapOf(
                        "query" to query,
                        "found" to (findProgramWeek(program, week, day) != null),
                        "resolved" to resolveWorkoutForWeek(program, week, library, day)?.let(::resolvedJson),
                    )
                },
        )
    }

    /** `block` is an index into the workout's blocks, as `base` and `edited` are for `diffBlock`. */
    private fun runOverrides(case: Case): Any {
        val library = mustParseLibrary(case.string("library")!!)
        val exercise = { id: Any? -> library.exercises.single { it.id == id } }
        val block = { workout: Any?, index: Any? -> library.workouts.single { it.id == workout }.blocks[index as Int] }
        return case.maps("ops").map { op ->
            val config by lazy { overrideConfig(op["config"] as Map<*, *>) }
            val workout = op["workout"]
            val result =
                when (val name = op["op"]) {
                    "mergeExercise" -> {
                        val target = exercise(op["exercise"])
                        exerciseMergeJson(target, mergeExerciseOverride(target, config))
                    }

                    "mergeBlock" -> {
                        val target = block(workout, op["block"])
                        blockMergeJson(target, mergeBlockOverride(target, config))
                    }

                    "diffExercise" -> {
                        patchJson(diffExerciseOverride(exercise(op["base"]), exercise(op["edited"])))
                    }

                    "diffBlock" -> {
                        patchJson(diffBlockOverride(block(workout, op["base"]), block(workout, op["edited"])))
                    }

                    else -> {
                        error("unknown op $name")
                    }
                }
            mapOf("op" to op, "result" to result)
        }
    }

    private fun runPreferences(case: Case): Any? = parsePreferences(case.string("input")!!)?.let(::preferencesJson)

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

    @Suppress("UNCHECKED_CAST")
    fun maps(key: String): List<Map<String, Any?>> = fields[key] as List<Map<String, Any?>>

    fun number(key: String): Double = (fields[key] as Number).toDouble()
}

/**
 * A library case's text: inline `input`, or the repo file at `source` (the published
 * `site/examples/` libraries), read in place like the corpus itself so there is still one copy.
 */
private fun libraryText(case: Case): String =
    case.string("source")?.let { File(conformanceDir.parentFile, it).readText() } ?: case.string("input")!!

// Lazy so a missing property fails the test with this message, not as the ExceptionInInitializerError
// a top-level initialiser would throw.
private val conformanceDir: File by lazy {
    File(
        requireNotNull(System.getProperty("kettle.conformanceDir")) {
            "kettle.conformanceDir is unset; run this through Gradle, which points it at the repo's conformance/"
        },
    )
}

/** A fixture library, which a case promises parses: one that doesn't is a broken case, not a result. */
private fun mustParseLibrary(text: String): Library =
    when (val result = parseLibraryYaml(text)) {
        is ParseResult.Ok -> result.value
        else -> error("fixture library does not parse: $result")
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

/** The preferences with their defaults filled in, as zod's parse returns them. */
private fun preferencesJson(preferences: Preferences): Map<String, Any?> =
    mapOf(
        "unitSystem" to preferences.unitSystem.name.lowercase(),
        "themePreference" to preferences.themePreference.name.lowercase(),
        "restDayReminder" to preferences.restDayReminder,
        "sessionSounds" to preferences.sessionSounds,
        "backupFolderUri" to preferences.backupFolderUri,
    )

/**
 * The camelCase domain model as JSON, written out by hand: every `| null` field is always present
 * here, and [entryJson] leaves an absent optional out rather than writing it as null.
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
