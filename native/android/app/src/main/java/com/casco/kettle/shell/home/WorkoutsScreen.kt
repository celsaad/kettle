package com.casco.kettle.shell.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.programWorkoutIds
import com.casco.kettle.domain.rankByName
import com.casco.kettle.domain.workoutShape
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.library.rememberProgramPosition
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.NoResults
import com.casco.kettle.shell.list.RowStartButton
import com.casco.kettle.shell.list.SearchBar
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.ScrollsToTopOnReselect
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/** What the Library tab draws in its Workouts section (#412). */
@Composable
fun WorkoutsRoute() {
    val navigator = LocalNavigator.current
    WorkoutsScreen(LocalLatestFiles.current, LocalTabReselects.current, navigator::push, navigator::openRunner)
}

/**
 * Every workout there is to run (#412), under Library's tabs (#413), which hold its title and New
 * workout: a name search, rows that open the editor with a start each, and the empty library's way
 * out. A workout the running program trains says so. [open] pushes a route and [startRunner] opens the
 * runner, both parameters so a test hears each.
 *
 * [reselected] carries taps on the tab already showing; one on Library scrolls back to the top.
 */
@Composable
fun WorkoutsScreen(
    files: LatestFiles,
    reselected: Flow<Tab>,
    open: (Route) -> Unit,
    startRunner: (RunnerStart) -> Unit,
    clock: Clock = Clock.System,
) {
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(Tab.Library, reselected, listState)
    val library = files.library
    var query by rememberSaveable { mutableStateOf("") }
    val all = library?.workouts.orEmpty()
    val matching = remember(all, query) { rankByName(all, query) { it.name } }
    val program = rememberProgramPosition(files, clock)?.program
    val inProgram = remember(program) { program?.let(::programWorkoutIds).orEmpty() }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = Spacing.three,
                    end = Spacing.three,
                    top = Spacing.two,
                    bottom = Spacing.four,
                ),
        ) {
            item(key = "header") {
                // Keyed off the whole library, so it doesn't vanish under the finger typing. The count is
                // the whole library's too: a placeholder only shows while nothing is typed.
                if (all.isNotEmpty()) {
                    SearchBar(
                        query,
                        { query = it },
                        pluralText(R.plurals.build_searchPlaceholder, R.string.build_searchPlaceholder_zero, all.size),
                    )
                }
                if (library != null) ListHeaderRule()
            }
            itemsIndexed(matching, key = { _, workout -> workout.id }) { index, workout ->
                if (index > 0) ListRowSeparator()
                WorkoutRow(workout, library?.exercises.orEmpty(), open, startRunner, workout.id in inProgram)
            }
            // Told apart: "no workouts yet" is right on a fresh install and wrong to someone with forty
            // who mistyped one.
            when {
                library == null -> Unit
                all.isEmpty() -> item(key = "empty") { NoWorkouts { open(Route(Destination.Import)) } }
                matching.isEmpty() -> item(key = "no-results") { NoResults(query) }
            }
        }
    }
}

/**
 * One workout: its name and summary open it in the editor, and the triangle starts it. Today's too.
 * [inProgram] leads the summary with "In your program".
 */
@Composable
internal fun WorkoutRow(
    workout: Workout,
    exercises: List<Exercise>,
    open: (Route) -> Unit,
    startRunner: (RunnerStart) -> Unit,
    inProgram: Boolean = false,
) {
    val colors = KettleTheme.colors
    val shape = remember(workout, exercises) { workoutShape(workout, exercises) }
    ListRow {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open(Route(Destination.WorkoutEditor, mapOf("id" to workout.id))) }
                .wrapContentHeight(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(workout.name, style = KettleType.heading, color = colors.text)
            val summary = workoutShapeText(shape)
            Text(
                if (inProgram) stringResource(R.string.library_workoutInProgram, summary) else summary,
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
        RowStartButton(stringResource(R.string.build_startAccessibility, workout.name)) {
            startRunner(RunnerStart.Planned(SessionParams.Workout(workout.id)))
        }
    }
}
