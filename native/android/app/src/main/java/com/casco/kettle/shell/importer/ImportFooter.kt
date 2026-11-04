package com.casco.kettle.shell.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * Pinned below the scroll rather than its last child: a re-imported library renders a row per id and a
 * line per changed field, which put Merge screens down with nothing saying it was there. Once the write
 * lands the pair becomes Done alone: Cancel beside a finished import would suggest it could be undone.
 */
@Composable
internal fun Footer(importer: Importer) {
    val colors = KettleTheme.colors
    HorizontalDivider(thickness = 1.dp, color = colors.border)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.three, end = Spacing.three, top = Spacing.two + 2.dp, bottom = Spacing.one),
        contentAlignment = Alignment.Center,
    ) {
        Row(SheetColumn, horizontalArrangement = Arrangement.spacedBy(Spacing.three - 4.dp)) {
            if (importer.applied != null) {
                FooterButton(
                    stringResource(R.string.common_done),
                    Modifier.weight(1f),
                    primary = true,
                    onClick = importer.close,
                )
            } else {
                FooterButton(
                    stringResource(R.string.common_cancel),
                    Modifier.weight(1f),
                    primary = false,
                    onClick = importer.close,
                )
                FooterButton(
                    stringResource(R.string.import_mergeButton),
                    Modifier.weight(PRIMARY_WEIGHT),
                    primary = true,
                    enabled = importer.canMerge,
                    onClick = importer::merge,
                )
            }
        }
    }
}

/** RN's editor buttons: Cancel outlined, the primary filled and the wider, as the step the screen is for. */
@Composable
private fun FooterButton(
    text: String,
    modifier: Modifier,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    val surface =
        if (primary) {
            Modifier.background(colors.accent.dimmedUnless(enabled))
        } else {
            Modifier.border(1.5.dp, colors.border.dimmedUnless(enabled), ButtonShape)
        }
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(ButtonShape)
            .then(surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = KettleType.heading,
            color = (if (primary) colors.onAccent else colors.textSecondary).dimmedUnless(enabled),
        )
    }
}

internal val ButtonShape = RoundedCornerShape(15.dp)

/**
 * RN's `opacity: 0.5` on a control that can't be pressed yet: the whole control, fill and label.
 * Through the colours rather than `Modifier.alpha`, whose layer going from 0.5 to 1 as Merge enabled
 * left the button undrawn on a device (an SM-S936B), with its node still laid out and pressable.
 */
internal fun Color.dimmedUnless(enabled: Boolean) = if (enabled) this else copy(alpha = alpha * DISABLED_ALPHA)

private const val DISABLED_ALPHA = 0.5f

/** RN's `flex: 1.4` against Cancel's 1. */
private const val PRIMARY_WEIGHT = 1.4f
