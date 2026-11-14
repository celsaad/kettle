package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

// Either end of a session: the count-in before it, and the completion screen after.

/**
 * GET READY, the workout's name as the headline and the count as its caption: the name is what a
 * reader checks here. The hint shows only before the first logged session, and only with sounds on.
 */
@Composable
internal fun CountIn(
    view: RunnerView.CountingIn,
    environment: RunnerEnvironment,
    now: Long,
) {
    val resources = LocalResources.current
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.two, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Caps(stringResource(R.string.session_countdown_getReady))
        Text(
            resources.sessionName(view.plan.workout?.name),
            style = KettleType.subtitle,
            color = runnerColors.text,
            textAlign = TextAlign.Center,
        )
        Numeral("${view.countIn.numeral(now)}", COUNT_NUMERAL, leading = COUNT_LEADING)
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

private val COUNT_NUMERAL = 56.sp

/** RN's count sets 64 on 56: a looser line than the other numerals' solid one. */
private const val COUNT_LEADING = 64f / 56f

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
