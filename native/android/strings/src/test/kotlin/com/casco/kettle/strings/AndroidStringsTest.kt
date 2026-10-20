package com.casco.kettle.strings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AndroidStringsTest {
    private fun english(json: String) =
        generateResources(mapOf("en" to json), defaultLanguage = "en").getValue("values")

    /** The one resource line a single-key bundle renders to, without the file around it. */
    private fun line(value: String) =
        english("""{ "k": ${kotlinx.serialization.json.JsonPrimitive(value)} }""")
            .lines()
            .single { it.trimStart().startsWith("<string") }
            .trim()

    @Test
    fun `a nested key becomes one string named by its path`() {
        val xml = english("""{ "common": { "save": "Save" } }""")

        assertEquals(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Generated from src/i18n/locales by :strings. Edit the JSON bundles, not this file. -->
            <resources>
                <string name="common_save">Save</string>
            </resources>

            """.trimIndent(),
            xml,
        )
    }

    @Test
    fun `characters aapt2 reads as syntax are escaped`() {
        assertEquals("""<string name="k">Don\'t say \"hi\" \\ here</string>""", line("""Don't say "hi" \ here"""))
        assertEquals("""<string name="k">Steady &amp; Strong &lt; 5 &gt; 3</string>""", line("Steady & Strong < 5 > 3"))
        assertEquals("""<string name="k">one\ntwo\tthree</string>""", line("one\ntwo\tthree"))
    }

    @Test
    fun `a leading at sign or question mark is text, not a resource reference`() {
        assertEquals("""<string name="k">\@home</string>""", line("@home"))
        assertEquals("""<string name="k">\?attr</string>""", line("?attr"))
        assertEquals("""<string name="k">mid @ and ?</string>""", line("mid @ and ?"))
    }

    @Test
    fun `whitespace aapt2 would trim or collapse is kept by quoting the value`() {
        // `format.repsWeight` is " · {{weight}} {{unit}}": appended to another string, so its space is load-bearing.
        assertEquals("""<string name="k">" · kg"</string>""", line(" · kg"))
        assertEquals("""<string name="k">"a  b"</string>""", line("a  b"))
        assertEquals("""<string name="k">"end "</string>""", line("end "))
    }

    @Test
    fun `the c tag the help screens style as code stays markup`() {
        assertEquals("""<string name="k">the <c>id</c> field</string>""", line("the <c>id</c> field"))
    }

    @Test
    fun `a percent sign in a string with nothing to format is left alone`() {
        assertEquals("""<string name="k" formatted="false">100%</string>""", line("100%"))
    }

    private fun languages(vararg bundles: Pair<String, String>) =
        generateResources(mapOf(*bundles), defaultLanguage = "en")

    @Test
    fun `placeholders are numbered alphabetically, so rewording the English can't reorder a call site`() {
        val xml = english("""{ "k": "{{week}} of {{name}}, {{week}} again" }""")

        assertEquals(
            """
            <!-- %1${'$'}s = name, %2${'$'}s = week -->
            <string name="k">%2${'$'}s of %1${'$'}s, %2${'$'}s again</string>
            """.trimIndent(),
            body(xml),
        )
    }

    @Test
    fun `a plain key beside plural forms of the same name fails the build, in either order`() {
        // i18next would read `x` when no form matches; here one resource would silently replace the other.
        val error = assertThrows<IllegalArgumentException> { english("""{ "x": "a", "x_one": "b", "x_other": "c" }""") }
        assertEquals("x is both a plain string and a plural", error.message)
        assertThrows<IllegalArgumentException> { english("""{ "x_one": "b", "x_other": "c", "x": "a" }""") }
    }

    @Test
    fun `a key that is a plural in English and a plain string in a translation fails the build`() {
        // The call site reads one resource type; the other language's would be a different type and
        // never be found, so the screen would fall back to English with no error anywhere.
        val en = """{ "k_one": "{{count}} set", "k_other": "{{count}} sets" }"""
        val error = assertThrows<IllegalArgumentException> { languages("en" to en, "pt" to """{ "k": "séries" }""") }
        assertEquals("pt k: a plural in English but a plain string here", error.message)

        val reverse = assertThrows<IllegalArgumentException> { languages("en" to """{ "k": "sets" }""", "pt" to en) }
        assertEquals("pt k: a plain string in English but a plural here", reverse.message)
    }

    @Test
    fun `a translation takes its argument numbers from English, whatever order it uses them in`() {
        val pt = languages("en" to """{ "k": "{{week}} of {{name}}" }""", "pt" to """{ "k": "{{name}}: {{week}}" }""")

        assertTrue("""<string name="k">%1${'$'}s: %2${'$'}s</string>""" in pt.getValue("values-pt"))
    }

    @Test
    fun `a percent sign beside a placeholder is doubled, since that string goes through String format`() {
        assertEquals("""<string name="k">%1${'$'}s%%</string>""", line("{{n}}%"))
    }

    @Test
    fun `a translation naming a placeholder English doesn't have fails the build`() {
        // i18next would print the braces literally; on Android it would be a missing argument.
        val error =
            assertThrows<IllegalArgumentException> {
                languages("en" to """{ "a": { "k": "Hi {{name}}" } }""", "pt" to """{ "a": { "k": "Oi {{nome}}" } }""")
            }
        assertEquals("pt a.k: {{nome}} isn't a placeholder in English", error.message)
    }

    @Test
    fun `interpolation syntax the generator can't translate fails the build`() {
        assertThrows<IllegalArgumentException> { line("{{- name}}") }
        assertThrows<IllegalArgumentException> { line("{{count, number}}") }
    }

    /** A bundle's resources after the file header, as the indented lines between `<resources>` tags. */
    private fun body(xml: String) =
        xml
            .lines()
            .filter { it.startsWith("    ") }
            .joinToString("\n")
            .trimIndent()

    @Test
    fun `a plural becomes plurals with count as its first argument, and an empty zero branch`() {
        val xml =
            english(
                """{ "f": { "session_one": "{{count}} in {{week}}", "session_other": "{{count}}s in {{week}}" } }""",
            )

        assertEquals(
            """
            <!-- %1${'$'}s = count, %2${'$'}s = week -->
            <plurals name="f_session">
                <item quantity="one">%1${'$'}s in %2${'$'}s</item>
                <item quantity="other">%1${'$'}ss in %2${'$'}s</item>
            </plurals>
            <!-- What pluralText shows for a count of 0, ahead of the plural rule. Empty: this bundle has no _zero. -->
            <string name="f_session_zero"></string>
            """.trimIndent(),
            body(xml),
        )
    }

    @Test
    fun `a _zero key becomes the zero branch, not a plural item Android would never select`() {
        // CLDR files Portuguese 0 under `one`, so a `quantity="zero"` item would never be read: the
        // "0 sessão" bug the RN bundles already guard against.
        val en = """{ "s_one": "{{count}} session", "s_other": "{{count}} sessions" }"""
        val pt = """{ "s_one": "{{count}} sessão", "s_other": "{{count}} sessões", "s_zero": "{{count}} sessões" }"""
        val xml = languages("en" to en, "pt" to pt).getValue("values-pt")

        assertEquals(
            """
            <!-- %1${'$'}s = count -->
            <plurals name="s">
                <item quantity="one">%1${'$'}s sessão</item>
                <item quantity="other">%1${'$'}s sessões</item>
            </plurals>
            <!-- What pluralText shows for a count of 0, ahead of the plural rule. -->
            <string name="s_zero">%1${'$'}s sessões</string>
            """.trimIndent(),
            body(xml),
        )
    }

    @Test
    fun `plural items come out in CLDR order whatever order the bundle lists them`() {
        val xml = english("""{ "s_other": "many", "s_one": "one" }""")

        assertTrue(body(xml).indexOf("quantity=\"one\"") < body(xml).indexOf("quantity=\"other\""))
    }

    @Test
    fun `two keys that would share one resource name fail the build`() {
        val error =
            assertThrows<IllegalArgumentException> { english("""{ "a": { "b_c": "x" }, "a_b": { "c": "y" } }""") }
        assertEquals("a.b_c and a_b.c would both be the resource a_b_c", error.message)
    }

    @Test
    fun `a hyphen in a key, as content pack ids have, becomes an underscore`() {
        val xml = english("""{ "packs": { "steady-strength": { "name": "Steady" } } }""")

        assertEquals("""<string name="packs_steady_strength_name">Steady</string>""", body(xml))
    }
}
