package com.casco.kettle.repochecks

import com.casco.kettle.domain.Session
import com.casco.kettle.domain.serializeSessionYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The docs and the site are the only spec an outside author, or an assistant asked to emit YAML, ever
 * reads, and both are hand-maintained copies of the format. They have drifted before: the product
 * plan's sample predated the block `type` discriminator, and the first draft of `site/format.html`
 * had five of seven type tables wrong. So every complete sample goes through the real parser rather
 * than a proofread, and one the importer stops accepting fails here, in the file that would otherwise
 * keep teaching it.
 */
class SamplesTest {
    /**
     * Every fenced YAML block in a doc that is a complete library: `version: 1` at the top, and no
     * `<placeholder>` angle brackets, which the shape sketches use and which aren't meant to parse.
     */
    private fun docSamples(path: String): List<String> =
        Regex("```yaml\n([\\s\\S]*?)```")
            .findAll(repoText(path))
            .map { it.groupValues[1] }
            .filter { it.startsWith("version: 1") && "exercises:" in it && '<' !in it }
            .toList()

    /**
     * Every `<pre data-validate="library">` block on a page, as a reader would copy it. The samples are
     * syntax-highlighted, every token in its own `<span>`, so stripping the tags and decoding the
     * entities is what turns the markup back into the YAML that has to parse. The marker is opt-in:
     * the page's other samples are deliberate fragments that can't parse standalone.
     */
    private fun pageSamples(path: String): List<String> =
        Regex("""<pre[^>]*\bdata-validate="library"[^>]*>([\s\S]*?)</pre>""")
            .findAll(repoText(path))
            .map { match ->
                match.groupValues[1]
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
            }.toList()

    private fun check(samples: Map<String, List<String>>) {
        // A page reorganised so the extraction matches nothing would otherwise pass by checking zero.
        samples.forEach { (path, found) -> assertTrue(found.isNotEmpty(), "$path has no complete sample to check") }
        val failures =
            samples.flatMap { (path, found) ->
                found.mapIndexedNotNull { index, yaml -> refusal(yaml)?.let { "$path sample ${index + 1}: $it" } }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `every complete library sample in the docs parses`() {
        check(listOf("docs/authoring-exercises-yaml.md", "docs/product-plan.md").associateWith(::docSamples))
    }

    @Test
    fun `every validated library sample on the site parses`() {
        check(listOf("site/index.html", "site/format.html").associateWith(::pageSamples))
    }

    /**
     * The product plan's session sample is app-written rather than hand-authored, so it has to look
     * like what the app writes. It once quoted its timestamps, because js-yaml resolved a bare one to
     * a Date; `:shared`'s loader has no timestamp type, so the app writes them bare, and the sample
     * is held to whatever line the writer actually produces rather than to either spelling.
     */
    @Test
    fun `the product plan session sample writes timestamps the way the app does`() {
        val written =
            serializeSessionYaml(
                Session(
                    version = 1,
                    id = "2026-07-22T18-30-00",
                    workout = "calisthenics-a",
                    program = null,
                    programWeek = null,
                    programDay = null,
                    startedAt = "2026-07-22T18:30:00Z",
                    endedAt = "2026-07-22T19:05:12Z",
                    entries = emptyList(),
                ),
            )
        val sample = repoText("docs/product-plan.md")
        val failures =
            listOf("started_at", "ended_at").mapNotNull { key ->
                val line = written.lines().single { it.startsWith("$key:") }
                line.takeIf { sample.lines().none { it.substringBefore("  #").trimEnd() == line } }
            }
        assertEquals(emptyList<String>(), failures)
    }
}
