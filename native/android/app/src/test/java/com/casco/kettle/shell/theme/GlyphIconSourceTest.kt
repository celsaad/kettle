package com.casco.kettle.shell.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * No control icon drawn as a character in a `Text` (#267): its shape, weight and baseline came from
 * whichever font the device resolved for the code point, so it changed by OEM and locale, and `✎` was a
 * colour emoji on some phones. A source scan rather than a UI test, so a screen ported later is held
 * to it too without anyone remembering to write one.
 */
class GlyphIconSourceTest {
    @Test
    fun `no Text in the app's sources draws a glyph icon`() {
        // A one-character non-ASCII literal (with any emoji variation selector), or a lone +: as a Text's
        // first argument, named or not, or either branch of an if; set as a View's text; returned by a
        // when branch; or held in a val, which is how the sheet's × and the keypad's ⌫ hid from a scan of
        // the calls alone. Neither is ever a word, so every hit is an icon. A glyph built at run time
        // still gets past it: the price of a scan over a UI test.
        val glyph = """"(?:[^\x00-\x7F]\x{FE0F}?|\+)""""
        val inText = """\bText\(\s*(?:text\s*=\s*)?(?:if \(.*?\) (?:"[^"]*" else )?)?$glyph"""
        val onView = """\btext = $glyph"""
        val inWhen = """-> $glyph"""
        val held = """\bval \w+(?::\s*String)? = $glyph"""
        val glyphIcon = Regex("$inText|$onView|$inWhen|$held")
        val hits =
            File("src/main/java")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .flatMap { file ->
                    // The whole file, not line by line: a wrapped call puts its glyph on the next line.
                    val text = file.readText()
                    glyphIcon.findAll(text).map { hit ->
                        val line = text.take(hit.range.first).count { it == '\n' } + 1
                        "${file.name}:$line"
                    }
                }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}
