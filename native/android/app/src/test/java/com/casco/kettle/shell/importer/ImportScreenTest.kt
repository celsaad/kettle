package com.casco.kettle.shell.importer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.serializeLibraryYaml
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Locale

/**
 * Import (#244), case for case from RN's `import.test.tsx`: the sources, refusals, preview and merge.
 * The hand-offs to an assistant are `ImportHandoffTest`'s. Import is the only path outside data takes
 * into the library, so every refusal is asserted to write nothing.
 *
 * The preview's arithmetic and the diff are `:shared`'s (`ImportPreviewTest`, `LibraryDiffTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w400dp-h4000dp")
class ImportScreenTest : ImportHarness() {
    // the picker and refusals

    // A cancel is a normal outcome, and reporting it as a failure would train the reader to ignore the
    // error line that matters.
    @Test
    fun `does nothing when the picker is dismissed`() {
        val files = chooseFile()

        assertEquals(1, picks)
        absent(NO_CHANGES)
        shown("Escolher exercises.yaml")
        files.nothingWritten()
    }

    @Test
    fun `surfaces a YAML syntax error`() {
        picksText("exercises: [\n  - id: broken")
        val files = chooseFile()

        compose.onNode(hasText("YAML inválido:", substring = true)).assertExists()
        files.nothingWritten()
    }

    // `programs` is missing. It parses as YAML perfectly well, so only the schema stands in the way.
    @Test
    fun `surfaces a schema error rather than importing a partial library`() {
        picksText("version: 1\nexercises: []\nworkouts: []\n")
        val files = chooseFile()

        compose.onNode(hasText("Não corresponde ao formato do exercises.yaml", substring = true)).assertExists()
        compose.onNode(hasText("programs", substring = true)).assertExists()
        files.nothingWritten()
    }

    // Each file is valid on its own, and only the combination is broken: the check the schema can't make.
    @Test
    fun `refuses a file whose workout points at an exercise nobody has, leaving the ids untranslated`() {
        picks(Library(1, emptyList(), listOf(legDay), emptyList()))
        val files = chooseFile()

        shown("O treino \"leg-day\" referencia o exercício desconhecido \"squats\"")
        files.nothingWritten()
    }

    @Test
    fun `refuses a program week whose workout nobody has, naming the day verbatim`() {
        val program = Program("base-6", "Base 6", listOf(ProgramWeek.Training(2, "Thursday", "legs")))
        picks(Library(1, emptyList(), emptyList(), listOf(program)))
        val files = chooseFile()

        shown("O programa \"base-6\" na semana 2 (Thursday) referencia o treino desconhecido \"legs\"")
        files.nothingWritten()
    }

    // The platform's message says what went wrong; ours says at which step, and the picker is usable again.
    @Test
    fun `surfaces a read failure instead of failing silently`() {
        picked = { FilePick.Failed("Permission denied") }
        val files = chooseFile()

        shown("Não foi possível ler esse arquivo: Permission denied")
        button("Escolher exercises.yaml").assertIsEnabled()
        files.nothingWritten()
    }

    @Test
    fun `previews what would change, and only writes once confirmed`() {
        picks(Library(1, listOf(dips), emptyList(), emptyList()), "more.yaml")
        val files = chooseFile()

        shown("more.yaml")
        shown("512 B · selecionado dos Arquivos")
        shown("dips")
        shown("novo exercício")
        files.nothingWritten()

        press("Mesclar e importar")

        assertEquals(
            listOf("pull-ups", "dips"),
            files.written
                .single()
                .exercises
                .map { it.id },
        )
        assertEquals(1, libraryChanged)
        // Not closed: the screen stays to say what landed. Done closes it.
        assertEquals(0, closed)
    }

    // A program-only file once previewed as "0 new, 0 updated" and merged its programs regardless.
    @Test
    fun `counts and lists programs, not just exercises and workouts`() {
        picks(Library(1, emptyList(), emptyList(), listOf(base6)))
        val files = chooseFile()

        shown("base-6")
        shown("novo programa")
        // The only non-zero tile.
        compose.onAllNodesWithText("1").assertCountEquals(1)
        absent(NO_CHANGES)

        press("Mesclar e importar")

        assertEquals(
            listOf("base-6"),
            files.written
                .single()
                .programs
                .map { it.id },
        )
    }

    @Test
    fun `counts an existing program as an update`() {
        picks(Library(1, emptyList(), emptyList(), listOf(base6.copy(name = "Base 6 v2"))))
        chooseFile(Files(current.copy(programs = listOf(base6))))

        shown("programa atualizado")
    }

    @Test
    fun `says so when the file adds and changes nothing`() {
        picks(Library(1, emptyList(), emptyList(), emptyList()))
        chooseFile()

        shown(NO_CHANGES)
    }

    // Merge classifies by id, not by value, so "no changes" is reachable only by an empty file.
    @Test
    fun `counts a re-imported identical file as updates, not as no changes`() {
        picks(current)
        chooseFile()

        absent(NO_CHANGES)
        shown("exercício atualizado")
    }

    // pasting

    @Test
    fun `previews a pasted library and merges it on confirm`() {
        val files = paste(serializeLibraryYaml(Library(1, listOf(dips), emptyList(), emptyList())))

        shown("YAML colado")
        shown("dips")
        shown("novo exercício")
        files.nothingWritten()
        // No file and no I/O on this path at all.
        assertEquals(0, picks)

        press("Mesclar e importar")

        assertEquals(
            listOf("pull-ups", "dips"),
            files.written
                .single()
                .exercises
                .map { it.id },
        )
    }

    // Trimmed first, so the label describes the YAML, not the blank lines a chat window appended.
    @Test
    fun `counts the lines it is about to merge`() {
        val yaml = serializeLibraryYaml(Library(1, listOf(dips), emptyList(), emptyList()))
        paste("$yaml\n\n")

        shown("${yaml.trim().split('\n').size} linhas coladas")
    }

    @Test
    fun `refuses malformed YAML the same way the picker does, keeping the text to fix`() {
        val files = paste("exercises: [\n  - id: broken")

        compose.onNode(hasText("YAML inválido:", substring = true)).assertExists()
        files.nothingWritten()
        compose.onNode(hasContentDescription(PASTE_BOX)).assert(hasText("exercises: [\n  - id: broken"))
    }

    @Test
    fun `refuses a merge-level break, which the schema alone would let through`() {
        val files = paste(serializeLibraryYaml(Library(1, emptyList(), listOf(legDay), emptyList())))

        shown("O treino \"leg-day\" referencia o exercício desconhecido \"squats\"")
        files.nothingWritten()
    }

    // No preview and no error: whitespace reaching the parser would come back "the input is empty".
    @Test
    fun `does nothing on an empty box`() {
        paste("   \n  ")

        button("Revisar texto colado").assertIsNotEnabled()
        compose.onAllNodes(hasText("YAML inválido:", substring = true)).assertCountEquals(0)
        absent("YAML colado")
    }

    @Test
    fun `tells TalkBack the paste box opens and closes`() {
        show()

        button("Colar YAML").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        press("Colar YAML")
        button("Colar YAML").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
    }

    // A file read in flight would replace a pack's preview when it lands, and Merge would write the file.
    @Test
    fun `holds the other sources while a file is being read`() {
        val never = CompletableDeferred<FilePick>()
        picked = { never.await() }
        show()
        press("Colar YAML")
        compose.onNode(hasContentDescription(PASTE_BOX)).performTextReplacement("version: 1")

        press("Escolher exercises.yaml")

        button("Firme e Forte", substring = true).assertIsNotEnabled()
        button("Revisar texto colado").assertIsNotEnabled()
    }

    // the update diff

    @Test
    fun `names what an updated exercise is about to lose`() {
        picks(Library(1, listOf(pullUps.copy(config = threeSets.copy(sets = 4))), emptyList(), emptyList()))
        chooseFile()

        shown("Séries: 3 → 4")
    }

    // A kilogram figure shown to someone working in pounds describes a change they didn't make.
    @Test
    fun `prints a weight in the reader's own units`() {
        picks(
            Library(1, listOf(pullUps.copy(config = threeSets.copy(targetWeightKg = 65.0))), emptyList(), emptyList()),
        )
        chooseFile(Files(preferences = imperial))

        shown("Peso: não definido → 143.3 lb")
    }

    @Test
    fun `says so when an updated id is byte-identical rather than showing a blank indent`() {
        picks(current)
        chooseFile()

        // One per updated row, the exercise and the workout, so every row accounts for itself.
        compose.onAllNodesWithText(SAME_VALUES).assertCountEquals(2)
    }

    // The noun at the end of the row says new or updated; read aloud, the glyph is noise.
    @Test
    fun `keeps the row's glyph from TalkBack`() {
        picks(Library(1, listOf(pullUps, dips), emptyList(), emptyList()))
        chooseFile()

        compose.onAllNodesWithText("↻").assertCountEquals(0)
        compose.onAllNodesWithText("+").assertCountEquals(0)
        shown("exercício atualizado")
    }

    // a long preview

    @Test
    fun `shows the first few and offers the rest by count`() {
        picks(Library(1, many, emptyList(), emptyList()))
        chooseFile()

        shown("ex-7")
        absent("ex-8")
        // The whole list's count, not what's hidden: it answers "how long is this".
        shown("Mostrar todas as 30 alterações")
    }

    @Test
    fun `reveals the rest on request, and can be collapsed again`() {
        picks(Library(1, many, emptyList(), emptyList()))
        chooseFile()

        button("Mostrar todas as 30 alterações").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        press("Mostrar todas as 30 alterações")
        shown("ex-29")

        button("Mostrar menos").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
        press("Mostrar menos")
        absent("ex-29")
    }

    // A layout claim a test can't measure, but the structure it rests on: nothing scrolls between Merge
    // and the root, so no length of list can push it out of view.
    @Test
    fun `leaves the actions reachable without scrolling past the list`() {
        picks(Library(1, many, emptyList(), emptyList()))
        chooseFile()

        button("Mesclar e importar").assertExists()
        compose.onAllNodes(hasText("Mesclar e importar") and hasAnyAncestor(hasScrollAction())).assertCountEquals(0)
        compose.onAllNodes(hasText("ex-0") and hasAnyAncestor(hasScrollAction())).assertCountEquals(1)
    }

    @Test
    fun `carries the expansion into what landed, rather than re-collapsing under the reader`() {
        picks(Library(1, many, emptyList(), emptyList()))
        chooseFile()
        press("Mostrar todas as 30 alterações")
        press("Mesclar e importar")

        shown("ex-29")
    }

    @Test
    fun `offers no toggle when the list is short enough to read`() {
        picks(Library(1, listOf(dips), emptyList(), emptyList()))
        chooseFile()

        compose.onAllNodes(hasText("Mostrar todas", substring = true)).assertCountEquals(0)
    }

    // after a successful merge

    @Test
    fun `reports what landed instead of vanishing`() {
        picks(Library(1, listOf(dips), emptyList(), listOf(base6)))
        chooseFile()
        press("Mesclar e importar")

        shown("Importado")
        shown("2 itens novos · 0 atualizados")
        // The ids that landed are still named, so the confirmation is specific.
        shown("dips")
        shown("base-6")
    }

    // Focus doesn't move and the result swaps in below it, so nothing re-reads on its own. A pane title,
    // which TalkBack speaks as the card appears: a live region new to the tree sent nothing on a device.
    @Test
    fun `announces the result, which a screen reader would otherwise miss`() {
        picks(Library(1, listOf(dips), emptyList(), emptyList()))
        chooseFile()
        press("Mesclar e importar")

        compose
            .onNode(
                hasText("Importado") and
                    SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Importado. 1 novos, 0 atualizados."),
            ).assertExists()
    }

    // Cancel would be a lie now: the library is already on disk.
    @Test
    fun `closes on Done, and offers nothing that implies the import could be undone`() {
        picks(Library(1, listOf(dips), emptyList(), emptyList()))
        chooseFile()
        press("Mesclar e importar")

        absent("Cancelar")
        absent("Mesclar e importar")
        press("Concluir")

        assertEquals(1, closed)
    }

    // One of each: the case where the two plural fragments have to disagree. "0 atualizados", plural,
    // is pt's `_zero`, which Android's plural rule alone would render singular.
    @Test
    fun `counts an update separately from a new item`() {
        picks(Library(1, listOf(pullUps.copy(name = "Pull-ups (renamed)"), dips), emptyList(), emptyList()))
        chooseFile()
        press("Mesclar e importar")

        shown("1 item novo · 1 atualizado")
    }

    @Test
    fun `says why a merge didn't land, and leaves it to try again`() {
        picks(Library(1, listOf(dips), emptyList(), emptyList()))
        val files = chooseFile(Files().apply { failure = IOException("no space left") })

        press("Mesclar e importar")

        shown("Não foi possível salvar sua biblioteca: no space left")
        files.nothingWritten()
        assertEquals(0, libraryChanged)
        button("Mesclar e importar").assertIsEnabled()
    }

    // A second tap while the first write is landing would write twice.
    @Test
    fun `writes once however fast Merge is pressed`() {
        val io = Steps()
        picks(Library(1, listOf(dips), emptyList(), emptyList()))
        val files = show(Files(), io)
        press("Escolher exercises.yaml")

        press("Mesclar e importar")
        button("Mesclar e importar").assertIsNotEnabled()
        press("Mesclar e importar")
        io.run()
        compose.waitForIdle()

        assertEquals(1, files.written.size)
    }

    // the bundled starter packs

    // Three separately pluralised parts: nine and one in one line is what a single count gets wrong.
    @Test
    fun `lists every pack with what it would add`() {
        show()

        shown("Firme e Forte")
        shown("Academia com Barra")
        shown("Kettlebell Essencial")
        shown("9 exercícios · 3 treinos · 1 programa")
    }

    @Test
    fun `previews a pack rather than merging it on the tap`() {
        val files = show()
        press("Firme e Forte", substring = true)

        shown("Pacote inicial que vem com o app")
        button("Mesclar e importar").assertIsEnabled()
        files.nothingWritten()
    }

    // The id prefixes' whole promise, checked where the reader would see it break.
    @Test
    fun `adds a pack without touching anything already in the library`() {
        val files = show()
        press("Firme e Forte", substring = true)
        press("Mesclar e importar")

        shown("13 itens novos · 0 atualizados")
        val merged = files.written.single()
        assertEquals("Pull-ups", merged.exercises.first { it.id == "pull-ups" }.name)
        assertTrue(merged.exercises.any { it.id == "ss-sit-to-stand" })
    }

    // The row follows the language, but what it writes is user data from the moment it lands.
    @Test
    fun `merges the pack in the language being read`() {
        val files = show()
        press("Firme e Forte", substring = true)
        press("Mesclar e importar")

        assertEquals(
            "Levantar da Cadeira",
            files.written
                .single()
                .exercises
                .first { it.id == "ss-sit-to-stand" }
                .name,
        )
    }

    // What it writes is the reader's data from the tap on, so a language change after it renames nothing.
    @Test
    fun `freezes the pack in the language it was picked in`() {
        val locale = mutableStateOf(Locale.forLanguageTag("pt"))
        val files = show(locale = locale)
        press("Firme e Forte", substring = true)

        locale.value = Locale.ENGLISH
        compose.waitForIdle()
        press("Mesclar e importar")

        assertEquals(
            "Levantar da Cadeira",
            files.written
                .single()
                .exercises
                .first { it.id == "ss-sit-to-stand" }
                .name,
        )
    }

    @Test
    fun `refuses a pack the same way it refuses a file when the library has not loaded`() {
        val files = show(Files(library = null))
        press("Firme e Forte", substring = true)

        shown("A biblioteca ainda não foi carregada.")
        files.nothingWritten()
    }

    // the order of the input sources

    // The packs are the one offer here that costs no file and no typing.
    @Test
    fun `leads with the packs for someone who has logged nothing`() {
        show()

        assertEquals(
            listOf(PACKS_TITLE, "Escolher exercises.yaml", SHARE_BRIEF),
            inOrder(PACKS_TITLE, "Escolher exercises.yaml", SHARE_BRIEF),
        )
    }

    // A returning reader came to import a file, and demoting it would trade a real job for a nicety. The
    // brief moves the other way: between the file and the paste, the order of the trip out and back.
    @Test
    fun `leads with the file path once a session has been logged`() {
        show(Files(log = SessionLog(listOf(unfinished), emptyList())))

        assertEquals(
            listOf("Escolher exercises.yaml", SHARE_BRIEF, "Colar YAML", PACKS_TITLE),
            inOrder(PACKS_TITLE, "Escolher exercises.yaml", SHARE_BRIEF, "Colar YAML"),
        )
    }

    // RN reads the order once so it can't move under the reader; a log that lands late settles it.
    @Test
    fun `settles the order on the first read of the log`() {
        val io = Steps()
        show(Files(log = SessionLog(listOf(unfinished), emptyList())), io, readFirst = false)

        io.run()
        compose.waitForIdle()

        assertTrue(top("Escolher exercises.yaml") < top(PACKS_TITLE))
    }

    @Test
    fun `keeps the order when a session lands while the sheet is up`() {
        val files = show()

        files.log = SessionLog(listOf(unfinished), emptyList())
        latest.reread()
        compose.waitForIdle()

        assertTrue(top(PACKS_TITLE) < top("Escolher exercises.yaml"))
    }

    @Test
    fun `still merges whichever source is picked, whatever the order`() {
        val files = show(Files(log = SessionLog(listOf(unfinished), emptyList())))
        press("Firme e Forte", substring = true)
        press("Mesclar e importar")

        assertTrue(
            files.written
                .single()
                .exercises
                .any { it.id.startsWith("ss-") },
        )
    }

    // a11y and i18n

    @Test
    fun `makes every control at least 44dp tall`() {
        picks(Library(1, many, emptyList(), emptyList()))
        show()
        for (label in listOf("Escolher exercises.yaml", "Colar YAML", "Firme e Forte")) {
            button(label, substring = true).assertHeightIsAtLeast(44.dp)
        }
        press("Colar YAML")
        for (label in listOf(SHARE_BRIEF, COPY_BRIEF, PASTE_FROM_CLIPBOARD)) button(label).assertHeightIsAtLeast(44.dp)
        press("Escolher exercises.yaml")
        for (label in listOf("Cancelar", "Mesclar e importar", "Mostrar todas as 30 alterações")) {
            button(label).assertHeightIsAtLeast(44.dp)
        }
    }

    @Test
    fun `renders no untranslated key path`() {
        picks(current)
        show()
        press("Colar YAML")
        compose.onAllNodes(hasTextMatching(KEY_PATH)).assertCountEquals(0)
        press("Escolher exercises.yaml")
        compose.onAllNodes(hasTextMatching(KEY_PATH)).assertCountEquals(0)
    }
}
