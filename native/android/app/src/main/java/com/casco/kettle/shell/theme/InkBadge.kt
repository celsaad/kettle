package com.casco.kettle.shell.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A record's mark, as the boards draw it: [text] in the page's own colour, [field], on an [ink] pill.
 * Achievement takes no hue (the decision log), so the mark is ink wherever it sits. History's PR badge
 * and the runner's (#475) are this one, so the two can't drift apart. Text, so a row's merged name reads
 * it with the rest.
 */
@Composable
fun InkBadge(
    text: String,
    ink: Color,
    field: Color,
) {
    Text(
        text,
        Modifier
            .clip(BadgeShape)
            .background(ink)
            .padding(horizontal = Spacing.one + 2.dp, vertical = 1.dp),
        style = KettleType.code.copy(fontWeight = FontWeight.Bold),
        color = field,
    )
}

private val BadgeShape = RoundedCornerShape(6.dp)
