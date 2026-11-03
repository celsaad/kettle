package com.casco.kettle.art

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GeneratedArtTest {
    /** A drawing in the shell every file shares, with [body] inside the root group. */
    private fun svg(body: String) =
        """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 96" fill="none">
          <g stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            $body
          </g>
        </svg>
        """.trimIndent()

    private fun drawable(body: String) =
        generateArt("""{ "drawings": { "plank": "plank" } }""", mapOf("plank" to svg(body)))
            .drawables
            .getValue("art_plank")

    @Test
    fun `a path becomes a stroked vector path on the same viewport`() {
        assertEquals(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Generated from src/content/art/plank.svg by :art. Edit the SVG, not this file. -->
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="120dp"
                android:height="96dp"
                android:viewportWidth="120"
                android:viewportHeight="96">
                <path
                    android:pathData="M8,84 H112"
                    android:strokeColor="#FF000000"
                    android:strokeWidth="3"
                    android:strokeLineCap="round"
                    android:strokeLineJoin="round" />
            </vector>

            """.trimIndent(),
            drawable("""<path d="M8,84 H112"/>"""),
        )
    }

    /** Every `pathData` in the drawable, in document order. */
    private fun pathData(body: String) =
        Regex("""android:pathData="([^"]*)"""").findAll(drawable(body)).map { it.groupValues[1] }.toList()

    @Test
    fun `a circle becomes two half arcs, since a vector path has no circle`() {
        assertEquals(
            listOf("M64.5,32 A7.5,7.5 0 1 0 79.5,32 A7.5,7.5 0 1 0 64.5,32 Z"),
            pathData("""<circle cx="72" cy="32" r="7.5"/>"""),
        )
    }

    @Test
    fun `a transformed group becomes a vector group, which applies scale, rotation and translation in SVG's order`() {
        assertEquals(
            """
            <group
                android:translateX="106"
                android:translateY="46"
                android:rotation="-90"
                android:scaleX="0.9"
                android:scaleY="0.9">
                <path
                    android:pathData="M-7,0 H7"
                    android:strokeColor="#FF000000"
                    android:strokeWidth="3"
                    android:strokeLineCap="round"
                    android:strokeLineJoin="round" />
            </group>
            """.trimIndent(),
            drawable("""<g transform="translate(106 46) rotate(-90) scale(0.9)"><path d="M-7,0 H7"/></g>""")
                .substringAfter("android:viewportHeight=\"96\">\n")
                .substringBefore("\n</vector>")
                .trimIndent(),
        )
    }

    @Test
    fun `a group takes only the parts of the transform it names, and a two-axis scale`() {
        val group = drawable("""<g transform="translate(26 62) scale(-1 1)"><path d="M0,0 H28"/></g>""")

        assertEquals(
            listOf("translateX=\"26\"", "translateY=\"62\"", "scaleX=\"-1\"", "scaleY=\"1\""),
            Regex("""android:(translate[XY]|rotation|scale[XY])="[^"]*"""")
                .findAll(group)
                .map {
                    it.value.removePrefix("android:")
                }.toList(),
        )
    }

    /** The build's failure message for a drawing whose root group holds [body]. */
    private fun refusal(body: String) = assertThrows<IllegalArgumentException> { drawable(body) }.message.orEmpty()

    @ParameterizedTest
    @ValueSource(
        strings = [
            """<rect x="1" y="1" width="2" height="2"/>""",
            """<use href="#dumbbell"/>""",
            """<g transform="translate(1 1)"><ellipse cx="1" cy="1" rx="1" ry="2"/></g>""",
        ],
    )
    fun `refuses an element outside the subset, naming the file and the element`(body: String) {
        val message = refusal(body)
        assertTrue("plank.svg" in message && Regex("<(rect|use|ellipse)>").containsMatchIn(message), message)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """<path d="M0,0 H1" stroke="#000"/>""",
            """<circle cx="1" cy="1" r="1" fill="red"/>""",
            """<g transform="translate(1 1)" opacity="0.5"><path d="M0,0 H1"/></g>""",
            """<path/>""",
        ],
    )
    fun `refuses an attribute the element does not take, or one it needs and lacks`(body: String) {
        val message = refusal(body)
        assertTrue("plank.svg" in message && Regex("(stroke|fill|opacity|d)\\b").containsMatchIn(message), message)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "scale(2) translate(1 1)",
            "rotate(90 5 5)",
            "skewX(10)",
            "translate(1 1) matrix(1 0 0 1 0 0)",
            "translate(a b)",
        ],
    )
    fun `refuses a transform that is not translate, rotate, scale in that order`(transform: String) {
        val message = refusal("""<g transform="$transform"><path d="M0,0 H1"/></g>""")
        assertTrue("plank.svg" in message && transform in message, message)
    }

    @Test
    fun `refuses path data that is not path data`() {
        assertTrue("url(#x)" in refusal("""<path d="url(#x)"/>"""))
    }

    @Test
    fun `refuses a colour on the root group other than currentColor`() {
        val svg = svg("""<path d="M0,0 H1"/>""").replace("currentColor", "#26221c")
        val message =
            assertThrows<IllegalArgumentException> {
                generateArt("""{ "drawings": { "plank": "plank" } }""", mapOf("plank" to svg))
            }.message.orEmpty()
        assertTrue("currentColor" in message, message)
    }

    @Test
    fun `refuses a canvas that fills, or a second root group`() {
        for (svg in listOf(
            svg("""<path d="M0,0 H1"/>""").replace("""fill="none"""", """fill="#000""""),
            svg("""<path d="M0,0 H1"/>""").replace("</svg>", """<g stroke="currentColor"/></svg>"""),
        )) {
            assertThrows<IllegalArgumentException> {
                generateArt("""{ "drawings": { "plank": "plank" } }""", mapOf("plank" to svg))
            }
        }
    }

    private val line = svg("""<path d="M0,0 H1"/>""")

    @Test
    fun `refuses an id whose drawing has no file`() {
        val message =
            assertThrows<IllegalArgumentException> {
                generateArt("""{ "drawings": { "plank": "plank", "kb-halo": "halo" } }""", mapOf("plank" to line))
            }.message.orEmpty()
        assertTrue("kb-halo" in message && "src/content/art/halo.svg" in message, message)
    }

    @Test
    fun `refuses a file no id draws with`() {
        val message =
            assertThrows<IllegalArgumentException> {
                generateArt("""{ "drawings": { "plank": "plank" } }""", mapOf("plank" to line, "halo" to line))
            }.message.orEmpty()
        assertTrue("src/content/art/halo.svg" in message, message)
    }

    @Test
    fun `the lookup gives every id its drawing's resource, two ids sharing one where the map says so`() {
        val art =
            generateArt(
                """
                {
                  "drawings": { "easy-cardio": "easy-cardio", "gym-cooldown": "easy-cardio", "plank": "plank" },
                  "noArt": ["rest"]
                }
                """.trimIndent(),
                mapOf("easy-cardio" to line, "plank" to line),
            )

        assertEquals(setOf("art_easy_cardio", "art_plank"), art.drawables.keys)
        assertEquals(
            """
            // Generated from src/content/art.json by :art. Edit art.json, not this file.
            package com.casco.kettle.shell.art

            import com.casco.kettle.shell.R

            /** Each exercise id with a drawing, and the drawable :art generated for it. */
            internal val artDrawables: Map<String, Int> =
                mapOf(
                    "easy-cardio" to R.drawable.art_easy_cardio,
                    "gym-cooldown" to R.drawable.art_easy_cardio,
                    "plank" to R.drawable.art_plank,
                )

            /**
             * Each exercise id with a drawing, and the string describing its movement: `exerciseArt.<id>`
             * in the locale bundles, by the resource name :strings gives that key.
             */
            internal val artDescriptions: Map<String, Int> =
                mapOf(
                    "easy-cardio" to R.string.exerciseArt_easy_cardio,
                    "gym-cooldown" to R.string.exerciseArt_gym_cooldown,
                    "plank" to R.string.exerciseArt_plank,
                )

            """.trimIndent(),
            art.lookup,
        )
    }
}
