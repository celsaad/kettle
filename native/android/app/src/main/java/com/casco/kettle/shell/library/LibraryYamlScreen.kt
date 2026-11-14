package com.casco.kettle.shell.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.serializeLibraryYaml
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/** What the route table draws at `Destination.LibraryYaml`. */
@Composable
fun LibraryYamlRoute() = LibraryYamlScreen(LocalLatestFiles.current)

/**
 * The library as YAML, read-only (#413): the app's own serialisation, so it reads as an export would
 * rather than as the file was last hand-written, and selectable, so a part can be copied out. Weights
 * are the file's kilograms whatever the reader's units, and it says so above them. No editing here.
 */
@Composable
fun LibraryYamlScreen(files: LatestFiles) {
    val colors = KettleTheme.colors
    val library = files.library
    val yaml = remember(library) { library?.let(::serializeLibraryYaml) }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            SheetColumn
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.four)
                .navigationBarsPadding(),
        ) {
            FileNameTitle()
            Text(stringResource(R.string.file_unitsNote), style = KettleType.small, color = colors.textSecondary)
            yaml?.let {
                SelectionContainer(Modifier.padding(top = Spacing.three).horizontalScroll(rememberScrollState())) {
                    Text(
                        it,
                        style =
                            KettleType.small.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                            ),
                        color = colors.text,
                    )
                }
            }
        }
    }
}
