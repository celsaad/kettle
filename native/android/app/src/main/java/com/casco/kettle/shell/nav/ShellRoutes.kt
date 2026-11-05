package com.casco.kettle.shell.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.casco.kettle.shell.R
import com.casco.kettle.shell.exerciseeditor.EXERCISE_EDITOR_PATH
import com.casco.kettle.shell.exerciseeditor.ExerciseEditorRoute
import com.casco.kettle.shell.history.HistoryRoute
import com.casco.kettle.shell.home.HomeRoute
import com.casco.kettle.shell.importer.IMPORT_PATH
import com.casco.kettle.shell.importer.ImportRoute
import com.casco.kettle.shell.library.LibraryRoute
import com.casco.kettle.shell.programdetail.PROGRAM_DETAIL_PATH
import com.casco.kettle.shell.programdetail.ProgramDetailRoute
import com.casco.kettle.shell.programeditor.PROGRAM_EDITOR_PATH
import com.casco.kettle.shell.programeditor.ProgramEditorRoute
import com.casco.kettle.shell.programguide.ProgramGuideRoute
import com.casco.kettle.shell.programs.ProgramsRoute
import com.casco.kettle.shell.progress.EXERCISE_PROGRESS_PATH
import com.casco.kettle.shell.progress.ExerciseProgressRoute
import com.casco.kettle.shell.runner.RunnerPlaceholderRoute
import com.casco.kettle.shell.sessioneditor.SESSION_EDITOR_PATH
import com.casco.kettle.shell.sessioneditor.SessionEditorRoute
import com.casco.kettle.shell.settings.SettingsRoute
import com.casco.kettle.shell.stats.StatsRoute
import com.casco.kettle.shell.support.SupportRoute
import com.casco.kettle.shell.workouteditor.WORKOUT_EDITOR_PATH
import com.casco.kettle.shell.workouteditor.WorkoutEditorRoute

/**
 * The tab bar's destinations, in bar order. [path] is the route RN knows the tab by, kept even once a
 * tab renders natively: RN still reports its own moves in those paths (a delete's `dismissTo`, the
 * runner's exit), and native has to know which tab they mean.
 */
enum class Tab(
    val path: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Workouts("/", R.string.tabs_workouts, R.drawable.tab_workouts),
    Library("/library", R.string.tabs_library, R.drawable.tab_library),
    History("/history", R.string.tabs_history, R.drawable.tab_history),
    Programs("/programs", R.string.tabs_programs, R.drawable.tab_programs),
    ;

    companion object {
        /** The tab RN's [pathname] is on, or null when something above the tabs is showing. */
        fun at(pathname: String): Tab? = entries.firstOrNull { it.path == pathname }
    }
}

/** How a destination renders: in the one RN surface, or as a Compose screen. */
sealed interface Screen {
    data object Rn : Screen

    /** [content] is given the params RN reported the route with. */
    class Native(
        val content: @Composable (params: Map<String, String>) -> Unit,
    ) : Screen
}

/**
 * The native screens' registrations, by the path RN knows the route by. Anything not listed renders
 * RN, which from the library flip (#245) is the session runner alone. Moving a screen to native is one
 * line, plus an empty stub route RN keeps at the same path (#190): RN stays the router, so its stack,
 * back and route reports are unchanged, and native draws the screen whenever RN reports that path: a
 * tab's in `Destinations`, and a modal route's in the sheet (`NativeSheet`, #197), with the same stub
 * behind it.
 *
 * M5's three ownership flips (#222) have each released, so native is the only writer of
 * `preferences.json` and the backup folder (#238), of an edited session file (#240), and of the
 * library (#245). RN's runner still writes new sessions, and nothing else.
 */
private val screenRoutes: Map<String, Screen> =
    mapOf(
        "/support" to Screen.Native { SupportRoute() },
        "/program-guide" to Screen.Native { ProgramGuideRoute() },
        EXERCISE_PROGRESS_PATH to Screen.Native { params -> ExerciseProgressRoute(params) },
        "/analytics" to Screen.Native { StatsRoute() },
        Tab.Workouts.path to Screen.Native { HomeRoute() },
        Tab.History.path to Screen.Native { HistoryRoute() },
        Tab.Library.path to Screen.Native { LibraryRoute() },
        Tab.Programs.path to Screen.Native { ProgramsRoute() },
        PROGRAM_DETAIL_PATH to Screen.Native { params -> ProgramDetailRoute(params) },
        "/settings" to Screen.Native { SettingsRoute() },
        SESSION_EDITOR_PATH to Screen.Native { params -> SessionEditorRoute(params) },
        EXERCISE_EDITOR_PATH to Screen.Native { params -> ExerciseEditorRoute(params) },
        WORKOUT_EDITOR_PATH to Screen.Native { params -> WorkoutEditorRoute(params) },
        PROGRAM_EDITOR_PATH to Screen.Native { params -> ProgramEditorRoute(params) },
        IMPORT_PATH to Screen.Native { ImportRoute() },
    )

/**
 * The routes with the native runner registered while [nativeRunner] is on (#302). It is a full-screen
 * destination above the tabs rather than a sheet (`Layer.NativeRunner`), behind a stub RN pushes like
 * any other. RN's runner stays at its own path either way, which `.dev`'s `kettle://session` links
 * still reach until routing moves native.
 */
fun routesFor(nativeRunner: Boolean): Map<String, Screen> =
    if (nativeRunner) {
        screenRoutes + (NATIVE_RUNNER_PATH to Screen.Native { params -> RunnerPlaceholderRoute(params) })
    } else {
        screenRoutes
    }

/** The routes this build registers: `Flips.kt`'s [NATIVE_RUNNER] is on in debug and off in release. */
val shellRoutes: Map<String, Screen> = routesFor(NATIVE_RUNNER)

/** How [path] renders. */
fun Map<String, Screen>.screenAt(path: String): Screen = this[path] ?: Screen.Rn
