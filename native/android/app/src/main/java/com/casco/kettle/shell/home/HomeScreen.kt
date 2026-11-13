package com.casco.kettle.shell.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.NextUp
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.nextUpView
import com.casco.kettle.domain.rankByName
import com.casco.kettle.domain.workoutShape
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.NewItemButton
import com.casco.kettle.shell.list.NewItemButtonClearance
import com.casco.kettle.shell.list.NoResults
import com.casco.kettle.shell.list.RowStartButton
import com.casco.kettle.shell.list.SearchBar
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.ScrollsToTopOnReselect
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.routeParams
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** What the route table draws at `Destination.Workouts`, the tab the app opens on. */
@Composable
fun HomeRoute() {
    val navigator = LocalNavigator.current
    HomeScreen(LocalLatestFiles.current, LocalTabReselects.current, navigator::push, navigator::openRunner)
}

/**
 * Home (#235), the Workouts tab, as RN's `(tabs)/index.tsx`, now the empty stub at this path: what to
 * run next, and everything there is to run. The next-up card is the only path that resolves which
 * program week is due and starts it with that week's overrides; a row's start passes a bare workout.
 * [open] pushes a route and [startRunner] opens the runner (#302); both parameters, so a test hears
 * each route opened and each start.
 *
 * It is the screen the app's start waits for. The activity's `reportFullyDrawn`, the startup
 * benchmark's end point, waits for the library as well as the first frame, since a frame without it
 * isn't Home drawn, as RN Home's marker didn't report before it either. The splash goes on the read
 * alone, in the shell (`SplashExit`).
 *
 * [reselected] carries taps on the tab already showing; one on Workouts scrolls back to the top.
 */
@Composable
fun HomeScreen(
    files: LatestFiles,
    reselected: Flow<Tab>,
    open: (Route) -> Unit,
    startRunner: (RunnerStart) -> Unit,
    clock: Clock = Clock.System,
) {
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(Tab.Workouts, reselected, listState)
    val library = files.library
    val log = files.sessionLog
    ReportDrawnWhen { library != null }

    var query by rememberSaveable { mutableStateOf("") }
    val all = library?.workouts.orEmpty()
    val matching = remember(all, query) { rankByName(all, query) { it.name } }
    // Logged, not raw: a session of nothing but zeros is a skipped workout, and mustn't move the
    // program on. Worked out again on every read, the app's return included, changed files or not,
    // since a turned day alone moves a rest day on. RN's card stayed put until a file changed.
    val sessions = log?.logged.orEmpty()
    val nextUp = remember(library, log, files.reads) { library?.let { nextUpView(it, sessions, clock) } }

    Box(Modifier.fillMaxSize().statusBarsPadding().homeScreenTag(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = Spacing.three,
                    end = Spacing.three,
                    top = Spacing.two,
                    bottom = NewItemButtonClearance,
                ),
        ) {
            item(key = "header") {
                TitleRow { open(Route(Destination.Settings)) }
                // Nothing below the title before there is a library, so the screen never says "none"
                // before it has looked.
                if (library != null) {
                    WhatToRun(nextUp, newHere = sessions.isEmpty(), hasWorkouts = all.isNotEmpty(), open, startRunner)
                    // Keyed off the whole library, so it doesn't vanish under the finger typing. The
                    // count is the whole library's too: a placeholder only shows while nothing is typed.
                    if (all.isNotEmpty()) {
                        SearchBar(
                            query,
                            { query = it },
                            pluralText(
                                R.plurals.build_searchPlaceholder,
                                R.string.build_searchPlaceholder_zero,
                                all.size,
                            ),
                        )
                    }
                    ListHeaderRule()
                }
            }
            itemsIndexed(matching, key = { _, workout -> workout.id }) { index, workout ->
                if (index > 0) ListRowSeparator()
                WorkoutRow(workout, library?.exercises.orEmpty(), open, startRunner)
            }
            // Told apart: "no workouts yet" is right on a fresh install and wrong to someone with forty
            // who mistyped one.
            when {
                library == null -> Unit
                all.isEmpty() -> item(key = "empty") { NoWorkouts { open(Route(Destination.Import)) } }
                matching.isEmpty() -> item(key = "no-results") { NoResults(query) }
            }
        }
        NewItemButton(stringResource(R.string.build_newWorkout), Modifier.align(Alignment.BottomEnd)) {
            open(Route(Destination.WorkoutEditor))
        }
    }
}

/**
 * Everything above the list that says what to run: the first-run card and the starter-pack link for
 * someone [newHere], the next-up card, and the empty session under it.
 */
@Composable
private fun WhatToRun(
    nextUp: NextUp?,
    newHere: Boolean,
    hasWorkouts: Boolean,
    open: (Route) -> Unit,
    startRunner: (RunnerStart) -> Unit,
) {
    // Gated on a queued workout, not on any card: step one reads "Start the workout below", and a rest
    // card has no Start, which a program opening on a rest day puts a new user in front of.
    if (newHere && nextUp is NextUp.Queued) FirstRunCard()
    // Not the first-run gate, which a rest day turns off and would leave a new user no invitation at
    // all. An empty library makes the offer from its own empty state, so it is never made twice. Under
    // the first-run card, continuing the block that orients someone new, rather than beside the empty
    // session, whose reading as Start's alternative a second link there would take away.
    if (newHere && hasWorkouts) {
        StarterPackLink(Modifier.padding(top = Spacing.three)) { open(Route(Destination.Import)) }
    }
    nextUp?.let {
        NextUpCard(
            it,
            start = { params -> startRunner(RunnerStart.Planned(params)) },
            preview = { params -> open(Route(Destination.WorkoutPreview, RunnerStart.Planned(params).routeParams())) },
        )
    }
    // Under the card's Start, reading as its alternative. Text weight, so it can't be taken for Start's
    // peer; on a rest day and with no workouts, it is the way to train.
    HomeLink(stringResource(R.string.today_startEmpty), Modifier) {
        startRunner(RunnerStart.Adhoc)
    }
}

/**
 * The title, and the gear that is the app's only way to Settings. A bordered 36dp square inside a
 * 48dp target, as RN's 36 with its `hitSlop`; icon-only, so it carries the name.
 */
@Composable
private fun TitleRow(onSettings: () -> Unit) {
    val colors = KettleTheme.colors
    val settings = stringResource(R.string.common_settings)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.build_title),
            Modifier.weight(1f).semantics { heading() },
            style = KettleType.subtitle,
            color = colors.text,
        )
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onSettings)
                .semantics { contentDescription = settings },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(GearShape)
                    .background(colors.backgroundElement)
                    .border(1.dp, colors.border, GearShape),
                contentAlignment = Alignment.Center,
            ) {
                TypeIcon(R.drawable.icon_settings, KettleType.default, colors.textSecondary)
            }
        }
    }
}

/** One workout: its name and summary open it in the editor, and the triangle starts it. */
@Composable
private fun WorkoutRow(
    workout: Workout,
    exercises: List<Exercise>,
    open: (Route) -> Unit,
    startRunner: (RunnerStart) -> Unit,
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
            Text(workoutShapeText(shape), style = KettleType.small, color = colors.textSecondary)
        }
        RowStartButton(stringResource(R.string.build_startAccessibility, workout.name)) {
            startRunner(RunnerStart.Planned(SessionParams.Workout(workout.id)))
        }
    }
}

private fun nextUpView(
    library: Library,
    sessions: List<Session>,
    clock: Clock,
) = nextUpView(library, sessions, clock.now(), TimeZone.currentSystemDefault())

/** The benchmark's uiautomator finds Home by this, as it found RN Home's testID. */
private fun Modifier.homeScreenTag() = semantics { testTagsAsResourceId = true }.testTag(HOME_SCREEN_TAG)

/** What `StartupBenchmark` waits for. The two share no code, so change both. */
private const val HOME_SCREEN_TAG = "home-screen"

private val GearShape = RoundedCornerShape(10.dp)
