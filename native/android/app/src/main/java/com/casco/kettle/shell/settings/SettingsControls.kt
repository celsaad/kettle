package com.casco.kettle.shell.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.ListRowVerticalPadding
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

// Settings' building blocks, `src/app/settings.tsx`'s Section, Segmented and ActionRow.

@Composable
internal fun Section(
    @StringRes title: Int,
    content: @Composable () -> Unit,
) = Column(Modifier.padding(top = Spacing.four)) {
    val locale = LocalConfiguration.current.locales[0]
    // RN's `label` type uppercases at draw time; the bundle's string is in sentence case.
    Text(
        stringResource(title).uppercase(locale),
        Modifier
            .padding(bottom = Spacing.two)
            .semantics { heading() },
        style = KettleType.label,
        color = KettleTheme.colors.textSecondary,
    )
    content()
}

@Composable
internal fun Caption(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) = Text(
    text,
    modifier.padding(top = Spacing.two),
    style = KettleType.small,
    color = if (accent) KettleTheme.colors.accentText else KettleTheme.colors.textSecondary,
)

/**
 * A line that only appears after a press, announced: focus stays on the row pressed, so without it
 * TalkBack reads nothing back at all, and for a failure that's the whole message.
 */
@Composable
internal fun Outcome(
    text: String,
    accent: Boolean,
) = Caption(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, accent)

/**
 * A choice of a few, as RN's `Segmented`: each a button carrying its selected state, so TalkBack says
 * which one holds. 44dp, never a fixed height, so it grows with the reader's text size.
 */
@Composable
internal fun <T> Segmented(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = KettleTheme.colors
    val outer = RoundedCornerShape(SEGMENTED_RADIUS)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, outer)
            .padding(SEGMENT_GAP),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        for ((value, label) in options) {
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(SEGMENT_RADIUS))
                    .background(if (active) colors.text else colors.backgroundElement)
                    .selectable(selected = active, role = Role.Button) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    style = KettleType.smallMedium,
                    color = if (active) colors.onAccent else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * A row that does something, matching the list screens' rows: a hairline above each rather than a
 * separator between, since some are conditional and the line above the first is the section's rule.
 * One button, named by its own two texts.
 */
@Composable
internal fun ActionRow(
    title: String,
    detail: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        ListRowSeparator()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowMinHeight)
                .padding(vertical = ListRowVerticalPadding)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
            horizontalArrangement = Arrangement.spacedBy(Spacing.two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = KettleType.heading, color = colors.text)
                Text(detail, style = KettleType.small, color = colors.textSecondary)
            }
            if (enabled) Text("›", style = KettleType.default, color = colors.textSecondary)
        }
    }
}

private val SEGMENTED_RADIUS = 14.dp
private val SEGMENT_RADIUS = 11.dp
private val SEGMENT_GAP = Spacing.half + 1.dp
private const val DISABLED_ALPHA = 0.5f
