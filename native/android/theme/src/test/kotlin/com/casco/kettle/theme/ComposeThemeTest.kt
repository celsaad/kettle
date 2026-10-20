package com.casco.kettle.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ComposeThemeTest {
    /** A theme.json with every group at its smallest, each overridable as the JSON inside its braces. */
    private fun theme(
        light: String = """"text": "#26221c"""",
        dark: String = """"text": "#f0ece2"""",
        runner: String = """"text": "#f3efe4"""",
        spacing: String = """"half": 2""",
        extra: String = "",
    ) = """
        {
          "Colors": { "light": { $light }, "dark": { $dark } },
          "RunnerColors": { $runner },
          "Spacing": { $spacing },
          "MaxContentWidth": 480$extra
        }
        """.trimIndent()

    @Test
    fun `every group becomes its Compose counterpart`() {
        assertEquals(
            """
            // Generated from src/constants/theme.json by :theme. Edit theme.json, not this file.
            package com.casco.kettle.shell.theme

            import androidx.compose.runtime.Immutable
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp

            /** `Colors.light` and `Colors.dark`: one token set, remapped per scheme. */
            @Immutable
            data class KettleColors(
                val text: Color,
            ) {
                /** Every token by its theme.json name, in file order. */
                internal val tokens =
                    listOf(
                        "text" to text,
                    )
            }

            internal val LightColors =
                KettleColors(
                    text = Color(0xFF26221C),
                )

            internal val DarkColors =
                KettleColors(
                    text = Color(0xFFF0ECE2),
                )

            /** `RunnerColors`: the live session runner's, dark whatever the system scheme. */
            object RunnerColors {
                val text = Color(0xFFF3EFE4)

                /** Every token by its theme.json name, in file order. */
                internal val tokens =
                    listOf(
                        "text" to text,
                    )
            }

            object Spacing {
                val half = 2.dp

                /** Every token by its theme.json name, in file order. */
                internal val tokens =
                    listOf(
                        "half" to half,
                    )
            }

            val MaxContentWidth = 480.dp

            """.trimIndent(),
            generateTheme(theme()),
        )
    }

    /** The generated lines assigning [token], trimmed. */
    private fun lines(
        kotlin: String,
        token: String,
    ) = kotlin.lines().map { it.trim() }.filter { it.startsWith("$token =") || it.startsWith("val $token =") }

    @Test
    fun `a translucent color rounds its alpha the way React Native does`() {
        // Math.round(0.10 * 255) is 26 (0x1A), and Math.round(0.06 * 255) is 15 (0x0F).
        val kotlin =
            generateTheme(
                theme(
                    light = """"text": "#26221c", "border": "rgba(30,25,15,0.10)"""",
                    dark = """"text": "#f0ece2", "border": "rgba(240, 236, 226, 0.06)"""",
                ),
            )

        assertEquals(listOf("border = Color(0x1A1E190F),", "border = Color(0x0FF0ECE2),"), lines(kotlin, "border"))
    }

    @Test
    fun `a hex color is opaque whatever its case`() {
        val kotlin = generateTheme(theme(runner = """"text": "#F3efE4""""))

        assertEquals(listOf("val text = Color(0xFFF3EFE4)"), lines(kotlin, "text").filter { it.startsWith("val") })
    }

    private fun refusal(json: String) = assertThrows<IllegalArgumentException> { generateTheme(json) }.message.orEmpty()

    @Test
    fun `a group the theme doesn't know fails the build rather than going missing`() {
        val message = refusal(theme(extra = """, "Radius": { "card": 12 }"""))

        assertTrue("Radius" in message, message)
    }

    @Test
    fun `a token in one scheme but not the other is refused`() {
        val message = refusal(theme(dark = """"text": "#f0ece2", "border": "rgba(240,236,226,0.10)""""))

        assertTrue("the same tokens" in message, message)
    }

    @Test
    fun `a value of the wrong type for its group is refused`() {
        val cases =
            listOf(
                theme(runner = """"text": 3""") to "RunnerColors.text",
                theme(runner = """"text": { "value": "#ffffff" }""") to "RunnerColors.text",
                theme(spacing = """"half": "2"""") to "Spacing.half",
                theme(light = """"text": null""", dark = """"text": null""") to "Colors.light.text",
            )
        for ((json, token) in cases) {
            val message = refusal(json)

            assertTrue(token in message, message)
        }
    }

    @Test
    fun `a color in a form the generator doesn't convert is refused`() {
        val colors =
            listOf("#fff", "#26221cff", "rgb(30, 25, 15)", "rgba(30,25,15,1.5)", "rgba(300,25,15,0.1)", "tomato")
        for (color in colors) {
            val message = refusal(theme(runner = """"text": "$color""""))

            assertTrue(message.startsWith(color), "$color: $message")
        }
    }

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    /**
     * The shipping theme.json, through the generator: every token in it comes out as a declaration,
     * once per group it sits in. Read here with the JSON library directly, so a token the generator
     * skipped shows up as a shortfall.
     */
    @Test
    fun `the real theme json generates a declaration for every token`() {
        val json = File(checkNotNull(System.getProperty("kettle.themeFile")) { "set by Gradle" }).readText()
        val root = Json.parseToJsonElement(json).jsonObject
        val groups =
            listOf(
                root.obj("Colors").obj("light"),
                root.obj("Colors").obj("dark"),
                root.obj("RunnerColors"),
                root.obj("Spacing"),
            )
        val expected = groups.flatMap { it.keys }

        // An argument to LightColors or DarkColors, or a val of RunnerColors or Spacing.
        val kotlin = generateTheme(json)
        val declared =
            Regex(
                """^ +(?:val )?(\w+) = """,
                RegexOption.MULTILINE,
            ).findAll(kotlin).map { it.groupValues[1] }
        assertEquals(expected.sorted(), declared.toList().sorted())
        assertTrue("val MaxContentWidth = ${root.getValue("MaxContentWidth")}.dp" in kotlin, kotlin)
    }
}
