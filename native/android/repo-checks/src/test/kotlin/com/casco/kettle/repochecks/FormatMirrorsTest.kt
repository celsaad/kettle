package com.casco.kettle.repochecks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The format has hand-maintained mirrors beside `content/library-schema.json`, and the samples
 * test covers only their samples. Their type tables were checked by eye, which failed the first time
 * it mattered: the published reference shipped with `emom` taking `rounds` instead of
 * `total_minutes` and `amrap` missing its required `time_cap_sec`, plausible enough to read as right
 * and refused on import. So each table is parsed and diffed against the schema file's config fields:
 * their names and whether each is optional, the two things a reader copies literally. Ranges, units
 * and prose are still the author's.
 *
 * The product plan's type table names fields in prose ("work sec, rest sec") rather than as the
 * format spells them, so it isn't a mirror this can read.
 */
class FormatMirrorsTest {
    /**
     * A config field as a mirror states it. Each type's fields are a list sorted by name, not a set, so
     * a row pasted twice into a table is a difference rather than a duplicate the set swallows.
     */
    private data class Field(
        val name: String,
        val optional: Boolean,
    )

    /** Each exercise type's config fields, from the schema file the assistant brief carries. */
    private val expected: Map<String, List<Field>> =
        Json
            .parseToJsonElement(repoText("content/library-schema.json"))
            .jsonObject
            .path("properties", "exercises", "items")
            .getValue("oneOf")
            .jsonArray
            .associate { option ->
                val properties = option.jsonObject.path("properties")
                val config = properties.path("config")
                val required =
                    config["required"]
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                        .orEmpty()
                        .toSet()
                val fields = config["properties"]?.jsonObject?.keys.orEmpty()
                properties
                    .path("type")
                    .getValue("const")
                    .jsonPrimitive.content to
                    fields.map { Field(it, it !in required) }.sortedBy { it.name }.toList()
            }

    private fun JsonObject.path(vararg keys: String): JsonObject =
        keys.fold(this) { node, key -> node.getValue(key).jsonObject }

    /**
     * The markdown mirror is a row per type: `` | `emom` | `interval_sec` (>0), … | notes | ``. Fields
     * are read from the second cell only, since the notes cell backticks field names in prose, and a
     * field is `` `name` `` followed by its parenthesised constraints, which keeps the
     * `` ≥ `target_reps_min` `` inside another field's constraints from reading as a field.
     */
    private fun markdown(): Map<String, List<Field>> {
        val found = LinkedHashMap<String, List<Field>>()
        for (line in repoText("docs/authoring-exercises-yaml.md").lines()) {
            val cells = line.split('|')
            val type = cells.getOrNull(1)?.trim()?.replace("`", "")
            if (cells.size < MIN_ROW_CELLS || type !in expected || type in found) continue
            found[type!!] =
                Regex("""`([a-z_]+)`\s*\(([^)]*)\)""")
                    .findAll(cells[2])
                    .map { Field(it.groupValues[1], "optional" in it.groupValues[2]) }
                    .sortedBy { it.name }
                    .toList()
        }
        return found
    }

    /**
     * The site mirror is an `<h3>type</h3>` per type, then a table whose first column is the field.
     * Only a row's first `<code>` is its field, since the meaning column cites others. Each section is
     * cut at its first `</table>`: without that, the last type absorbs every table below it (circuits,
     * programs) and reports their columns as its own config, which is how the parser first failed, and
     * quietly enough to pass for a documentation bug.
     */
    private fun site(): Map<String, List<Field>> =
        repoText("site/format.html")
            .split("<h3>")
            .drop(1)
            .map { it.substringBefore("</h3>").trim() to it }
            .filter { (type, section) -> type in expected && "<table" in section }
            .map { (type, section) ->
                type to section.substring(section.indexOf("<table"), section.indexOf("</table>"))
            }.filter { (_, table) -> "<th>config field</th>" in table }
            .distinctBy { (type, _) -> type }
            .associate { (type, table) -> type to configFields(table) }

    /** A row's field is its first cell's `<code>`, optional when its second cell says so. */
    private fun configFields(table: String): List<Field> =
        TABLE_ROW
            .findAll(table)
            .mapNotNull { row ->
                val cells = TABLE_CELL.findAll(row.groupValues[1]).map { it.groupValues[1] }.toList()
                val name = cells.firstOrNull()?.let { FIELD_CODE.find(it)?.groupValues?.get(1) }
                name?.let { Field(it, "optional" in cells.getOrElse(1) { "" }) }
            }.sortedBy { it.name }
            .toList()

    @Test
    fun `reads every exercise type from the schema file`() {
        // Both diffs below compare whole maps, so an empty read would make them agree with an empty page.
        assertEquals(EXERCISE_TYPES, expected.keys.size)
    }

    @Test
    fun `the authoring reference states every type's config fields as the schema does`() {
        // Compared whole: a type the parser stopped finding shows up as a missing key, not a pass.
        assertEquals(expected, markdown())
    }

    @Test
    fun `the published format page states every type's config fields as the schema does`() {
        assertEquals(expected, site())
    }

    private companion object {
        /** `| type | fields | notes |` splits into five, both ends empty. */
        const val MIN_ROW_CELLS = 4

        /** The format's seven, also what the seed demonstrates (`ShippedSeedTest`). */
        const val EXERCISE_TYPES = 7
        val TABLE_ROW = Regex("""<tr>([\s\S]*?)</tr>""")
        val TABLE_CELL = Regex("""<td>([\s\S]*?)</td>""")
        val FIELD_CODE = Regex("<code>([a-z_]+)</code>")
    }
}
