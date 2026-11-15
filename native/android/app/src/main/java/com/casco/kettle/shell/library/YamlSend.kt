package com.casco.kettle.shell.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.SmallButton
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.system.confirmCopy
import com.casco.kettle.shell.system.copyToClipboard
import com.casco.kettle.shell.system.openLink
import com.casco.kettle.shell.system.shareText
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.delay

/**
 * What the last Copy, Share or link did, said under the buttons; nothing before any. Only a failure is
 * announced as it appears: a copy is the system's to announce (`confirmCopy`), and a live region on
 * "Copied" doubled it on a device, as Import's own button found.
 */
private enum class SendOutcome(
    val message: Int,
    val failure: Boolean,
) {
    Copied(R.string.common_copied, failure = false),
    CopyFailed(R.string.yamlView_copyFailed, failure = true),
    ShareCopied(R.string.yamlView_shareCopied, failure = false),
    LinkCopied(R.string.yamlView_linkCopied, failure = false),
}

/**
 * Copy and Share, the note that they send a complete file, and the link to the format (#444). A share
 * sheet that won't open says so and leaves Copy as the way through; a link nothing can open is copied
 * instead, as Settings' links are.
 */
@Composable
internal fun SendActions(yaml: String) {
    val colors = KettleTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    var outcome by remember { mutableStateOf<SendOutcome?>(null) }
    // Bumped by every outcome, so a second Copy inside two seconds restarts the timer, not inherits it.
    var said by remember { mutableIntStateOf(0) }
    val copied = stringResource(R.string.common_copied)
    // Where the app announces a copy (12L and lower), a share's fallback says why it copied.
    val shareCopied = stringResource(R.string.yamlView_shareCopied)
    val linkCopied = stringResource(R.string.yamlView_linkCopied)
    val copy = { landed: SendOutcome ->
        val took = context.copyToClipboard(yaml)
        if (took) view.confirmCopy(if (landed == SendOutcome.ShareCopied) shareCopied else copied)
        outcome = if (took) landed else SendOutcome.CopyFailed
        said++
    }
    Row(Modifier.padding(top = Spacing.three), horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        SmallButton(
            stringResource(R.string.yamlView_copy),
            colors.text,
            Modifier.weight(1f),
        ) { copy(SendOutcome.Copied) }
        // A sheet that won't open copies instead, and says so: the document still leaves.
        SmallButton(stringResource(R.string.yamlView_share), colors.text, Modifier.weight(1f)) {
            if (context.shareText(yaml)) outcome = null else copy(SendOutcome.ShareCopied)
        }
    }
    outcome?.let {
        Text(
            stringResource(it.message),
            Modifier.padding(top = Spacing.one).semantics { if (it.failure) liveRegion = LiveRegionMode.Polite },
            style = KettleType.small,
            color = if (it.failure) colors.accentText else colors.textSecondary,
        )
    }
    // "Copied" holds for two seconds, as Import's does: long enough to read, not long enough to describe a
    // later copy. A sentence saying why holds longer, but goes too, rather than describing an hour-old tap.
    LaunchedEffect(said) {
        val shown = outcome ?: return@LaunchedEffect
        delay(if (shown == SendOutcome.Copied) COPIED_FOR_MS else NOTE_FOR_MS)
        outcome = null
    }
    Text(
        stringResource(R.string.yamlView_sendNote),
        Modifier.padding(top = Spacing.two),
        style = KettleType.small,
        color = colors.textSecondary,
    )
    TextAction(stringResource(R.string.yamlView_formatLink), KettleType.smallMedium, colors.text, onClick = {
        if (!context.openLink(FORMAT_URL)) {
            val took = context.copyToClipboard(FORMAT_URL)
            if (took) view.confirmCopy(linkCopied)
            outcome = if (took) SendOutcome.LinkCopied else SendOutcome.CopyFailed
            said++
        }
    })
}

/** The format reference the site publishes, the copy an outside author reads. */
internal const val FORMAT_URL = "https://celsaad.github.io/kettle/format.html"

private const val COPIED_FOR_MS = 2000L

private const val NOTE_FOR_MS = 6000L
