package com.casco.kettle.shell.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.resolveRunnerPlan
import com.casco.kettle.domain.sessionOutline
import com.casco.kettle.domain.workoutShape
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.home.weekLabel
import com.casco.kettle.shell.home.workoutShapeText
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.nav.runnerStartFrom
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.timeline.OutlineItem
import com.casco.kettle.shell.timeline.OutlineMode
import com.casco.kettle.shell.timeline.TimelineColors
import com.casco.kettle.shell.timeline.TimelineItem

/** What the route table draws at `Destination.WorkoutPreview`, opened from Home's next-up card. */
@Composable
fun WorkoutPreviewRoute(params: Map<String, String>) {
    WorkoutPreviewScreen(runnerStartFrom(params), LocalLatestFiles.current)
}

/**
 * What the next-up card will run, read-only (#280): the exercises in order, with their sets, targets
 * and weights, so you know what to get out before you press Start. No Start of its own: the card's
 * stays the one way in.
 *
 * Its params are the session route's, and it resolves them through [resolveRunnerPlan], the runner's
 * own resolver, so a program week's overrides reach the preview exactly as they reach the session. A
 * start that names nothing is a missing workout here, never the runner's first-workout fallback: a
 * preview of a workout no one picked would be a wrong answer, not a degraded one.
 */
@Composable
fun WorkoutPreviewScreen(
    start: RunnerStart?,
    files: LatestFiles,
) {
    val library = files.library
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(LocalConfiguration.current.locales[0])
    val plan =
        remember(start, library) {
            library?.let { if (start is RunnerStart.Planned) resolveRunnerPlan(start, it) else RunnerPlan.Missing }
        }
    val slot = (start as? RunnerStart.Planned)?.params as? SessionParams.ProgramSlot
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.four)
                .navigationBarsPadding(),
        ) {
            // Nothing before the library is read, so a workout that is there is never called gone.
            when (plan) {
                null -> {}

                RunnerPlan.Missing -> {
                    Message(stringResource(R.string.workoutPreview_notFound))
                }

                RunnerPlan.RestWeek -> {
                    Message(stringResource(R.string.session_restDay_body))
                }

                is RunnerPlan.NoSteps -> {
                    Header(plan.workout, emptyList(), slot)
                    Message(stringResource(R.string.session_nothingToRun_body, plan.workout.name))
                }

                // A truncated workout previews the steps that will run, which is what TooLong carries.
                is RunnerPlan.TooLong -> {
                    Preview(plan.plan, slot, units) {
                        Message(
                            stringResource(
                                R.string.session_tooLong_body,
                                plan.plan.workout
                                    ?.name
                                    .orEmpty(),
                            ),
                        )
                    }
                }

                is RunnerPlan.Runnable -> {
                    Preview(plan, slot, units)
                }
            }
        }
    }
}

@Composable
private fun Preview(
    plan: RunnerPlan.Runnable,
    slot: SessionParams.ProgramSlot?,
    units: UnitSystem,
    note: @Composable () -> Unit = {},
) {
    // Only an ad-hoc plan has no workout, and no route here names one; said rather than left blank.
    val workout = plan.workout
    if (workout == null) {
        Message(stringResource(R.string.workoutPreview_notFound))
        return
    }
    Header(workout, plan.exercises, slot)
    // A workout the step ceiling cuts short says so, as the runner does before its count-in: the list
    // under it is what will run, which isn't the whole workout.
    Box(Modifier.padding(top = Spacing.two)) { note() }
    ListHeaderRule()
    Outline(plan.steps, units)
}

@Composable
private fun Header(
    workout: Workout,
    exercises: List<Exercise>,
    slot: SessionParams.ProgramSlot?,
) {
    val colors = KettleTheme.colors
    val label = slot?.let { stringResource(R.string.today_nextUpWeek, weekLabel(it.week, it.day)) }
    Text(label ?: stringResource(R.string.today_nextUp), style = KettleType.label, color = colors.accentText)
    // User data, verbatim. No line cap: this is the one screen whose job is to show all of it.
    Text(
        workout.name,
        Modifier.padding(top = Spacing.one).semantics { heading() },
        style = KettleType.subtitle,
        color = colors.text,
    )
    if (exercises.isNotEmpty()) {
        val shape = remember(workout, exercises) { workoutShape(workout, exercises) }
        Text(
            workoutShapeText(shape),
            Modifier.padding(top = Spacing.two),
            style = KettleType.default,
            color = colors.textSecondary,
        )
    }
}

/** The whole session on the runner's timeline, in Home's colours, ending where the runner's does. */
@Composable
private fun Outline(
    steps: List<RunnerStep>,
    units: UnitSystem,
) {
    val colors = KettleTheme.colors
    val timeline =
        TimelineColors(
            text = colors.text,
            textSecondary = colors.textSecondary,
            rail = colors.border,
            circuit = colors.accentCalm,
            kicker = colors.accentCalmText,
        )
    val items = remember(steps) { sessionOutline(steps) }
    Column(Modifier.padding(top = Spacing.three)) {
        items.forEach { item -> OutlineItem(item, timeline, last = false, OutlineMode.Preview(units)) }
        TimelineItem(timeline, colors.textSecondary, last = true) {
            Text(stringResource(R.string.session_upcoming_end), style = KettleType.code, color = colors.textSecondary)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = KettleType.small, color = KettleTheme.colors.textSecondary)
}
