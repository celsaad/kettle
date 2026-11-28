package com.casco.kettle.shell.files

import com.casco.kettle.domain.equipmentMatchKey
import com.casco.kettle.shell.i18n.equipmentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The known equipment (#393), `content/equipment.json` as it reaches the APK, against the names it
 * shows in. A known item reads in the reader's language, so a key without a name in one bundle shows
 * nothing there; `:strings` holds pt and ja to en, but only for keys en has.
 */
@RunWith(RobolectricTestRunner::class)
class EquipmentVocabularyTest {
    private val shipped = ShippedLibraries()
    private val keys = shipped.content.knownEquipment()

    @Test
    fun readsAVocabularyOfDistinctKeysThatAreTheirOwnMatch() {
        // A key that isn't its own match key could never be matched: `Pull-up bar` folds to
        // `pull_up_bar`, never to `Pull_Up_Bar`.
        assertTrue(keys.size > 1)
        assertEquals(keys.distinct(), keys)
        assertEquals(emptyList<String>(), keys.filter { equipmentMatchKey(it) != it })
    }

    @Test
    fun namesEveryKeyInEveryBundleAndNothingElse() {
        val failures =
            shipped.localeBundles().flatMap { (language, bundle) ->
                val names = bundle.optJSONObject("equipment")
                val named =
                    names
                        ?.keys()
                        ?.asSequence()
                        ?.toSet()
                        .orEmpty()
                keys.filter { names?.optString(it).isNullOrBlank() }.map { "$language: no name for $it" } +
                    (named - keys.toSet()).map { "$language: names $it, which isn't a key" }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun namesEachKeyInEnglishAsItsOwnSpelling() {
        // An author who writes the item as the app shows it in English gets the known item, not words
        // of their own: `Pull-up bar` is `pull_up_bar`, where "Squat rack" for `rack` would not be.
        val english = shipped.localeBundles().getValue("en").getJSONObject("equipment")
        assertEquals(emptyList<String>(), keys.filter { equipmentMatchKey(english.getString(it)) != it })
    }

    @Test
    @Config(qualifiers = "pt")
    fun resolvesEveryKeyToItsNameInTheReadersLanguage() {
        val resources = RuntimeEnvironment.getApplication().resources
        val portuguese = shipped.localeBundles().getValue("pt").getJSONObject("equipment")
        val failures =
            keys.filter { key -> equipmentName(key)?.let(resources::getString) != portuguese.getString(key) }
        assertEquals(emptyList<String>(), failures)
    }
}
