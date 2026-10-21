package com.casco.kettle.shell.i18n

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.strings.PluralEntry
import com.casco.kettle.strings.parseBundle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** What the locale previews show, rendered in each language the way Studio renders them. */
@RunWith(RobolectricTestRunner::class)
class LocalePreviewsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun assertShown(vararg texts: String) {
        compose.setContent { GeneratedStringsSample(exerciseName = "Turkish get-up") }
        for (text in texts) compose.onNodeWithText(text).assertExists()
    }

    @Test
    fun `the preview renders in English`() =
        assertShown("Save", "\"Turkish get-up\" will be permanently removed.", "0 sessions", "1 session", "2 sessions")

    @Test
    @Config(qualifiers = "pt")
    fun `the preview renders in Portuguese, with a plural zero`() =
        assertShown("Salvar", "\"Turkish get-up\" será removido permanentemente.", "0 sessões", "1 sessão", "2 sessões")

    @Test
    @Config(qualifiers = "ja")
    fun `the preview renders in Japanese`() =
        assertShown("保存", "「Turkish get-up」を完全に削除します。", "0セッション", "1セッション", "2セッション")

    @Test
    fun `the catalogue lists every key in the English bundle, plurals with their zero branch`() {
        val locales = File(checkNotNull(System.getProperty("kettle.localesDir")) { "set by Gradle" })
        val expected =
            parseBundle(locales.resolve("en.json").readText())
                .flatMap { if (it is PluralEntry) listOf(it.name, "${it.name}_zero") else listOf(it.name) }
                .toSet()

        assertEquals(expected, generatedResources().map { it.first }.toSet())
    }

    @Test
    @Config(qualifiers = "pt")
    fun `the catalogue renders`() {
        compose.setContent { GeneratedStringsCatalogue() }
        compose.onNodeWithText("${generatedResources().first().first}: ", substring = true).assertExists()
    }
}
