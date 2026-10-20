package com.casco.kettle.shell.i18n

import android.content.Context
import android.icu.text.PluralRules
import android.icu.util.ULocale
import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.shell.R
import com.casco.kettle.strings.Entry
import com.casco.kettle.strings.PluralEntry
import com.casco.kettle.strings.StringEntry
import com.casco.kettle.strings.parseBundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    /**
     * Every string and plural form in every bundle, read back in its own locale and formatted with each
     * placeholder's own `{{name}}` as its argument, so a correct round trip reproduces the JSON exactly.
     * It finds the bundles and each language's plural counts itself, so a new language is covered the
     * day its JSON lands. `<c>` is markup to Android, so `getString` drops it.
     */
    @Test
    fun `every string in every bundle reads back as its bundle wrote it`() {
        val locales = File(checkNotNull(System.getProperty("kettle.localesDir")) { "set by Gradle" })
        val english = parseBundle(locales.resolve("en.json").readText()).associateBy { it.key }
        val bundles = locales.listFiles { file -> file.extension == "json" }.orEmpty()
        check(bundles.size > 1) { "no translations found in $locales" }

        val mismatches =
            bundles.flatMap { bundle ->
                val language = bundle.nameWithoutExtension
                RuntimeEnvironment.setQualifiers(language)
                parseBundle(bundle.readText()).flatMap { entry ->
                    roundTrip(language, entry, english.getValue(entry.key).arguments)
                }
            }

        assertEquals(emptyList<Mismatch>(), mismatches)
    }

    private data class Mismatch(
        val key: String,
        val expected: String,
        val actual: String,
    )

    private fun roundTrip(
        language: String,
        entry: Entry,
        args: List<String>,
    ): List<Mismatch> {
        val pkg = ApplicationProvider.getApplicationContext<Context>().packageName

        fun id(
            name: String,
            type: String,
        ) = resources.getIdentifier(name, type, pkg)

        val results =
            when (entry) {
                is StringEntry -> {
                    val id = id(entry.name, "string")
                    val actual =
                        if (args.isEmpty()) {
                            resources.getString(
                                id,
                            )
                        } else {
                            resources.getString(id, *placeholders(args))
                        }
                    listOf(Mismatch("$language ${entry.key}", entry.value.withoutTags(), actual))
                }

                is PluralEntry -> {
                    entry.forms.map { (category, form) ->
                        val count = sampleCount(language, category)
                        val actual =
                            resources.pluralText(
                                id(entry.name, "plurals"),
                                id("${entry.name}_zero", "string"),
                                count,
                                *placeholders(args.drop(1)),
                            )
                        Mismatch(
                            "$language ${entry.key}_$category",
                            form.replace("{{count}}", "$count").withoutTags(),
                            actual,
                        )
                    }
                }
            }
        return results.filter { it.expected != it.actual }
    }

    /** 0 for `zero`, which `pluralText` handles itself; otherwise a whole number CLDR files under [category]. */
    private fun sampleCount(
        language: String,
        category: String,
    ): Int {
        if (category == "zero") return 0
        val samples = PluralRules.forLocale(ULocale(language)).getSamples(category).orEmpty()
        val sample = samples.firstOrNull { it != 0.0 && it % 1 == 0.0 }
        return checkNotNull(sample) { "$language has no whole-number sample for $category" }.toInt()
    }

    private fun placeholders(names: List<String>) = names.map { "{{$it}}" }.toTypedArray()

    private fun String.withoutTags() = replace("<c>", "").replace("</c>", "")
}
