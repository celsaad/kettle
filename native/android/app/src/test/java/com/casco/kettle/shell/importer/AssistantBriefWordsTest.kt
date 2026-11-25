package com.casco.kettle.shell.importer

import com.casco.kettle.domain.AssistantBrief
import com.casco.kettle.domain.Library
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.ShippedContent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The ownership line from the decision log, as a test rather than a comment: the app supplies the
 * file format and the user's own ids, and never a word about training. `:shared`'s
 * `AssistantBriefTest` holds the brief's layout with stand-in words; this holds the words the app
 * actually ships, which is where "ask for a hypertrophy block" would be added to make the output
 * nicer. Built over an empty library, so every word left is the app's, not the user's.
 */
@RunWith(RobolectricTestRunner::class)
class AssistantBriefWordsTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun asksForAFormatAndNeverForTrainingContent() {
        val words =
            AssistantBrief.Words(
                prompt = app.getString(R.string.import_brief_prompt),
                inventoryHeading = app.getString(R.string.import_brief_inventoryHeading),
                exercisesLabel = app.getString(R.string.import_brief_exercisesLabel),
                workoutsLabel = app.getString(R.string.import_brief_workoutsLabel),
                programsLabel = app.getString(R.string.import_brief_programsLabel),
                none = app.getString(R.string.import_brief_none),
                schemaHeading = app.getString(R.string.import_brief_schemaHeading),
            )
        val empty = Library(1, emptyList(), emptyList(), emptyList())
        val brief = AssistantBrief.build(empty, words, ShippedContent(app.assets).librarySchema()).lowercase()

        val training = listOf("program for", "hypertrophy", "strength training", "sets of", "beginner", "goal")
        assertEquals(emptyList<String>(), training.filter { it in brief })
    }
}
