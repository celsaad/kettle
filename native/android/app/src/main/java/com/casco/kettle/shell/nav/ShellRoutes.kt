package com.casco.kettle.shell.nav

import androidx.compose.runtime.Composable
import com.casco.kettle.shell.exerciseeditor.ExerciseEditorRoute
import com.casco.kettle.shell.home.HomeRoute
import com.casco.kettle.shell.importer.ImportRoute
import com.casco.kettle.shell.preview.WorkoutPreviewRoute
import com.casco.kettle.shell.programdetail.ProgramDetailRoute
import com.casco.kettle.shell.programeditor.ProgramEditorRoute
import com.casco.kettle.shell.programguide.ProgramGuideRoute
import com.casco.kettle.shell.progress.ExerciseProgressRoute
import com.casco.kettle.shell.runner.RunnerRoute
import com.casco.kettle.shell.sessioneditor.SessionEditorRoute
import com.casco.kettle.shell.settings.SettingsRoute
import com.casco.kettle.shell.support.SupportRoute
import com.casco.kettle.shell.workouteditor.WorkoutEditorRoute

/**
 * The screen each destination draws, given the params its route was opened with: the other half of the
 * route table [Destination] starts. Exhaustive, so a destination with no screen doesn't compile; since
 * #309 nothing falls back to RN.
 *
 * Native is the only writer of every file since the flip release (#308): the editors, Import and
 * Settings here, and the runner's sessions.
 *
 * Over detekt's complexity by one branch per destination: the table is the point, and splitting it would
 * hide a missing one.
 */
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun RouteContent(route: Route) {
    val params = route.params
    when (route.destination) {
        Destination.Today -> HomeRoute()

        Destination.Library -> LibraryTab(route)

        Destination.Progress -> ProgressTab(route)

        Destination.Settings -> SettingsRoute()

        Destination.Support -> SupportRoute()

        Destination.ProgramGuide -> ProgramGuideRoute()

        Destination.ExerciseProgress -> ExerciseProgressRoute(params)

        Destination.ProgramDetail -> ProgramDetailRoute(params)

        Destination.SessionEditor -> SessionEditorRoute(params)

        Destination.ExerciseEditor -> ExerciseEditorRoute(params)

        Destination.WorkoutEditor -> WorkoutEditorRoute(params)

        Destination.WorkoutPreview -> WorkoutPreviewRoute(params)

        Destination.ProgramEditor -> ProgramEditorRoute(params)

        Destination.Import -> ImportRoute()

        // The runner's slot draws it from the start it holds; a route only says which start that is.
        Destination.Session -> RunnerRoute(route.runnerStart)
    }
}
