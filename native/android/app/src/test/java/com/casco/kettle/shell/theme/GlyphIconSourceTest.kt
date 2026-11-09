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
        val glyphIcon = Regex("""\bText\(\s*(?:if \(.*?\) )?"[⚙✎⌕›⌄]"""")
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
