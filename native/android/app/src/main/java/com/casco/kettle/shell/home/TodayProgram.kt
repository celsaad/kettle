package com.casco.kettle.shell.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ProgramPosition
import com.casco.kettle.domain.SlotPlace
import com.casco.kettle.domain.StripSlot
import com.casco.kettle.domain.weekStrip
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

// Today's program (#412): the row naming it and the week, and the strip of that week's slots.

/**
 * The program the card's slot is from and its week, "Dumbbell Strength / Week 3 of 4", opening the
 * program. One button, named by its own words; the chevron is decoration.
 */
@Composable
internal fun ProgramRow(
    position: ProgramPosition,
    onOpen: () -> Unit,
) {
    val colors = KettleTheme.colors
    Row(
        Modifier
            .padding(top = Spacing.three)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // User data, verbatim.
            Text(position.program.name, style = KettleType.heading, color = colors.text)
            Text(
                stringResource(R.string.today_programWeek, position.weekCount, position.weekNumber),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
        Chevron()
    }
}

/**
 * The week's slots numbered 1–N in file order, seven to a line, and not a control. Position, not
 * completion (#408): a training slot behind the card's gets a check, the card's slot is outlined, rest
 * is drawn in the calm blue throughout. Each slot carries its own name for a screen reader, since the
 * marks are drawn, and the strip says what it is once.
 */
@Composable
internal fun WeekStrip(position: ProgramPosition) {
    val slots = remember(position) { weekStrip(position) }
    val label = stringResource(R.string.today_strip_label)
    Column(
        Modifier
            .padding(top = Spacing.two)
            .fillMaxWidth()
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(StripGap),
    ) {
        // Equal widths on every line, so a week of nine reads as seven and two rather than two wide ones.
        val lines = slots.chunked(SLOTS_PER_LINE)
        lines.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StripGap)) {
                line.forEach { StripCell(it, Modifier.weight(1f)) }
                if (lines.size > 1) repeat(SLOTS_PER_LINE - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StripCell(
    slot: StripSlot,
    modifier: Modifier,
) {
    val colors = KettleTheme.colors
    val current = slot.place == SlotPlace.Current
    val ink = if (slot.rest) colors.accentCalmText else colors.text
    val name = slot.day?.takeIf { it.isNotEmpty() } ?: slot.number.toString()
    val description = stripLabel(slot, name)
    Column(
        modifier
            .heightIn(min = 40.dp)
            .then(if (current) Modifier.border(2.dp, ink, StripShape) else Modifier)
            .padding(vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(slot.number.toString(), style = KettleType.small, color = colors.textSecondary, maxLines = 1)
        StripMark(slot, ink)
    }
}

/** ✓ behind, a dot for the next workout, a dash for rest, nothing for a workout still ahead. */
@Composable
private fun StripMark(
    slot: StripSlot,
    ink: Color,
) {
    val box = Modifier.size(MarkSize)
    when {
        slot.rest -> {
            Box(box, contentAlignment = Alignment.Center) {
                Box(Modifier.size(8.dp, 2.dp).background(ink))
            }
        }

        slot.place == SlotPlace.Behind -> {
            TypeIcon(R.drawable.icon_check, KettleType.default, ink, box)
        }

        slot.place == SlotPlace.Current -> {
            Box(box, contentAlignment = Alignment.Center) {
                Box(Modifier.size(8.dp).background(ink, CircleShape))
            }
        }

        else -> {
            Spacer(box)
        }
    }
}

@Composable
private fun stripLabel(
    slot: StripSlot,
    name: String,
): String =
    when {
        slot.rest && slot.place == SlotPlace.Current -> stringResource(R.string.today_strip_restToday, name)
        slot.rest -> stringResource(R.string.today_strip_rest, name)
        slot.place == SlotPlace.Behind -> stringResource(R.string.today_strip_done, name)
        slot.place == SlotPlace.Current -> stringResource(R.string.today_strip_next, name)
        else -> name
    }

/** Seven to a line, a week as most people keep one; a longer week wraps. */
private const val SLOTS_PER_LINE = 7
private val StripGap = 4.dp
private val StripShape = RoundedCornerShape(10.dp)
private val MarkSize = 16.dp
