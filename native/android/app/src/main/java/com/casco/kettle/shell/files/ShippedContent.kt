package com.casco.kettle.shell.files

import android.content.res.AssetManager
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.localizeLibrary
import com.casco.kettle.domain.parseLibraryTranslation
import com.casco.kettle.domain.parseLibraryYaml
import com.casco.kettle.domain.translationFor
import org.json.JSONArray

/** One entry of `packs.json`: a pack's directory name and the prefix every id in it carries. */
data class ContentPackEntry(
    val id: String,
    val idPrefix: String,
)

/**
 * The libraries the app ships, read from `content/`, which `:app` packages as assets in place, so
 * there is one copy. The decision log holds the reasoning for the seed and the packs, and
 * `ShippedLibrariesTest`, `ShippedSeedTest` and `ContentPacksTest` hold the content's own invariants.
 *
 * Import's starter packs read from here (#244), and so does the launch's seed (`LibraryGate`).
 *
 * Every read **throws** on a file that doesn't parse: shipped content is fixed at build time, so a bad
 * file is a build defect with nothing honest to degrade to.
 */
class ShippedContent(
    private val assets: AssetManager,
) {
    /** The seed in [language]'s strings, a BCP 47 tag like `pt-BR`, or English where there is no table. */
    fun seed(language: String?): Library = localized("seed", language)

    /** Every pack, in the order the import screen lists them. */
    fun packs(): List<ContentPackEntry> {
        val list = JSONArray(read("packs.json"))
        return (0 until list.length()).map { index ->
            val pack = list.getJSONObject(index)
            ContentPackEntry(pack.getString("id"), pack.getString("idPrefix"))
        }
    }

    /** Pack [id] in [language]'s strings, as [seed] reads the seed. */
    fun pack(
        id: String,
        language: String?,
    ): Library = localized("packs/$id", language)

    /**
     * The known equipment's keys (#393), from `equipment.json`, in the order a list of them is shown.
     * An item that matches one of these (`knownEquipment`) reads in the reader's language; any other
     * is the user's own words.
     */
    fun knownEquipment(): List<String> {
        val list = JSONArray(read("equipment.json"))
        return (0 until list.length()).map(list::getString)
    }

    /**
     * The JSON Schema of `exercises.yaml`, as the assistant brief carries it: hand-maintained, and held
     * to the conformance corpus by `:shared`'s `LibrarySchemaFileTest`.
     */
    fun librarySchema(): String = read("library-schema.json")

    /**
     * The tables are whatever `<language>.json` files the directory holds; `ShippedLibrariesTest`
     * holds that to one per UI language.
     */
    private fun localized(
        dir: String,
        language: String?,
    ): Library {
        val library =
            when (val result = parseLibraryYaml(read("$dir/library.yaml"))) {
                is ParseResult.Ok -> result.value
                else -> error("shipped library $dir does not parse: $result")
            }
        val tables =
            assets.list(dir).orEmpty().filter { it.endsWith(".json") }.associate {
                it.removeSuffix(".json") to parseLibraryTranslation(read("$dir/$it"))
            }
        return localizeLibrary(library, translationFor(tables, language))
    }

    private fun read(path: String): String = assets.open(path).bufferedReader().use { it.readText() }
}
