package com.casco.kettle.shell.importer

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.serializeLibraryYaml
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor

/**
 * The Import screens' shared harness (#193's recipe): the screen in `pt`, so a hardcoded English string
 * fails, on fake files behind `StoredFiles`, the real shipped packs and schema, and fakes for the
 * picker, the clipboard and the share sheet that record what they were handed.
 */
abstract class ImportHarness {
    @get:Rule
    val compose = createComposeRule()

    internal var closed = 0
    internal var libraryChanged = 0
    internal var picks = 0

    /** What the next tap on the file row gets back from the picker. */
    internal var picked: suspend () -> FilePick = { FilePick.Cancelled }

    /** Every text copied, and whether the clipboard takes the next one. */
    internal val copies = mutableListOf<String>()
    internal var clipboardTakes = true

    /** What the clipboard holds when Paste from clipboard is pressed; null when it refuses to be read. */
    internal var clipboard: String? = ""

    /** Every text handed to the share sheet, and whether the next one opens. */
    internal val shares = mutableListOf<String>()
    internal var sheetOpens = true

    internal lateinit var latest: LatestFiles

    /**
     * [io] holds the file work until it's run; [readFirst] false leaves the first read held in it too.
     * [locale] stands in for the reader's language where a test changes it under the sheet.
     */
    internal fun show(
        files: Files = Files(),
        io: Steps? = null,
        readFirst: Boolean = true,
        locale: State<Locale>? = null,
    ): Files {
        val dispatcher = io?.asCoroutineDispatcher() ?: Dispatchers.Unconfined
        latest = LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = dispatcher).apply { reread() }
        if (readFirst) io?.run()
        compose.setContent {
            val configuration =
                locale?.let { Configuration(LocalConfiguration.current).apply { setLocale(it.value) } }
                    ?: LocalConfiguration.current
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                KettleTheme(darkTheme = false) {
                    ImportScreen(
                        latest,
                        ShippedContent(RuntimeEnvironment.getApplication().assets),
                        ImportActions(
                            pickFile = {
                                picks += 1
                                picked()
                            },
                            close = { closed += 1 },
                            libraryChanged = { libraryChanged += 1 },
                            copy = { text ->
                                copies += text
                                clipboardTakes
                            },
                            readClipboard = { clipboard },
                            share = { text ->
                                shares += text
                                sheetOpens
                            },
                        ),
                    )
                }
            }
        }
        return files
    }

    internal fun picks(
        library: Library,
        name: String = "exercises.yaml",
    ) = picksText(serializeLibraryYaml(library), name)

    internal fun picksText(
        text: String,
        name: String = "exercises.yaml",
    ) {
        picked = { FilePick.Picked(name, 512, text) }
    }

    internal fun chooseFile(files: Files = Files()): Files {
        val shown = show(files)
        press("Escolher exercises.yaml")
        return shown
    }

    internal fun paste(
        text: String,
        files: Files = Files(),
    ): Files {
        val shown = show(files)
        press("Colar YAML")
        compose.onNode(hasContentDescription(PASTE_BOX)).performTextReplacement(text)
        press("Revisar texto colado")
        return shown
    }

    internal val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    internal fun button(label: String) = compose.onNode(hasText(label) and isButton)

    internal fun press(label: String) = button(label).performClick()

    internal fun shown(text: String) = compose.onNodeWithText(text).assertExists()

    internal fun absent(text: String) = compose.onAllNodesWithText(text).assertCountEquals(0)

    internal fun top(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top

    /** [texts] as the screen lays them out, top to bottom. */
    internal fun inOrder(vararg texts: String) = texts.sortedBy(::top)

    internal fun Files.nothingWritten() = assertEquals(emptyList<Library>(), written)

    internal fun button(
        label: String,
        substring: Boolean,
    ) = compose.onNode(hasText(label, substring = substring) and isButton)

    internal fun press(
        label: String,
        substring: Boolean,
    ) = button(label, substring).performClick()

    internal class Files(
        private val library: Library? = current,
        private val preferences: Preferences? = null,
        var log: SessionLog = SessionLog.Empty,
    ) : FakeStoredFiles() {
        val written = mutableListOf<Library>()

        /** Thrown by the next write, as a full disk would. */
        var failure: IOException? = null

        override fun library() = written.lastOrNull() ?: library

        override fun preferences() = preferences

        override fun sessionLog() = log

        override fun writeLibrary(library: Library) {
            failure?.let { throw it }
            written += library
        }
    }
}

internal val threeSets = ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 90.0)
internal val pullUps = Exercise("pull-ups", "Pull-ups", threeSets)
internal val dips = Exercise("dips", "Dips", threeSets)
internal val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pull-ups")))
internal val legDay = Workout("leg-day", "Leg day", listOf(WorkoutBlock.ExerciseBlock("squats")))
internal val base6 = Program("base-6", "Base 6", listOf(ProgramWeek.Training(1, workoutId = "push-day")))
internal val current = Library(1, listOf(pullUps), listOf(pushDay), emptyList())
internal val many = (0 until 30).map { Exercise("ex-$it", "Exercise $it", threeSets) }

/** Unfinished, so it counts as logged without needing any work in it. */
internal val unfinished = Session(1, "s1", "push-day", null, null, null, "2026-07-29T09:00:00.000Z", null, emptyList())

internal val imperial = Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null)

internal const val NO_CHANGES = "Nenhuma alteração — o arquivo importado é igual à sua biblioteca atual."
internal const val SAME_VALUES = "nenhum campo alterado — substituído por uma definição idêntica"
internal const val PASTE_BOX = "Cole aqui o exercises.yaml completo"
internal const val SHARE_BRIEF = "Enviar o formato para um assistente"
internal const val COPY_BRIEF = "Copiar em vez disso"
internal const val COPIED = "Copiado"
internal const val COPY_FAILED = "Não foi possível acessar a área de transferência."
internal const val SHARE_FAILED = "Não foi possível abrir o menu de compartilhamento."
internal const val PASTE_FROM_CLIPBOARD = "Colar da área de transferência"
internal const val CLIPBOARD_EMPTY = "Nada para colar — a área de transferência está vazia ou o acesso foi negado."

/** RN's `label` type uppercases at draw time. */
internal const val PACKS_TITLE = "PACOTES INICIAIS"

/** Holds the file work until it's run, for a test that needs a write caught mid-way. */
internal class Steps : Executor {
    private val queued = ArrayDeque<Runnable>()

    override fun execute(task: Runnable) {
        queued += task
    }

    fun run() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }
}

/** A text that reads like an i18next key path, "import.title", and not a file's name, "exercises.yaml". */
internal val KEY_PATH = Regex("^(?!.*\\.yaml$)[a-z][a-zA-Z]*\\.[a-zA-Z.]+$")

internal fun hasTextMatching(pattern: Regex) =
    SemanticsMatcher("text matches $pattern") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { pattern.matches(it.text) }
    }
