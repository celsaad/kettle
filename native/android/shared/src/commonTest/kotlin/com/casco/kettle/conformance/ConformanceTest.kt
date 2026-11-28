package com.casco.kettle.conformance

import com.casco.kettle.domain.EntryBest
import com.casco.kettle.domain.EntryResult
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.RecordKind
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionRecord
import com.casco.kettle.domain.StepBuildResult
import com.casco.kettle.domain.SupporterState
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.addSetForMember
import com.casco.kettle.domain.buildStepsForExercise
import com.casco.kettle.domain.buildStepsWithLimits
import com.casco.kettle.domain.comingUpTimeline
import com.casco.kettle.domain.diffBlockOverride
import com.casco.kettle.domain.diffExerciseOverride
import com.casco.kettle.domain.dropLastSetForMember
import com.casco.kettle.domain.emomIntervalCount
import com.casco.kettle.domain.entryBest
import com.casco.kettle.domain.filterByName
import com.casco.kettle.domain.findProgramWeek
import com.casco.kettle.domain.fromDisplayWeight
import com.casco.kettle.domain.libraryTranslationOf
import com.casco.kettle.domain.localizeLibrary
import com.casco.kettle.domain.mergeBlockOverride
import com.casco.kettle.domain.mergeExerciseOverride
import com.casco.kettle.domain.mergeLibraries
import com.casco.kettle.domain.parseLibraryTranslation
import com.casco.kettle.domain.parseLibraryYaml
import com.casco.kettle.domain.parsePreferences
import com.casco.kettle.domain.parseSessionYaml
import com.casco.kettle.domain.parseSupporterState
import com.casco.kettle.domain.programWeekNumbers
import com.casco.kettle.domain.rankByName
import com.casco.kettle.domain.recordKey
import com.casco.kettle.domain.repairLibraryBounds
import com.casco.kettle.domain.resolveWorkoutForWeek
import com.casco.kettle.domain.serializeLibraryYaml
import com.casco.kettle.domain.serializeSessionYaml
import com.casco.kettle.domain.sessionDurationMinutes
import com.casco.kettle.domain.sessionEntryResult
import com.casco.kettle.domain.sessionRecords
import com.casco.kettle.domain.sessionSetCount
import com.casco.kettle.domain.setStepsForMember
import com.casco.kettle.domain.swapExerciseForMember
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.domain.translationFor
import com.casco.kettle.domain.upcomingOutline
import com.casco.kettle.domain.weightStep
import com.casco.kettle.storage.listFileNames
import com.casco.kettle.storage.readTextOrNull
import it.krzeminski.snakeyaml.engine.kmp.api.Load
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the repo's `conformance/` corpus against `:shared`, on the JVM and on the iOS simulator, and
 * with `-PupdateConformance` writes its goldens too (the corpus README), on the JVM only. An update
 * run compares each golden with the output it just wrote, so it passes whatever the runners say: the
 * golden diff it leaves is the only check, and reading it is the review.
 *
 * Suites are discovered from the directory rather than listed, and a suite with no runner below fails
 * [everySuiteHasARunner], so none is skipped or passed by omission, and none is written. Adding a
 * suite is adding its runner.
 *
 * This is `kotlin.test`, which runs on both platforms (#368) and has no dynamic tests, so each test
 * checks every case and then fails once, naming each case that failed, rather than reporting per case.
 */
class ConformanceTest {
    private val runners: Map<String, (Case) -> Any?> =
        mapOf(
            "library" to ::runLibrary,
            "localize" to ::runLocalize,
            "merge" to ::runMerge,
            "overrides" to ::runOverrides,
            "preferences" to ::runPreferences,
            "program" to ::runProgram,
            "records" to ::runRecords,
            "repair" to ::runRepair,
            "search" to ::runSearch,
            "session" to ::runSession,
            "steps" to ::runSteps,
            "supporter" to ::runSupporter,
            "units" to ::runUnits,
        )

    /** "Zero skipped", asserted: JUnit could report an unported suite as skipped, and this can't. */
    @Test
    fun everySuiteHasARunner() {
        val suites = suiteNames()
        assertTrue(suites.isNotEmpty(), "no suites in $conformanceDir")
        assertEquals(emptyList(), suites - runners.keys, "suites with no Kotlin runner")
    }

    @Test
    fun everyCaseMatchesItsGolden() {
        val write = goldenWriter()
        assertAll(
            suiteNames().filter { it in runners }.flatMap { suite ->
                val runner = runners.getValue(suite)
                val cases = readSuite(suite)
                write?.invoke(corpusPath("$suite.expected.json"), goldenJson(cases.associate { it.name to runner(it) }))
                val expected = readGoldens(suite)
                val names = cases.map { it.name }
                listOf(
                    "$suite has unique case names" to { assertEquals(names.size, names.toSet().size) },
                    // As sets: the README orders lists, not the goldens' keys.
                    "$suite has a golden for every case and no golden without a case" to {
                        assertEquals(names.toSet(), expected.keys)
                    },
                ) +
                    cases.map { case ->
                        "$suite / ${case.name}" to {
                            assertEquals(normalise(expected[case.name]), normalise(runner(case)))
                        }
                    }
            },
        )
    }

    /**
     * The README's other half: what a writer emits isn't pinned, but that it reads back is. Every
     * case that parses must survive serialize → parse unchanged, compared as domain values.
     */
    @Test
    fun everyLibraryRoundTrips() {
        // The `source` cases are in too: the published examples are the largest real libraries the
        // corpus has, and nothing about reading them from a file changes what the writer owes.
        assertAll(
            readSuite("library").mapNotNull { case ->
                val parsed = parseLibraryYaml(libraryText(case)) as? ParseResult.Ok ?: return@mapNotNull null
                "${case.name} survives serialize → parse" to {
                    assertEquals(parsed, parseLibraryYaml(serializeLibraryYaml(parsed.value)))
                }
            },
        )
    }

    @Test
    fun everySessionRoundTrips() {
        assertAll(
            readSuite("session").mapNotNull { case ->
                val parsed = parseSessionYaml(case.string("input")!!) as? ParseResult.Ok ?: return@mapNotNull null
                "${case.name} survives serialize → parse" to {
                    assertEquals(parsed, parseSessionYaml(serializeSessionYaml(parsed.value)))
                }
            },
        )
    }

    private fun runLibrary(case: Case): Any = golden(parseLibraryYaml(libraryText(case)), ::libraryJson)

    /** A table is inline, or the path of a shipped `<language>.json`, read in place like `source`. */
    private fun runLocalize(case: Case): Any {
        val tables =
            case.map("tables").entries.associate { (language, table) ->
                language as String to
                    when (table) {
                        is String -> parseLibraryTranslation(readRepoFile(table))
                        else -> libraryTranslationOf(table)
                    }
            }
        val library = mustParseLibrary(libraryText(case, "library"))
        return libraryJson(localizeLibrary(library, translationFor(tables, case.string("language"))))
    }

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

    private fun runSupporter(case: Case): Any = supporterJson(parseSupporterState(case.string("input")!!))

    private fun runRecords(case: Case): Any =
        when (val op = case.string("op")) {
            "entryBest" -> {
                val session = parseSessionYaml(case.string("session")!!) as? ParseResult.Ok
                checkNotNull(
                    session,
                ) { "fixture session does not parse" }.value.entries.map { entryBestJson(entryBest(it)) }
            }

            "recordKey" -> {
                recordKey(case.string("exercise")!!, RecordKind.entries.single { it.json == case.string("kind") })
            }

            "sessionDurationMinutes" -> {
                val session = parseSessionYaml(case.string("session")!!) as? ParseResult.Ok
                sessionDurationMinutes(checkNotNull(session) { "fixture session does not parse" }.value)
            }

            "sessionEntryResult" -> {
                val session = parseSessionYaml(case.string("session")!!) as? ParseResult.Ok
                checkNotNull(session) { "fixture session does not parse" }.value.entries.map {
                    entryResultJson(sessionEntryResult(it))
                }
            }

            "sessionSetCount" -> {
                val session = parseSessionYaml(case.string("session")!!) as? ParseResult.Ok
                sessionSetCount(checkNotNull(session) { "fixture session does not parse" }.value)
            }

            "sessionRecords" -> {
                val parse = { yaml: String ->
                    checkNotNull(parseSessionYaml(yaml) as? ParseResult.Ok) { "fixture session does not parse" }.value
                }
                val library = mustParseLibrary(case.string("library")!!)
                sessionRecords(parse(case.string("session")!!), case.strings("prior").map(parse), library.exercises)
                    .map(::sessionRecordJson)
            }

            else -> {
                error("unknown op $op")
            }
        }

    private fun runSession(case: Case): Any = golden(parseSessionYaml(case.string("input")!!), ::sessionJson)

    /** The ops and output shapes are in the header of `conformance/steps.yaml`. */
    private fun runSteps(case: Case): Any {
        val library = mustParseLibrary(case.string("library")!!)
        val workout =
            case.string("workout")?.let { id -> library.workouts.single { it.id == id } } ?: library.workouts.first()
        val built =
            if (case.flag(
                    "adhoc",
                )
            ) {
                StepBuildResult(emptyList(), false)
            } else {
                buildStepsWithLimits(workout, library.exercises)
            }
        val ops = case.mapsOrEmpty("ops")
        if (case.string("op") == "mutate") {
            var steps = built.steps
            return ops.map { op ->
                val result = stepOp(library, steps, op)
                if (result is List<*>) {
                    @Suppress("UNCHECKED_CAST")
                    steps = result as List<RunnerStep>
                }
                mapOf("op" to op, "result" to if (result is List<*>) steps.map(::stepJson) else result)
            }
        }
        val steps =
            ops.fold(built.steps) { steps, op ->
                @Suppress("UNCHECKED_CAST")
                (stepOp(library, steps, op) as? List<RunnerStep>) ?: steps
            }
        val indices = case.intsOrNull("at") ?: (-1..steps.size).toList()
        return when (val op = case.string("op")) {
            "build" -> {
                buildJson(built.truncated, steps, case.flag("brief"))
            }

            "outline" -> {
                indices.map { at -> mapOf("at" to at, "items" to upcomingOutline(steps, at).map(::upcomingJson)) }
            }

            "timeline" -> {
                indices.map { at -> mapOf("at" to at) + timelineJson(comingUpTimeline(steps, at)) }
            }

            else -> {
                error("unknown op $op")
            }
        }
    }

    private fun stepOp(
        library: Library,
        steps: List<RunnerStep>,
        op: Map<String, Any?>,
    ): Any {
        val member = op["member"] as String
        val exercise = { library.exercises.single { it.id == op["exercise"] } }
        return when (val name = op["op"]) {
            "count" -> setStepsForMember(steps, member)
            "addSet" -> addSetForMember(steps, member)
            "dropLastSet" -> dropLastSetForMember(steps, member)
            "swap" -> swapExerciseForMember(steps, member, op["from"] as Int, exercise(), op["newMember"] as String)
            "append" -> steps + buildStepsForExercise(exercise(), op["block"] as Int, member)
            else -> error("unknown op $name")
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

    fun map(key: String): Map<*, *> = fields[key] as Map<*, *>

    @Suppress("UNCHECKED_CAST")
    fun maps(key: String): List<Map<String, Any?>> = fields[key] as List<Map<String, Any?>>

    fun number(key: String): Double = (fields[key] as Number).toDouble()

    fun flag(key: String): Boolean = fields[key] == true

    @Suppress("UNCHECKED_CAST")
    fun mapsOrEmpty(key: String): List<Map<String, Any?>> = fields[key] as List<Map<String, Any?>>? ?: emptyList()

    fun intsOrNull(key: String): List<Int>? = (fields[key] as List<*>?)?.map { it as Int }
}

/**
 * A case's library text: inline under [key], or the repo file at `source` (the published
 * `site/examples/` libraries and the shipped `content/` ones), read in place like the corpus
 * itself so there is still one copy.
 */
internal fun libraryText(
    case: Case,
    key: String = "input",
): String = case.string("source")?.let(::readRepoFile) ?: case.string(key)!!

// Lazy so a missing setting fails the test with this message, not as the ExceptionInInitializerError
// a top-level initialiser would throw.
internal val conformanceDir: String by lazy {
    requireNotNull(conformanceDirSetting()) {
        "the corpus path is unset; run this through Gradle, which points it at the repo's conformance/"
    }.trimEnd('/')
}

/** [name] in the corpus directory. */
internal fun corpusPath(name: String): String = "$conformanceDir/$name"

/** A repo file, [path] relative to the root, as `source:` cases and shipped tables name them. */
internal fun readRepoFile(path: String): String = readFile("${conformanceDir.substringBeforeLast('/')}/$path")

/** A file the corpus promises is there, so a missing one fails naming it rather than as a null. */
private fun readFile(path: String): String = checkNotNull(readTextOrNull(path)) { "can't read $path" }

/** A fixture library, which a case promises parses: one that doesn't is a broken case, not a result. */
private fun mustParseLibrary(text: String): Library =
    when (val result = parseLibraryYaml(text)) {
        is ParseResult.Ok -> result.value
        else -> error("fixture library does not parse: $result")
    }

private fun suiteNames(): List<String> =
    listFileNames(conformanceDir)
        .filter { it.endsWith(".yaml") }
        .map { it.removeSuffix(".yaml") }
        .sorted()

// The corpus files themselves, read with the engine's own defaults (the core schema) rather than
// `:shared`'s configured loader, so a bug in that loader can't also misread the inputs it's tested on.
// The goldens go through the same call, since JSON is a subset of YAML 1.2.
private fun load(name: String): Any? = Load().loadOne(readFile(corpusPath(name)))

/**
 * Runs every check and fails once, listing each that failed, so one broken case can't hide the rest
 * the way a first-failure assertion would. An empty list fails too: a suite that read as nothing
 * would otherwise pass.
 */
private fun assertAll(checks: List<Pair<String, () -> Unit>>) {
    assertTrue(checks.isNotEmpty(), "nothing to check")
    val failures =
        checks.mapNotNull { (name, check) ->
            runCatching(check).exceptionOrNull()?.let { "$name: ${it.message ?: it}" }
        }
    if (failures.isNotEmpty()) {
        fail("${failures.size} of ${checks.size} failed:\n" + failures.joinToString("\n"))
    }
}

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

private fun entryBestJson(best: EntryBest?): Map<String, Any?>? =
    best?.let {
        mapOf("kind" to it.kind.json, "value" to it.value, "reps" to it.reps, "oneRepMaxKg" to it.oneRepMaxKg)
    }

/** As RN's `SessionRecord`: the kind, who, and the kind's own fields. */
private fun sessionRecordJson(record: SessionRecord): Map<String, Any?> {
    val who =
        mapOf("kind" to record.kind.json, "exerciseId" to record.exerciseId, "exerciseName" to record.exerciseName)
    return who +
        when (record) {
            is SessionRecord.HeaviestSet -> {
                mapOf("weightKg" to record.weightKg, "reps" to record.reps, "oneRepMaxKg" to record.oneRepMaxKg)
            }

            is SessionRecord.MostReps -> {
                mapOf("reps" to record.reps)
            }

            is SessionRecord.LongestHold -> {
                mapOf("holdSec" to record.holdSec)
            }

            is SessionRecord.MostRounds -> {
                mapOf("rounds" to record.rounds)
            }
        }
}

/** As RN's `EntryResult`, with an absent optional left out rather than null. */
private fun entryResultJson(result: EntryResult): Map<String, Any> {
    val fields: Map<String, Any?> =
        when (result) {
            is EntryResult.Holds -> {
                mapOf("kind" to "holds", "holdSecs" to result.holdSecs)
            }

            is EntryResult.Reps -> {
                mapOf("kind" to "reps", "reps" to result.reps, "weightsKg" to result.weightsKg)
            }

            is EntryResult.Rounds -> {
                mapOf(
                    "kind" to "rounds",
                    "rounds" to result.rounds,
                    "extraReps" to result.extraReps,
                )
            }

            is EntryResult.Intervals -> {
                mapOf("kind" to "intervals", "intervals" to result.intervals, "totalReps" to result.totalReps)
            }

            is EntryResult.Cardio -> {
                mapOf(
                    "kind" to "cardio",
                    "durationSec" to result.durationSec,
                    "distanceMeters" to result.distanceMeters,
                )
            }

            is EntryResult.Rest -> {
                mapOf("kind" to "rest", "restTakenSec" to result.restTakenSec)
            }
        }
    @Suppress("UNCHECKED_CAST")
    return fields.filterValues { it != null } as Map<String, Any>
}

private fun supporterJson(state: SupporterState): Map<String, Any?> =
    mapOf(
        "tipCount" to state.tipCount,
        "lastTipAt" to state.lastTipAt,
        "lastTier" to state.lastTier?.name?.lowercase(),
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
internal fun readSuite(suite: String): List<Case> {
    val document = load("$suite.yaml") as Map<String, Any?>
    return (document["cases"] as List<Map<String, Any?>>).map(::Case)
}

@Suppress("UNCHECKED_CAST")
internal fun readGoldens(suite: String): Map<String, Any?> = load("$suite.expected.json") as Map<String, Any?>

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
