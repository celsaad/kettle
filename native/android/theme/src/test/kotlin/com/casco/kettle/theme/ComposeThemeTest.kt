package com.casco.kettle.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ComposeThemeTest {
    /**
     * A theme.json with every group at its smallest, each overridable as the JSON inside its braces. One
     * parameter per group is the point, so a test overrides the group it's about and nothing else.
     */
    @Suppress("LongParameterList")
    private fun theme(
        light: String = """"text": "#26221c"""",
        dark: String = """"text": "#f0ece2"""",
        runner: String = """"text": "#f3efe4"""",
        spacing: String = """"half": 2""",
        fonts: String = """"body": "HankenGrotesk_400Regular", "display": "SpaceGrotesk_600SemiBold"""",
        type: String = """"small": { "font": "body", "fontSize": 13, "lineHeight": 18 }""",
    ) = """
        {
          "Colors": { "light": { $light }, "dark": { $dark } },
          "RunnerColors": { $runner },
          "Spacing": { $spacing },
          "MaxContentWidth": 480,
          "Fonts": { $fonts },
          "Type": { $type }
        }
        """.trimIndent()

    @Test
    fun `the file opens with its banner, its package and every import the groups need`() {
        assertEquals(
            """
            // Generated from src/constants/theme.json by :theme. Edit theme.json, not this file.
            package com.casco.kettle.shell.theme

            import androidx.compose.runtime.Immutable
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.text.TextStyle
            import androidx.compose.ui.text.font.Font
            import androidx.compose.ui.text.font.FontFamily
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp
            import com.casco.kettle.shell.R


            """.trimIndent(),
            generateTheme(theme()).substringBefore("/**"),
        )
    }

    @Test
    fun `every group becomes its Compose counterpart`() {
        assertEquals(
            """
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
            ("/**" + generateTheme(theme()).substringAfter("/**").substringBefore("/** `Fonts`")).trimEnd(),
        )
    }

    @Test
    fun `the fonts become a family per typeface, then the type scale KettleType, last in the file`() {
        assertEquals(
            """
            /** `Fonts`' typefaces, each a family of the faces theme.json names in it, from RN's packages. */
            private val HankenGrotesk =
                FontFamily(
                    Font(R.font.hankengrotesk_400regular, FontWeight.Normal),
                )

            private val SpaceGrotesk =
                FontFamily(
                    Font(R.font.spacegrotesk_600semibold, FontWeight.SemiBold),
                )

            /** `Type`: ThemedText's types, each in its `Fonts` role's family and weight. */
            object KettleType {
                val small = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

                /** Every token by its theme.json name, in file order. */
                internal val tokens =
                    listOf(
                        "small" to small,
                    )
            }


            """.trimIndent(),
            "/** `Fonts`" + generateTheme(theme()).substringAfter("/** `Fonts`"),
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
        val message =
            refusal(
                theme().replace("\"MaxContentWidth\": 480,", "\"MaxContentWidth\": 480, \"Radius\": { \"card\": 12 },"),
            )

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
    fun `a token name that can't be a Kotlin property is refused by name, not by the compiler`() {
        for (name in listOf("my-color", "2x", "val", "object", "tokens")) {
            val message = refusal(theme(runner = """"text": "#ffffff", "$name": "#000000""""))

            assertTrue("RunnerColors.$name" in message, "$name: $message")
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

    @Test
    fun `a type style takes its role's family and weight, and only the sizes it sets`() {
        val kotlin =
            generateTheme(
                theme(
                    type =
                        """"title": { "font": "display", "fontSize": 40, "letterSpacing": -0.8 }, """ +
                            """"numeral": { "font": "display" }""",
                ),
            )

        assertEquals(
            listOf(
                "val title = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, " +
                    "fontSize = 40.sp, letterSpacing = (-0.8).sp)",
            ),
            lines(kotlin, "title"),
        )
        assertEquals(
            listOf("val numeral = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold)"),
            lines(kotlin, "numeral"),
        )
    }

    @Test
    fun `a type style RN couldn't draw the same way is refused by name`() {
        val cases =
            listOf(
                """"small": { "font": "serif", "fontSize": 13 }""" to "Type.small.font",
                """"small": { "fontSize": 13 }""" to "Type.small.font",
                """"small": { "font": "body", "fontSize": "13" }""" to "Type.small.fontSize",
                """"small": { "font": "body", "fontWeight": 600 }""" to "Type.small",
                """"small": 13""" to "Type.small",
                """"val": { "font": "body" }""" to "Type.val",
            )
        for ((type, path) in cases) {
            val message = refusal(theme(type = type))

            assertTrue(path in message, "$type: $message")
        }
    }

    @Test
    fun `a family holds each face its roles name once, lightest first, in the order the families appear`() {
        val kotlin =
            generateTheme(
                theme(
                    fonts =
                        """"display": "SpaceGrotesk_600SemiBold", "body": "HankenGrotesk_400Regular", """ +
                            """"displayBold": "SpaceGrotesk_700Bold", "displayMedium": "SpaceGrotesk_500Medium", """ +
                            """"heading": "SpaceGrotesk_600SemiBold"""",
                ),
            )

        assertEquals(
            """
            private val SpaceGrotesk =
                FontFamily(
                    Font(R.font.spacegrotesk_500medium, FontWeight.Medium),
                    Font(R.font.spacegrotesk_600semibold, FontWeight.SemiBold),
                    Font(R.font.spacegrotesk_700bold, FontWeight.Bold),
                )

            private val HankenGrotesk =
                FontFamily(
                    Font(R.font.hankengrotesk_400regular, FontWeight.Normal),
                )
            """.trimIndent(),
            kotlin.substringAfter("from RN's packages. */\n").substringBefore("\n\n/**"),
        )
    }

    @Test
    fun `a font that isn't one of RN's packaged faces is refused by its role`() {
        val names =
            listOf(
                "\"Roboto\"",
                "\"SpaceGrotesk_600Bold\"",
                "\"SpaceGrotesk_650SemiBold\"",
                "\"SpaceGrotesk_400Regular_Italic\"",
                "\"spaceGrotesk_600SemiBold\"",
                "\"Space-Grotesk_600SemiBold\"",
                "\"Spacing_400Regular\"",
                "600",
            )
        for (name in names) {
            val message = refusal(theme(fonts = """"body": "HankenGrotesk_400Regular", "display": $name"""))

            assertTrue("Fonts.display" in message, "$name: $message")
        }
    }

    @Test
    fun `a family can't take a name the generated file already declares`() {
        val declared =
            Regex("""^(?:internal |private )?(?:data class|object|val) (\w+)""", RegexOption.MULTILINE)
                .findAll(generateTheme(theme()))
                .map { it.groupValues[1] }
                .toSet() - setOf("HankenGrotesk", "SpaceGrotesk")
        assertTrue("KettleType" in declared, "$declared")
        for (name in declared) {
            val message =
                refusal(theme(fonts = """"body": "HankenGrotesk_400Regular", "display": "${name}_600SemiBold""""))

            assertTrue("Fonts.display" in message, "$name: $message")
        }
    }
}
