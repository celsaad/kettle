package com.casco.kettle.shell.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.shell.R
import com.casco.kettle.strings.Plural
import com.casco.kettle.strings.Text
import com.casco.kettle.strings.parseBundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The generated resources read back through Android's own resource stack, against the bundles they
 * came from. What aapt2 does with an escape is the part the generator's own tests can only assume.
 */
@RunWith(RobolectricTestRunner::class)
class GeneratedStringsTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    @Config(qualifiers = "pt")
    fun `a Portuguese count of 0 reads as plural, not as one`() {
        // The rule the zero branch exists for: Android's plural selection alone gives the singular.
        assertEquals("0 sessão", resources.getQuantityString(R.plurals.format_session, 0, 0))

        assertEquals("0 sessões", resources.pluralText(R.plurals.format_session, R.string.format_session_zero, 0))
        assertEquals("1 sessão", resources.pluralText(R.plurals.format_session, R.string.format_session_zero, 1))
        assertEquals("2 sessões", resources.pluralText(R.plurals.format_session, R.string.format_session_zero, 2))
    }

    @Test
    fun `an English count of 0 falls through the empty zero branch to the plural rule`() {
        assertEquals("0 sessions", resources.pluralText(R.plurals.format_session, R.string.format_session_zero, 0))
        assertEquals("1 session", resources.pluralText(R.plurals.format_session, R.string.format_session_zero, 1))
    }

    @Test
    @Config(qualifiers = "ja")
    fun `user data is interpolated verbatim, untranslated and unformatted`() {
        // An exercise name is the user's own text: quotes, a percent sign and a format specifier included.
        val name = "Mike's \"100%\" swing %1\$s"
        assertEquals("「$name」を完全に削除します。", resources.getString(R.string.common_deleteConfirmBody, name))
    }

    @Test
    fun `every English string reads back as its bundle wrote it`() = assertRoundTrip("en")

    @Test
    @Config(qualifiers = "pt")
    fun `every Portuguese string reads back as its bundle wrote it`() = assertRoundTrip("pt")

    @Test
    @Config(qualifiers = "ja")
    fun `every Japanese string reads back as its bundle wrote it`() = assertRoundTrip("ja")

    /**
     * Every string and plural form in the bundle, formatted with each placeholder's own `{{name}}` as
     * its argument, so a correct round trip reproduces the JSON exactly. `<c>` is markup to Android, so
     * `getString` drops it.
     */
    private fun assertRoundTrip(language: String) {
        val locales =
            File(
                checkNotNull(
                    System.getProperty("kettle.localesDir"),
                ) { "run through Gradle, which sets kettle.localesDir" },
            )
        val english = parseBundle(locales.resolve("en.json").readText()).associateBy { it.key }
        val entries = parseBundle(locales.resolve("$language.json").readText())
        val res = resources
        val pkg = ApplicationProvider.getApplicationContext<Context>().packageName
        val counts = mapOf("zero" to 0, "one" to 1, "other" to if (language == "ja") 1 else 2)

        val mismatches =
            entries
                .flatMap { entry ->
                    val args = argumentsOf(english.getValue(entry.key))
                    when (entry) {
                        is Text -> {
                            val id = res.getIdentifier(entry.name, "string", pkg)
                            val actual =
                                if (args.isEmpty()) {
                                    res.getString(
                                        id,
                                    )
                                } else {
                                    res.getString(id, *placeholders(args))
                                }
                            listOf(Triple(entry.key, entry.value.withoutTags(), actual))
                        }

                        is Plural -> {
                            entry.forms.map { (category, form) ->
                                val count = counts.getValue(category)
                                val rest = placeholders(args.drop(1))
                                val actual =
                                    res.pluralText(
                                        res.getIdentifier(entry.name, "plurals", pkg),
                                        res.getIdentifier("${entry.name}_zero", "string", pkg),
                                        count,
                                        *rest,
                                    )
                                Triple(
                                    "${entry.key}_$category",
                                    form.replace("{{count}}", "$count").withoutTags(),
                                    actual,
                                )
                            }
                        }
                    }
                }.filter { (_, expected, actual) -> expected != actual }

        assertEquals(emptyList<Triple<String, String, String>>(), mismatches)
    }

    private fun placeholders(names: List<String>) = names.map { "{{$it}}" }.toTypedArray()

    private fun String.withoutTags() = replace("<c>", "").replace("</c>", "")

    /** The generator's argument order, restated: alphabetical, with a plural's `count` first. */
    private fun argumentsOf(entry: com.casco.kettle.strings.Entry): List<String> {
        val values = if (entry is Plural) entry.forms.values else listOf((entry as Text).value)
        val names = values.flatMap { PLACEHOLDER.findAll(it).map { m -> m.groupValues[1] } }.toSet()
        return if (entry is Plural) listOf("count") + (names - "count").sorted() else names.sorted()
    }

    private companion object {
        val PLACEHOLDER = Regex("""\{\{(\w+)}}""")
    }
}
