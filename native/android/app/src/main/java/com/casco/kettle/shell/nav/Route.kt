package com.casco.kettle.shell.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.shell.R
import java.net.URLDecoder

/** How a destination is presented: as a tab under the bar, in the sheet over the tabs, or as the runner. */
enum class Presentation { Tab, Sheet, Runner }

/**
 * Every screen the shell can show, by the path a link or `NavigateReceiver` names it with. The complete
 * route table: there is no fallback, so a path not listed here goes nowhere.
 */
enum class Destination(
    val path: String,
    val presentation: Presentation,
) {
    Workouts("/", Presentation.Tab),
    Library("/library", Presentation.Tab),
    History("/history", Presentation.Tab),
    Programs("/programs", Presentation.Tab),
    Settings("/settings", Presentation.Sheet),
    Support("/support", Presentation.Sheet),
    ProgramGuide("/program-guide", Presentation.Sheet),
    ExerciseProgress("/exercise-progress", Presentation.Sheet),
    Analytics("/analytics", Presentation.Sheet),
    ProgramDetail("/program-detail", Presentation.Sheet),
    SessionEditor("/session-editor", Presentation.Sheet),
    ExerciseEditor("/exercise-editor", Presentation.Sheet),
    WorkoutEditor("/workout-editor", Presentation.Sheet),
    ProgramEditor("/program-editor", Presentation.Sheet),
    Import("/import", Presentation.Sheet),
    Session("/session", Presentation.Runner),
}

/** The tab bar's destinations, in bar order. */
enum class Tab(
    val destination: Destination,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Workouts(Destination.Workouts, R.string.tabs_workouts, R.drawable.tab_workouts),
    Library(Destination.Library, R.string.tabs_library, R.drawable.tab_library),
    History(Destination.History, R.string.tabs_history, R.drawable.tab_history),
    Programs(Destination.Programs, R.string.tabs_programs, R.drawable.tab_programs),
    ;

    companion object {
        /** The tab [destination] is, or null for one above the tabs. */
        fun of(destination: Destination): Tab? = entries.firstOrNull { it.destination == destination }
    }
}

/** One place to go: a [destination] and the params it is opened with. */
data class Route(
    val destination: Destination,
    val params: Map<String, String> = emptyMap(),
) {
    /** Whether this route shows in the sheet, which is what `SHEET_ROUTES` said in RN. */
    val sheet: Boolean get() = destination.presentation == Presentation.Sheet

    /**
     * The start a [Destination.Session] route names, or null for none, which the runner's resolver reads
     * as the library's first workout (#303): `kettle://session` on its own still starts something.
     */
    val runnerStart: RunnerStart? get() = runnerStartFrom(params)

    companion object {
        /**
         * A `kettle://` link as a route, by the same rules as [parse]: `kettle://history` and
         * `kettle:///history` alike, since the host and the path are one route path here. Null for another
         * scheme, or a path no destination has.
         */
        fun fromLink(link: String): Route? {
            if (!link.startsWith(LINK_SCHEME)) return null
            return parse("/" + link.removePrefix(LINK_SCHEME).trimStart('/'))
        }

        /** [path] as a route, `/exercise-editor?id=pushups` say; null for a path no destination has. */
        fun parse(path: String): Route? {
            val (pathname, query) = path.split('?', limit = 2).let { it[0] to it.getOrNull(1) }
            val destination = Destination.entries.firstOrNull { it.path == pathname } ?: return null
            return Route(destination, queryParams(query))
        }

        private fun queryParams(query: String?): Map<String, String> =
            query
                ?.split('&')
                ?.filter { it.isNotEmpty() }
                ?.associate { pair ->
                    val (key, value) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    decode(key) to decode(value)
                }.orEmpty()

        // The charset's name, not a Charset: that overload is API 33, and the app runs from 24.
        private fun decode(text: String): String = URLDecoder.decode(text, "UTF-8")

        /** What the manifest's intent filter declares. */
        private const val LINK_SCHEME = "kettle://"
    }
}

/**
 * A start as params, the way a session route names it and [runnerStartFrom] reads it back: the runner's
 * service carries its start in an intent's extras this way too.
 */
internal fun RunnerStart.routeParams(): Map<String, String> =
    when (this) {
        RunnerStart.Adhoc -> {
            mapOf(ADHOC to "1")
        }

        is RunnerStart.Planned -> {
            when (val params = params) {
                is SessionParams.Workout -> {
                    mapOf(WORKOUT_ID to params.workoutId)
                }

                // Left out rather than empty: an absent `day` reads back as no day.
                is SessionParams.ProgramSlot -> {
                    mapOf(PROGRAM_ID to params.programId, WEEK to params.week.toString()) +
                        listOfNotNull(params.day?.let { DAY to it })
                }
            }
        }
    }

/** The start [params] name; null when they name none. */
internal fun runnerStartFrom(params: Map<String, String>): RunnerStart? {
    val workoutId = params[WORKOUT_ID]
    val programId = params[PROGRAM_ID]
    val week = params[WEEK]?.toLongOrNull()
    return when {
        params[ADHOC] == "1" -> {
            RunnerStart.Adhoc
        }

        workoutId != null -> {
            RunnerStart.Planned(SessionParams.Workout(workoutId))
        }

        programId != null && week != null -> {
            RunnerStart.Planned(
                SessionParams.ProgramSlot(programId, week, params[DAY]),
            )
        }

        else -> {
            null
        }
    }
}

private const val ADHOC = "adhoc"
private const val WORKOUT_ID = "workoutId"
private const val PROGRAM_ID = "programId"
private const val WEEK = "week"
private const val DAY = "day"
