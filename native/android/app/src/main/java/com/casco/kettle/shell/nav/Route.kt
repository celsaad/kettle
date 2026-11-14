package com.casco.kettle.shell.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.shell.R
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

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
    Today("/", Presentation.Tab),
    Library("/library", Presentation.Tab),
    Progress("/progress", Presentation.Tab),
    Settings("/settings", Presentation.Sheet),
    Support("/support", Presentation.Sheet),
    ProgramGuide("/program-guide", Presentation.Sheet),
    ExerciseProgress("/exercise-progress", Presentation.Sheet),
    ProgramDetail("/program-detail", Presentation.Sheet),
    SessionEditor("/session-editor", Presentation.Sheet),
    ExerciseEditor("/exercise-editor", Presentation.Sheet),
    WorkoutEditor("/workout-editor", Presentation.Sheet),
    WorkoutPreview("/workout-preview", Presentation.Sheet),
    ProgramEditor("/program-editor", Presentation.Sheet),
    Import("/import", Presentation.Sheet),
    YourFile("/your-file", Presentation.Sheet),
    LibraryYaml("/library-yaml", Presentation.Sheet),
    Session("/session", Presentation.Runner),
}

/** The tab bar's destinations, in bar order. */
enum class Tab(
    val destination: Destination,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Today(Destination.Today, R.string.tabs_today, R.drawable.tab_today),
    Library(Destination.Library, R.string.tabs_library, R.drawable.tab_library),
    Progress(Destination.Progress, R.string.tabs_progress, R.drawable.tab_progress),
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
    /** Whether this route shows in the sheet. */
    val sheet: Boolean get() = destination.presentation == Presentation.Sheet

    /**
     * The start a [Destination.Session] route names, or null for none, which the runner's resolver reads
     * as the library's first workout (#303): `kettle://session` on its own still starts something.
     */
    val runnerStart: RunnerStart? get() = runnerStartFrom(params)

    /** This route as the path [parse] reads back to it: what a saved back stack holds. */
    val path: String
        get() {
            if (params.isEmpty()) return destination.path
            val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
            return "${destination.path}?$query"
        }

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

        /**
         * [path] as a route, `/exercise-editor?id=pushups` say; null for a path no destination has, or a
         * query that can't be decoded. Never throws: a link is anyone's to type, and one read at launch
         * that threw would crash every relaunch until the task went.
         */
        fun parse(path: String): Route? {
            val (pathname, query) = path.split('?', limit = 2).let { it[0] to it.getOrNull(1) }
            val params = queryParams(query) ?: return null
            // An old path's own section wins over one it carried.
            return LEGACY[pathname]?.let { it.copy(params = params + it.params) }
                ?: Destination.entries.firstOrNull { it.path == pathname }?.let { Route(it, params) }
        }

        /**
         * The paths the four-tab shell had (#411), where a link, a widget or someone's notes may still
         * point: each lands on the tab and section that now holds its screen, rather than going nowhere.
         */
        private val LEGACY =
            mapOf(
                "/programs" to of(Section.PROGRAMS),
                "/history" to of(Section.SESSIONS),
                "/analytics" to of(Section.STATS),
            )

        /** Null when any key or value can't be decoded: half a query is no route. */
        private fun queryParams(query: String?): Map<String, String>? {
            val pairs =
                query?.split('&').orEmpty().filter { it.isNotEmpty() }.map { pair ->
                    val (key, value) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    percentDecode(key)?.let { k -> percentDecode(value)?.let { v -> k to v } }
                }
            return if (null in pairs) null else pairs.filterNotNull().toMap()
        }

        /** [section] on its tab: the one route each section has. */
        fun of(section: Section): Route = Route(section.tab.destination, mapOf(SECTION to section.param))

        /** What the manifest's intent filter declares. */
        private const val LINK_SCHEME = "kettle://"
    }
}

/**
 * [text] with each `%XX` read as a UTF-8 byte, or null for an escape that isn't one (`%zz`, a `%` at the
 * end) or bytes that aren't UTF-8. A path's query rather than a form's, so `+` stays a plus, which
 * `URLDecoder` would have read as a space, and nothing here throws, which `URLDecoder` would.
 */
private fun percentDecode(text: String): String? {
    val bytes = percentBytes(text) ?: return null
    return try {
        Charsets.UTF_8
            .newDecoder()
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

/** [text]'s bytes, each `%XX` as the byte it names; null for an escape that isn't two hex digits. */
private fun percentBytes(text: String): ByteArray? {
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '%') {
            val hex = text.substring(i + 1, minOf(i + ESCAPE_LENGTH, text.length))
            // Two ASCII hex digits exactly: toInt alone would take a sign (`%+1`) and any script's digits.
            if (hex.length != ESCAPE_LENGTH - 1 || !hex.all { it in HEX_DIGITS }) {
                return null
            }
            bytes.write(hex.toInt(HEX))
            i += ESCAPE_LENGTH
        } else {
            bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
    }
    return bytes.toByteArray()
}

/** [text] with everything but RFC 3986's unreserved characters escaped, as [percentDecode] reads it. */
private fun encode(text: String): String =
    buildString {
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (byte >= 0 && (c.isLetterOrDigit() || c in UNRESERVED)) {
                append(c)
            } else {
                append('%').append("%02X".format(byte.toInt() and BYTE_MASK))
            }
        }
    }

private const val ESCAPE_LENGTH = 3
private const val HEX = 16
private const val BYTE_MASK = 0xFF
private const val UNRESERVED = "-._~"
private const val HEX_DIGITS = "0123456789abcdefABCDEF"

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

/** The route param a tab's section rides in (#411): `/library?section=programs`. */
const val SECTION = "section"

/**
 * The sections the Library and Progress tabs hold (#411): each shows a screen the four-tab shell had as
 * a tab of its own, or Stats, which was a sheet. Declared in tab order, and a tab's first is where it
 * opens with none named. Library's is Workouts (#412), the list Today held until it narrowed to what's
 * next, then Programs and Exercises, as the canvas draws them (#413).
 */
enum class Section(
    val param: String,
    val tab: Tab,
    @StringRes val label: Int,
) {
    WORKOUTS("workouts", Tab.Library, R.string.tabs_sections_workouts),
    PROGRAMS("programs", Tab.Library, R.string.tabs_sections_programs),
    EXERCISES("exercises", Tab.Library, R.string.tabs_sections_exercises),
    SESSIONS("sessions", Tab.Progress, R.string.tabs_sections_sessions),
    STATS("stats", Tab.Progress, R.string.tabs_sections_stats),
    ;

    companion object {
        /** [tab]'s sections in switch order; none for a tab without them. */
        fun of(tab: Tab): List<Section> = entries.filter { it.tab == tab }
    }
}

/**
 * The section this route names on [tab], or the tab's first: an unknown one is the first too. Null on a
 * tab without sections.
 */
fun Route.section(tab: Tab): Section? {
    val sections = Section.of(tab)
    return sections.firstOrNull { it.param == params[SECTION] } ?: sections.firstOrNull()
}
