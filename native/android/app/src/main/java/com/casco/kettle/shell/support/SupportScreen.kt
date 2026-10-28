package com.casco.kettle.shell.support

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.TipTier
import com.casco.kettle.domain.TipTierOffer
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.rn.FileKind
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing

/**
 * What `shellRoutes` registers at `/support`: the screen on the real file and Play, telling RN once a
 * tip is written, so Settings under the sheet thanks the user too.
 */
@Composable
fun SupportRoute() {
    val activity = LocalActivity.current ?: return
    val file = remember { DiskSupporterFile(StoragePaths(activity.filesDir).supporter) }
    val billing = remember { PlayTipBilling(activity) }
    SupportScreen(file, billing, onSaved = { JsBridge.filesChanged(FileKind.SUPPORTER) })
}

/**
 * Support, the tip jar (#198): `src/app/support.tsx` on the Play Billing Library. [onSaved] hears each
 * tip written to [file]. A test passes a [runtime] to fix the clock, write inline, and give up on a
 * store that never answers without waiting the real time.
 */
@Composable
fun SupportScreen(
    file: SupporterFile,
    billing: TipBilling,
    onSaved: () -> Unit,
    runtime: TipJarRuntime = TipJarRuntime(),
) {
    val scope = rememberCoroutineScope()
    val saved by rememberUpdatedState(onSaved)
    val jar = remember(file, billing) { TipJar(file, billing, scope, onSaved = { saved() }, runtime) }
    DisposableEffect(jar) {
        jar.open()
        onDispose { jar.close() }
    }
    SupportContent(jar)
}

@Composable
private fun SupportContent(jar: TipJar) {
    val colors = KettleTheme.colors
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
            Text(
                stringResource(R.string.support_title),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = colors.text,
            )
            Paragraph(stringResource(R.string.support_pitch))
            Paragraph(stringResource(R.string.support_whyPlay))
            // A past tip is still worth acknowledging when the store can't be reached now.
            if (jar.supporter.hasTipped) ThankYou(jar.supporter.tipCount)
            when (val store = jar.store) {
                StoreState.Connecting -> Connecting()
                StoreState.Unavailable -> Unavailable(onRetry = jar::retry)
                is StoreState.Ready -> Tiers(store.offers, jar.purchasing, onTip = jar::tip)
            }
            jar.outcome?.let { outcome ->
                Text(
                    stringResource(
                        when (outcome) {
                            Outcome.PENDING -> R.string.support_pending
                            Outcome.FAILED -> R.string.support_failed
                            Outcome.NOT_SAVED -> R.string.support_notSaved
                        },
                    ),
                    Modifier.padding(top = Spacing.three),
                    style = KettleType.small,
                    color = colors.accentText,
                )
            }
        }
    }
}

@Composable
private fun Paragraph(text: String) =
    Text(
        text,
        Modifier.padding(top = Spacing.three),
        style = KettleType.default,
        color = KettleTheme.colors.textSecondary,
    )

private val CardShape = RoundedCornerShape(16.dp)

/** RN's card padding, `Spacing.two + 6`. */
private val CardPadding = Spacing.two + 6.dp

@Composable
private fun Modifier.card(): Modifier {
    val colors = KettleTheme.colors
    return clip(CardShape)
        .background(colors.backgroundElement)
        .border(1.dp, colors.border, CardShape)
}

@Composable
private fun ThankYou(count: Long) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .padding(top = Spacing.four)
            .fillMaxWidth()
            .card()
            .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.one),
    ) {
        Text(stringResource(R.string.support_thankYouTitle), style = KettleType.heading, color = colors.text)
        Text(
            pluralText(
                R.plurals.support_thankYouBody,
                R.string.support_thankYouBody_zero,
                count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            ),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun StateBlock(content: @Composable () -> Unit) =
    Column(
        Modifier
            .padding(top = Spacing.four)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.two),
    ) { content() }

@Composable
private fun Connecting() =
    StateBlock {
        CircularProgressIndicator(Modifier.size(SpinnerSize), color = KettleTheme.colors.textSecondary)
        Text(
            stringResource(R.string.support_loading),
            style = KettleType.small,
            color = KettleTheme.colors.textSecondary,
        )
    }

/** RN offered no way out short of closing the sheet; the copy already said to try again. */
@Composable
private fun Unavailable(onRetry: () -> Unit) =
    StateBlock {
        Text(
            stringResource(R.string.support_storeUnavailablePlay),
            style = KettleType.small,
            color = KettleTheme.colors.accentText,
        )
        TextButton(onRetry, Modifier.heightIn(min = 48.dp)) {
            Text(
                stringResource(R.string.support_retry),
                style = KettleType.default,
                color = KettleTheme.colors.accentText,
            )
        }
    }

@Composable
private fun Tiers(
    offers: List<TipTierOffer>,
    purchasing: TipTier?,
    onTip: (TipTier) -> Unit,
) {
    val colors = KettleTheme.colors
    Column(Modifier.padding(top = Spacing.four), verticalArrangement = Arrangement.spacedBy(Spacing.two - 3.dp)) {
        for (offer in offers) {
            val busy = purchasing == offer.tier
            // One button, named by its own two texts, so it needs no label of its own: TalkBack reads
            // the tier and the price, as RN's `tipA11y` spelt out.
            Row(
                Modifier
                    .fillMaxWidth()
                    // heightIn, never height: a fixed height clips at large text sizes.
                    .heightIn(min = 48.dp)
                    .alpha(if (purchasing != null && !busy) DIMMED else 1f)
                    .card()
                    .clickable(enabled = purchasing == null, role = Role.Button) { onTip(offer.tier) }
                    .padding(CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.two),
            ) {
                Text(
                    stringResource(
                        when (offer.tier) {
                            TipTier.SMALL -> R.string.support_tierSmall
                            TipTier.MEDIUM -> R.string.support_tierMedium
                            TipTier.LARGE -> R.string.support_tierLarge
                        },
                    ),
                    Modifier.weight(1f),
                    style = KettleType.heading,
                    color = colors.text,
                )
                if (busy) {
                    CircularProgressIndicator(Modifier.size(SpinnerSize), color = colors.textSecondary)
                } else {
                    // accentText, not accent: white-on-accent clears AA-large only (theme.ts), and this
                    // sits at body size on a plain surface.
                    Text(offer.displayPrice, style = KettleType.heading, color = colors.accentText)
                }
            }
        }
    }
}

private const val DIMMED = 0.5f
private val SpinnerSize = 24.dp
