package com.casco.kettle.shell.i18n

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.casco.kettle.shell.R

/** One preview per shipped language. A new language needs a line here as well as its bundle. */
@Preview(name = "en", locale = "en")
@Preview(name = "pt", locale = "pt")
@Preview(name = "ja", locale = "ja")
annotation class LocalePreviews

/**
 * A plain string, one carrying user data, and a plural at 0, 1 and 2, read from the generated
 * resources. M1's landmark for resources is a key added to `en.json` showing up here in all three
 * languages with no hand edit. The exercise name is the user's own text and stays untranslated.
 */
@Composable
internal fun GeneratedStringsSample(exerciseName: String = "Turkish get-up") {
    Column {
        Text(stringResource(R.string.common_save))
        Text(stringResource(R.string.common_deleteConfirmBody, exerciseName))
        for (count in 0..2) {
            Text(pluralText(R.plurals.format_session, R.string.format_session_zero, count))
        }
    }
}

@LocalePreviews
@Composable
private fun GeneratedStringsPreview() {
    Surface { GeneratedStringsSample() }
}
