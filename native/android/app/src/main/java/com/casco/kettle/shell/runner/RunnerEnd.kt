package com.casco.kettle.shell.runner

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

// The count-in before a session, and the centred column it and other runner screens stand in; the
// completion receipt after a session is `Completion.kt`'s.

/**
 * The count-in, as its board draws it (#472): the top bar a running session has, its End leaving with
 * nothing written as back does, then "Get ready" over the count and what comes first, the exercise and
 * its load. The workout's name moved to the top bar, where a running session keeps it. The hint shows
 * only before the first logged session, and only with sounds on.
 */
@Composable
internal fun CountIn(
    view: RunnerView.CountingIn,
    environment: RunnerEnvironment,
    now: Long,
    leave: () -> Unit,
) {
    val resources = LocalResources.current
    Column(Modifier.fillMaxSize().padding(bottom = Spacing.three)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
            TopBarName(resources.sessionName(view.plan.workout?.name), Modifier.weight(1f))
            EndPill(leave)
        }
        // Scrolling once it can't fit: at 200% text on a short phone the 312dp count, the First line
        // and the first-session hint ran past the bottom, and the hint was what got cut.
        CenteredColumn(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.session_countdown_getReady),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = runnerColors.text,
                textAlign = TextAlign.Center,
            )
            Numeral("${view.countIn.numeral(now)}", COUNT_NUMERAL)
            resources.firstLine(view.plan, environment.units)?.let {
                Text(it, style = KettleType.default, color = runnerColors.textSecondary, textAlign = TextAlign.Center)
            }
            if (environment.priorSessions.isEmpty() && environment.soundsOn) {
                Text(
                    stringResource(R.string.session_countdown_firstTimeHint),
                    style = KettleType.small,
                    color = runnerColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * "First: Goblet Squat · 48.5 lb": the first exercise the session runs, with its load where it has one,
 * which is what the set screen seeds. A Rest block first is passed over: the line says what to pick
 * up. None for an ad-hoc session, which starts with nothing queued.
 */
private fun Resources.firstLine(
    plan: RunnerPlan.Runnable,
    units: UnitSystem,
): String? {
    val first = plan.steps.firstNotNullOfOrNull { it as? WorkStep } ?: return null
    val kg = (first as? RunnerStep.Reps)?.targetWeightKg?.takeIf { it > 0 }
    // Alphabetical, as `:strings` numbers them: load, then name.
    return if (kg == null) {
        getString(R.string.session_countdown_first, first.exerciseName)
    } else {
        getString(R.string.session_countdown_firstLoaded, weightText(kg, units), first.exerciseName)
    }
}

/** The board's 240: the count is the screen, read from across the room as the phone goes down. */
private val COUNT_NUMERAL = 240.sp

/**
 * Centred down the screen and scrolling once it can't fit, as RN's `justifyContent: 'center'`: the
 * minimum height is what gives a scrolling column something to centre in.
 */
@Composable
internal fun CenteredColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = Spacing.five),
            verticalArrangement = Arrangement.spacedBy(Spacing.two, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
