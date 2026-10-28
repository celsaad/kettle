package com.casco.kettle.shell.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import com.casco.kettle.theme.FONT_WEIGHTS
import com.casco.kettle.theme.parseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

/** The generated theme as Compose reads it, against the theme.json it came from. */
@RunWith(RobolectricTestRunner::class)
class GeneratedThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private val theme by lazy {
        parseTheme(File(checkNotNull(System.getProperty("kettle.themeFile")) { "set by Gradle" }).readText())
    }

    /** A CSS color as its 0–255 channels, alpha first, decoded here rather than by the generator. */
    private fun channels(css: String): List<Int> {
        val rgba = Regex("""rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)""").matchEntire(css)
        if (rgba == null) return listOf(255) + css.removePrefix("#").chunked(2).map { it.toInt(16) }
        val alpha = (rgba.groupValues[4].toDouble() * 255).roundToInt()
        return listOf(alpha) + rgba.groupValues.subList(1, 4).map { it.toInt() }
    }

    private fun channels(color: Color) =
        listOf(color.alpha, color.red, color.green, color.blue).map { (it * 255).roundToInt() }

    /**
     * Every color token, read back through Compose's own `Color`, so the ARGB order the generator writes
     * is checked against what Compose means by it. Keyed by name, so a token missing on either side
     * shows up as a difference too.
     */
    @Test
    fun `every color reads back in Compose as theme json wrote it`() {
        val groups =
            mapOf(
                "light" to (theme.light to LightColors.tokens),
                "dark" to (theme.dark to DarkColors.tokens),
                "runner" to (theme.runner to RunnerColors.tokens),
            )
        for ((name, pair) in groups) {
            val (source, generated) = pair
            assertEquals(
                name,
                source.mapValues { channels(it.value) },
                generated.toMap().mapValues { channels(it.value) },
            )
        }
    }

    @Test
    fun `spacing and the content width are theme json's numbers in dp`() {
        assertEquals(
            theme.spacing.mapValues { it.value.toFloat() },
            Spacing.tokens.toMap().mapValues { it.value.value },
        )
        assertEquals(theme.maxContentWidth.toFloat(), MaxContentWidth.value)
    }

    @Test
    fun `each type style is theme json's sizes in sp, at its font role's weight`() {
        val expected =
            theme.type.mapValues { (_, style) ->
                listOf(
                    FONT_WEIGHTS.getValue(style.font),
                    style.fontSize?.toFloat(),
                    style.lineHeight?.toFloat(),
                    style.letterSpacing?.toFloat(),
                )
            }
        val generated =
            KettleType.tokens.toMap().mapValues { (_, style) ->
                listOf(
                    when (style.fontWeight) {
                        FontWeight.Normal -> "Normal"
                        FontWeight.Medium -> "Medium"
                        FontWeight.SemiBold -> "SemiBold"
                        FontWeight.Bold -> "Bold"
                        else -> "unexpected ${style.fontWeight}"
                    },
                    style.fontSize.takeIf { it.isSp }?.value,
                    style.lineHeight.takeIf { it.isSp }?.value,
                    style.letterSpacing.takeIf { it.isSp }?.value,
                )
            }
        assertEquals(expected, generated)
    }

    /**
     * [this] drawn over [background], in doubles. Compose's own `compositeOver` packs its result back
     * into 8-bit sRGB, which on its own moved one recorded ratio by more than 0.01.
     */
    private fun Color.over(background: DoubleArray): DoubleArray =
        doubleArrayOf(red.toDouble(), green.toDouble(), blue.toDouble()).let { fg ->
            DoubleArray(3) { fg[it] * alpha + background[it] * (1 - alpha) }
        }

    /** An opaque surface's channels: what everything translucent is composited onto. */
    private fun Color.surface(): DoubleArray {
        check(alpha == 1f) { "$this is translucent; composite it onto a surface first" }
        return doubleArrayOf(red.toDouble(), green.toDouble(), blue.toDouble())
    }

    /** WCAG 2.1 relative luminance. */
    private fun luminance(rgb: DoubleArray): Double {
        val (r, g, b) = rgb.map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun assertContrast(
        recorded: Double,
        foreground: Color,
        background: DoubleArray,
    ) {
        val (light, dark) = listOf(luminance(foreground.over(background)), luminance(background)).sortedDescending()
        assertEquals(recorded, (light + 0.05) / (dark + 0.05), CONTRAST_TOLERANCE)
    }

    /**
     * The ratios theme.ts records, recomputed from the generated colors. They are the expected values
     * here, written by hand from its comments: a color that moved in the port would move its ratio.
     */
    @Test
    fun `the contrast ratios recorded in theme ts hold for the generated colors`() {
        with(LightColors) {
            assertContrast(5.28, textSecondary, background.surface())
            assertContrast(5.79, textSecondary, backgroundElement.surface())
            assertContrast(4.79, textSecondary, backgroundSelected.surface())
            assertContrast(3.64, onAccent, accent.surface())
        }

        with(RunnerColors) {
            val soft = accentSoft.over(background.surface())
            val calmSoft = accentCalmSoft.over(background.surface())
            assertContrast(4.17, accent, soft)
            assertContrast(3.79, accentCalm, calmSoft)
            assertContrast(5.68, accentOnSoft, soft)
            assertContrast(5.75, accentCalmOnSoft, calmSoft)
        }

        // Each level against the card, then against the empty-day cell drawn on that card.
        val calendar =
            mapOf(
                DarkColors to listOf(3.73 to 3.20, 5.61 to 4.81, 7.61 to 6.53),
                LightColors to listOf(3.64 to 3.23, 5.68 to 5.04, 9.69 to 8.59),
            )
        for ((colors, ratios) in calendar) {
            val card = colors.backgroundElement.surface()
            val empty = colors.calendarEmpty.over(card)
            val levels = listOf(colors.calendarLevel1, colors.calendarLevel2, colors.calendarLevel3)
            for ((level, ratio) in levels.zip(ratios)) {
                assertContrast(ratio.first, level, card)
                assertContrast(ratio.second, level, empty)
            }
        }
    }

    private class ObservedTheme {
        var shell: KettleColors? = null
        var shellBackground: Color? = null
        var runnerBackground: Color? = null
        var runnerText: Color? = null
    }

    /** What a screen in the shell, and the runner nested inside it, see under the current configuration. */
    private fun observeTheme(): ObservedTheme {
        val seen = ObservedTheme()
        compose.setContent {
            KettleTheme {
                seen.shell = KettleTheme.colors
                seen.shellBackground = MaterialTheme.colorScheme.background
                RunnerTheme {
                    seen.runnerBackground = MaterialTheme.colorScheme.background
                    seen.runnerText = MaterialTheme.colorScheme.onSurface
                }
            }
        }
        compose.waitForIdle()
        return seen
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `the shell is light in a light system scheme, and the runner is dark inside it`() {
        val seen = observeTheme()

        assertEquals(LightColors, seen.shell)
        assertEquals(LightColors.background, seen.shellBackground)
        assertEquals(RunnerColors.background, seen.runnerBackground)
        assertEquals(RunnerColors.text, seen.runnerText)
        assertTrue("the runner's background is dark", RunnerColors.background.luminance() < DARK_LUMINANCE)
    }

    @Test
    @Config(qualifiers = "night")
    fun `the shell is dark in a dark system scheme, and the runner keeps its own colors`() {
        val seen = observeTheme()

        assertEquals(DarkColors, seen.shell)
        assertEquals(DarkColors.background, seen.shellBackground)
        assertEquals(RunnerColors.background, seen.runnerBackground)
        assertEquals(RunnerColors.text, seen.runnerText)
    }

    private companion object {
        // Computed here at the byte alpha both platforms render. theme.ts measured some translucent
        // pairings that way (the dark calendar's) and some at the exact CSS fraction (the light
        // calendar's: 8.59 there, 8.62 rendered), up to 0.03 apart. The exact values are pinned by the
        // channel test above; this one holds the recorded claims.
        const val CONTRAST_TOLERANCE = 0.03

        const val DARK_LUMINANCE = 0.05
    }
}
