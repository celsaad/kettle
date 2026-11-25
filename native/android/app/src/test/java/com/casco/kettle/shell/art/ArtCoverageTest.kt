package com.casco.kettle.shell.art

import com.casco.kettle.shell.files.ShippedContent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * That the drawings cover the content they were drawn for: everything the app ships, read the way it
 * reads it, against `art.json` as it reaches the APK. Whether a figure reads as a plank rather than a
 * push-up is an eyeball question for the running app; `:art` holds the files, and `ArtDrawablesTest`
 * what it writes from them.
 *
 * The seed and every pack count alike: they render in the same lists, and an exercise with no drawing
 * beside neighbours that have one is the same defect whichever library it came out of.
 */
@RunWith(RobolectricTestRunner::class)
class ArtCoverageTest {
    private val assets = RuntimeEnvironment.getApplication().assets
    private val content = ShippedContent(assets)

    private val art = JSONObject(assets.open("art.json").bufferedReader().use { it.readText() })
    private val drawn: Set<String> =
        art
            .getJSONObject("drawings")
            .keys()
            .asSequence()
            .toSet()
    private val noArt: Set<String> =
        art.getJSONArray("noArt").let { list ->
            (0 until list.length()).map(list::getString).toSet()
        }

    private val shipped: Set<String> =
        (listOf(content.seed("en")) + content.packs().map { content.pack(it.id, "en") })
            .flatMap { library -> library.exercises.map { it.id } }
            .toSet()

    @Test
    fun everyShippedExerciseIsDrawnOrSaysOutLoudThatItIsNot() {
        // Silent by nature: a new shipped exercise simply renders without art, which looks like a
        // deliberate omission from every angle but this one. Landing on noArt is then a decision.
        assertTrue(shipped.size > drawn.size / 2)
        assertEquals(emptySet<String>(), shipped - drawn - noArt)
    }

    @Test
    fun noArtNamesOnlyShippedExercisesWithoutADrawing() {
        // An id that no longer ships, or has since been drawn, would buy the test above a pass it
        // hasn't earned.
        assertEquals(emptySet<String>(), noArt.filter { it !in shipped || it in drawn }.toSet())
    }

    @Test
    fun nothingIsDrawnForAnIdNothingShips() {
        // The direction a rename breaks: a lookup that misses renders nothing, so nobody would notice.
        assertEquals(emptySet<String>(), drawn - shipped)
    }

    @Test
    fun everyDrawingIsDescribedAndNoDescriptionLacksADrawing() {
        // English here; :strings holds every other bundle to English key for key, so a description
        // missing from one fails the build there.
        val locales = File(checkNotNull(System.getProperty("kettle.localesDir")) { "set by Gradle" })
        val described =
            JSONObject(File(locales, "en.json").readText())
                .getJSONObject("exerciseArt")
                .keys()
                .asSequence()
                .toSet()
        assertEquals(drawn, described)
    }
}
