package com.casco.kettle.shell.i18n

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
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
 * resources: the three shapes a call site uses. The exercise name is the user's own text and stays
 * untranslated.
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

/**
 * Every generated string and plural by resource name, read off `R` itself, so a key added to the
 * bundles shows up here with no edit. That is M1's landmark for resources. `R` holds only this module's
 * own resources (AGP's non-transitive R classes), which here are the generated ones plus the `expo_`
 * strings `app/build.gradle.kts` writes for Expo modules to read, as prebuild did. Arguments show as
 * their `%N$s`, and a plural as its `other` form.
 */
internal fun generatedResources(): List<Pair<String, Int>> =
    (R.string::class.java.fields + R.plurals::class.java.fields)
        .filterNot { it.name.startsWith("expo_") }
        .map { it.name to it.getInt(null) }
        .sortedBy { it.first }

@Composable
internal fun GeneratedStringsCatalogue() {
    val resources = LocalResources.current
    LazyColumn {
        items(generatedResources()) { (name, id) ->
            val text =
                if (resources.getResourceTypeName(id) == "plurals") {
                    resources.getQuantityString(id, Int.MAX_VALUE)
                } else {
                    resources.getString(id)
                }
            Text("$name: $text")
        }
    }
}

@LocalePreviews
@Composable
private fun GeneratedStringsPreview() {
    Surface { GeneratedStringsSample() }
}

@LocalePreviews
@Composable
private fun GeneratedStringsCataloguePreview() {
    Surface { GeneratedStringsCatalogue() }
}
