package com.casco.kettle.shell.runner

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.nav.routeParams
import com.casco.kettle.shell.nav.runnerStartFrom
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/** What `shellRoutes` registers at the native runner's path while the flip is on. */
@Composable
fun RunnerPlaceholderRoute(params: Map<String, String>) = RunnerPlaceholder(params, JsBridge::runnerDone)

/**
 * The native runner's stand-in (#302), on `.dev` alone: proof of the way in and out, which #305's
 * service and #306's screen replace. It reads its start back from the route and shows it as that
 * start's params, one `key=value` line each, so route-check can match them exactly and a start lost
 * on the way shows as nothing; they are ids, not copy, and no user sees them.
 */
@Composable
internal fun RunnerPlaceholder(
    params: Map<String, String>,
    onDone: () -> Unit,
) {
    val colors = KettleTheme.colors
    val start = remember(params) { runnerStartFrom(params) }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.three), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.two)) {
            for ((key, value) in start?.routeParams().orEmpty()) {
                Text("$key=$value", style = KettleType.code, color = colors.textSecondary)
            }
            Box(
                Modifier
                    .padding(top = Spacing.three)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(DoneShape)
                    .border(1.dp, colors.border, DoneShape)
                    .clickable(role = Role.Button, onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.common_done), style = KettleType.heading, color = colors.accentText)
            }
        }
    }
}

private val DoneShape = RoundedCornerShape(12.dp)
