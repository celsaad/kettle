package com.casco.kettle.shell.theme

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

/**
 * Every token of the theme as a swatch, by its theme.json name, read off the generated `tokens` so a
 * token added there shows up here with no edit. The names are developer-facing, not UI copy.
 */
@Composable
internal fun ThemeCatalogue(colors: List<Pair<String, Color>>) {
    Column(Modifier.padding(Spacing.three), verticalArrangement = Arrangement.spacedBy(Spacing.two)) {
        for ((name, color) in colors) {
            Swatch(name) {
                val outline = MaterialTheme.colorScheme.outline
                Box(Modifier.size(Spacing.five).background(color).border(Dp.Hairline, outline))
            }
        }
        for ((name, size) in Spacing.tokens) {
            Swatch("Spacing.$name") {
                Box(Modifier.width(size).height(Spacing.five).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    sample: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        sample()
        Text(name)
    }
}

@Preview(name = "light", uiMode = UI_MODE_NIGHT_NO)
@Preview(name = "dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun KettleColorsPreview() {
    KettleTheme { Surface { ThemeCatalogue(KettleTheme.colors.tokens) } }
}

/** Under both system schemes, to show the runner ignoring them. */
@Preview(name = "runner, light system", uiMode = UI_MODE_NIGHT_NO)
@Preview(name = "runner, dark system", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RunnerColorsPreview() {
    KettleTheme { RunnerTheme { Surface { ThemeCatalogue(RunnerColors.tokens) } } }
}
