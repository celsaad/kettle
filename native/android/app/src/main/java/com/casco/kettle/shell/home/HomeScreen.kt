package com.casco.kettle.shell.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.NextUp
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.nextUpView
import com.casco.kettle.domain.offThePlan
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.ScrollsToTopOnReselect
import com.casco.kettle.shell.nav.Section
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.routeParams
import com.casco.kettle.shell.programdetail.PROGRAM_ID_PARAM
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** What the route table draws at `Destination.Today`, the tab the app opens on. */
@Composable
fun HomeRoute() {
    val navigator = LocalNavigator.current
    HomeScreen(LocalLatestFiles.current, LocalTabReselects.current, navigator::push, navigator::openRunner)
}

/**
 * Today (#412): the date and the gear, what to run next, and "Off the plan" under it, the two workouts
 * run most recently and an empty session, with every workout a link away in Library. The next-up card
 * is the only path that resolves which program week is due and starts it with that week's overrides; a
 * row's start passes a bare workout. [open] pushes a route and [startRunner] opens the runner (#302);
 * both parameters, so a test hears each route opened and each start.
 *
 * It is the screen the app's start waits for. The activity's `reportFullyDrawn`, the startup
 * benchmark's end point, waits for the library as well as the first frame, since a frame without it
 * isn't Home drawn, as RN Home's marker didn't report before it either. The splash goes on the read
 * alone, in the shell (`SplashExit`).
 *
 * [reselected] carries taps on the tab already showing; one on Today scrolls back to the top.
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
    ScrollsToTopOnReselect(Tab.Today, reselected, listState)
    val library = files.library
    val log = files.sessionLog
    ReportDrawnWhen { library != null }

    // Logged, not raw: a session of nothing but zeros is a skipped workout, and mustn't move the
    // program on. Worked out again on every read, the app's return included, changed files or not,
    // since a turned day alone moves a rest day on. RN's card stayed put until a file changed.
    val sessions = log?.logged.orEmpty()
    val nextUp = remember(library, log, files.reads) { library?.let { nextUpView(it, sessions, clock) } }
    // Keyed on the card as well: the workout it offers can move with the day alone, and the list has to
    // move with it or it shows that workout a second time.
    val offPlan =
        remember(library, log, nextUp) {
            library?.let { offThePlan(it, sessions, except = (nextUp as? NextUp.Queued)?.workout?.id) }.orEmpty()
        }
    val hasWorkouts = library?.workouts.orEmpty().isNotEmpty()
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(LocalConfiguration.current.locales[0])

    Box(Modifier.fillMaxSize().statusBarsPadding().homeScreenTag(), contentAlignment = Alignment.TopCenter) {
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
                TitleRow(remember(files.reads) { clock.now() }) { open(Route(Destination.Settings)) }
                // Nothing below the title before there is a library, so the screen never says "none"
                // before it has looked.
                if (library != null) {
                    FirstRunGuide(nextUp, newHere = sessions.isEmpty(), hasWorkouts = hasWorkouts, open)
                    nextUp?.let { WhatsNext(it, units, open, startRunner) }
                    if (!hasWorkouts) NoWorkouts { open(Route(Destination.Import)) }
                    OffThePlanHeader(hasWorkouts) { open(Route.of(Section.WORKOUTS)) }
                }
            }
            items(offPlan, key = { it.id }) { workout ->
                ListRowSeparator()
                WorkoutRow(workout, library?.exercises.orEmpty(), open, startRunner)
            }
            if (library != null) {
                item(key = "empty-session") {
                    ListRowSeparator()
                    EmptySessionRow { startRunner(RunnerStart.Adhoc) }
                }
            }
        }
    }
}

/**
 * What orients someone [newHere], above everything that says what to run: the first-run card and the
 * starter-pack link.
 */
@Composable
private fun FirstRunGuide(
    nextUp: NextUp?,
    newHere: Boolean,
    hasWorkouts: Boolean,
    open: (Route) -> Unit,
) {
    // Gated on a queued workout, not on any card: step one reads "Start the workout below", and a rest
    // card has no Start, which a program opening on a rest day puts a new user in front of.
    if (newHere && nextUp is NextUp.Queued) FirstRunCard()
    // Not the first-run gate, which a rest day turns off and would leave a new user no invitation at
    // all. An empty library makes the offer from its own empty state, so it is never made twice.
    if (newHere && hasWorkouts) {
        StarterPackLink(Modifier.padding(top = Spacing.three)) { open(Route(Destination.Import)) }
    }
}

/**
 * The program and its week when a program queued [nextUp], heading the card it queued, then the card.
 * Under the first-run guidance, so orientation for someone new comes before both.
 */
@Composable
private fun WhatsNext(
    nextUp: NextUp,
    units: UnitSystem,
    open: (Route) -> Unit,
    startRunner: (RunnerStart) -> Unit,
) {
    nextUp.position?.let { position ->
        ProgramRow(position) {
            open(Route(Destination.ProgramDetail, mapOf(PROGRAM_ID_PARAM to position.program.id)))
        }
        WeekStrip(position)
    }
    NextUpCard(
        nextUp,
        units,
        start = { params -> startRunner(RunnerStart.Planned(params)) },
        preview = { params -> open(Route(Destination.WorkoutPreview, RunnerStart.Planned(params).routeParams())) },
    )
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
