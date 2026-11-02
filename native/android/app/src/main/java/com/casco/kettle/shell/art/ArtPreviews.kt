package com.casco.kettle.shell.art

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.Spacing

/** Each drawing at its viewport width in dp, so strokes land at the weight they were drawn at. */
private val SheetCell = 120.dp

/**
 * Every generated drawing once, tinted the way a screen will tint it, and labelled with the first id
 * that uses it: the eyeball check against the same drawings in RN's web build, which no test can
 * make. The labels are developer-facing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtContactSheet() {
    val drawings = artDrawables.values.distinct()
    FlowRow(Modifier.padding(Spacing.three), horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        for (drawable in drawings) {
            Column(Modifier.width(SheetCell), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(drawable),
                    contentDescription = null,
                    modifier = Modifier.width(SheetCell),
                    colorFilter = ColorFilter.tint(KettleTheme.colors.textSecondary),
                )
                Text(artDrawables.entries.first { it.value == drawable }.key)
            }
        }
    }
}

@Preview(name = "light", uiMode = UI_MODE_NIGHT_NO, widthDp = 800, heightDp = 1400)
@Preview(name = "dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 800, heightDp = 1400)
@Composable
private fun ArtContactSheetPreview() {
    KettleTheme { Surface { ArtContactSheet() } }
}
