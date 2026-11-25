package com.casco.kettle.shell.programdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ProgramPosition
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.rotation
import com.casco.kettle.shell.R
import com.casco.kettle.shell.home.weekLabel
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card

/**
 * The program's next slot (#437), the one Today would show for it: "Next · Week 3 · Wednesday", its
 * workout, its note to two lines, then Start and the preview, which carries the note in full. A rest
 * slot says so and offers neither, having nothing to run or preview.
 */
@Composable
internal fun NextCard(
    position: ProgramPosition,
    slot: SlotActions,
) {
    val colors = KettleTheme.colors
    val next = rotation(position.program)[position.index]
    Column(
        Modifier.fillMaxWidth().card().padding(Spacing.three),
        verticalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Text(
            stringResource(R.string.programDetail_nextSlot, weekLabel(next.week, next.day)),
            style = KettleType.smallMedium,
            color = colors.textSecondary,
        )
        // User data, verbatim.
        Text(
            when (next) {
                is ProgramWeek.Training -> slot.workoutName(next)
                is ProgramWeek.RestDay -> stringResource(R.string.programDetail_restDay)
            },
            style = KettleType.heading,
            color = colors.text,
        )
        next.notes?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = KettleType.small,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (next is ProgramWeek.Training) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                CardButton(stringResource(R.string.programDetail_start), filled = true) { slot.run(next) }
                CardButton(stringResource(R.string.programDetail_preview), filled = false) { slot.preview(next) }
            }
        }
    }
}

/** Start filled in the accent, the preview outlined: two equal halves, each named by its words. */
@Composable
private fun RowScope.CardButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .then(
                if (filled) {
                    Modifier.background(
                        colors.accent,
                    )
                } else {
                    Modifier.border(1.dp, colors.textSecondary, ButtonShape)
                },
            ).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.two, vertical = Spacing.one),
        contentAlignment = Alignment.Center,
    ) {
        // The heading's size, as the board draws it; white on the accent is 4.99:1 (theme-notes.md).
        Text(
            label,
            style = KettleType.heading,
            color = if (filled) colors.onAccent else colors.text,
            textAlign = TextAlign.Center,
        )
    }
}

private val ButtonShape = RoundedCornerShape(14.dp)
