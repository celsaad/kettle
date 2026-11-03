package com.casco.kettle.storage

import com.casco.kettle.domain.HISTORY_BACKUP_NAME
import com.casco.kettle.domain.LIBRARY_BACKUP_NAME
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.YamlLoad
import com.casco.kettle.domain.loadYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/**
 * The backup (#237): `backup.test.ts`'s `backUpNow` cases, against a folder faked behind
 * [BackupFolder]. Two stay behind: truncation is the SAF adapter's mode (`"wt"`), which only a device
 * shows, and "unsupported" has no Android case.
 */
class BackupTest {
    @TempDir
    lateinit var dir: File

    private lateinit var library: File
    private lateinit var targets: File
    private lateinit var folder: FakeFolder

    @BeforeEach
    fun setUp() {
        library = dir.resolve("exercises.yaml").apply { writeText("exercises: []\n") }
        targets = dir.resolve("backup-targets.json")
        folder = FakeFolder()
    }

    private fun session(
        id: String,
        startedAt: String,
    ) = Session(
        1,
        id,
        null,
        null,
        null,
        null,
        startedAt,
        null,
        listOf(SessionEntry.Reps("squat", listOf(RepsSetLog(8, restTakenSec = 60.0)))),
    )

    private val june = session("june", "2026-06-01T09:00:00.000Z")
    private val july = session("july", "2026-07-01T09:00:00.000Z")

    private fun backUp(
        sessions: List<Session>,
        into: BackupFolder? = folder,
    ) = backUpNow(into, library, targets, sessions, "2026-08-02T12:00:00.000Z")

    private fun archivedIds(text: String?) =
        (
            (
                (
                    loadYaml(
                        text!!,
                    ) as YamlLoad.Loaded
                ).value as Map<*, *>
            )["sessions"] as List<*>
        ).map { (it as Map<*, *>)["id"] }

    @Test
    fun `writes both artefacts into the chosen folder`() {
        assertNull(backUp(listOf(july, june)))

        assertEquals(listOf(LIBRARY_BACKUP_NAME, HISTORY_BACKUP_NAME), folder.created)
        assertEquals("exercises: []\n", folder.contentsOf(LIBRARY_BACKUP_NAME))
    }

    @Test
    fun `orders the archived log oldest-first regardless of the order it was given`() {
        backUp(listOf(july, june))

        assertEquals(listOf("june", "july"), archivedIds(folder.contentsOf(HISTORY_BACKUP_NAME)))
    }

    /** The duplicate-file regression: `createDocument` uniquifies a name rather than overwriting it. */
    @Test
    fun `overwrites the files it wrote last time instead of creating a second copy`() {
        folder.add(LIBRARY_BACKUP_NAME, "old library")
        folder.add(HISTORY_BACKUP_NAME, "old history")

        assertNull(backUp(listOf(june)))

        assertEquals(emptyList<String>(), folder.created)
        assertEquals("exercises: []\n", folder.contentsOf(LIBRARY_BACKUP_NAME))
        assertEquals(listOf("june"), archivedIds(folder.contentsOf(HISTORY_BACKUP_NAME)))
    }

    @Test
    fun `ignores the user's own files in the same folder`() {
        folder.add("tax-return.pdf", "")
        folder.add(LIBRARY_BACKUP_NAME, "")

        backUp(listOf(june))

        assertEquals("", folder.contentsOf("tax-return.pdf"))
    }

    @Test
    fun `skips the library when there is no library file to copy, and still archives the log`() {
        library.delete()

        assertNull(backUp(listOf(june)))

        assertEquals(listOf(HISTORY_BACKUP_NAME), folder.created)
    }

    @Nested
    inner class OnAProviderWhoseDocumentIdsAreNotNames {
        @BeforeEach
        fun opaque() {
            folder.opaqueNames = true
        }

        @Test
        fun `writes into the pair the last backup made instead of creating another`() {
            backUp(listOf(june))
            folder.created.clear()

            assertNull(backUp(listOf(june, july)))

            assertEquals(emptyList<String>(), folder.created)
            assertEquals(listOf("june", "july"), archivedIds(folder.contentsAt(1)))
        }

        // A remembered URI only counts while the folder still lists it.
        @Test
        fun `recreates a remembered file that is no longer in the folder`() {
            backUp(listOf(june))
            folder.remove(1)
            folder.created.clear()

            backUp(listOf(june, july))

            assertEquals(listOf(HISTORY_BACKUP_NAME), folder.created)
        }

        /** A write that throws has still left a document behind, so the retry has to find it. */
        @Test
        fun `remembers a file it created even when writing into it failed`() {
            folder.refuseNextWrite = "provider refused"

            assertEquals(BackupFailure.WriteFailed("provider refused"), backUp(listOf(june)))

            folder.created.clear()
            assertNull(backUp(listOf(june)))
            assertEquals(listOf(HISTORY_BACKUP_NAME), folder.created)
            assertEquals("exercises: []\n", folder.contentsAt(0))
        }

        @Test
        fun `backs up anyway when the record of last time is unreadable`() {
            targets.writeText("{ not json")

            assertNull(backUp(listOf(june)))
            assertEquals(2, folder.created.size)
        }

        @Test
        fun `backs up anyway when the record cannot be saved`() {
            targets.mkdirs()

            assertNull(backUp(listOf(june)))
            assertEquals("exercises: []\n", folder.contentsAt(0))
        }
    }

    @Nested
    inner class WhenItCannotBackUp {
        @Test
        fun `reports the missing folder rather than writing anywhere`() {
            assertEquals(BackupFailure.NoFolder, backUp(listOf(june), into = null))
        }

        // Deleted, or the grant revoked in system settings.
        @Test
        fun `reports a folder that can no longer be reached`() {
            folder.reachable = false

            assertEquals(BackupFailure.Unreachable, backUp(listOf(june)))
            assertEquals(emptyList<String>(), folder.created)
        }

        @Test
        fun `returns the reason instead of throwing when the write is refused`() {
            folder.refuseNextWrite = "disk full"

            assertEquals(BackupFailure.WriteFailed("disk full"), backUp(listOf(june)))
        }

        @Test
        fun `does not throw when the folder itself refuses to be listed`() {
            folder.refuseList = true

            assertEquals(BackupFailure.WriteFailed("listing refused"), backUp(listOf(june)))
        }
    }
}

/**
 * A SAF folder in memory: documents in order, by URI. With [opaqueNames], a document's name is its id,
 * as Drive's are, where the display name doesn't come back.
 */
private class FakeFolder : BackupFolder {
    private val documents = mutableListOf<Pair<BackupDocument, StringBuilder>>()
    val created = mutableListOf<String>()
    var opaqueNames = false
    var reachable = true
    var refuseList = false
    var refuseNextWrite: String? = null

    fun add(
        name: String,
        contents: String,
    ) {
        documents += BackupDocument(name, "content://fake/$name") to StringBuilder(contents)
    }

    fun remove(index: Int) {
        documents.removeAt(index)
    }

    fun contentsOf(name: String) = documents.firstOrNull { it.first.name == name }?.second?.toString()

    fun contentsAt(index: Int) = documents[index].second.toString()

    override fun reachable() = reachable

    override fun list(): List<BackupDocument> {
        if (refuseList) throw IOException("listing refused")
        return documents.map { it.first }
    }

    override fun create(name: String): BackupDocument {
        created += name
        val id = "doc-${documents.size}"
        val document = BackupDocument(if (opaqueNames) id else name, "content://fake/$id")
        documents += document to StringBuilder()
        return document
    }

    override fun write(
        document: BackupDocument,
        text: String,
    ) {
        refuseNextWrite?.let {
            refuseNextWrite = null
            throw IOException(it)
        }
        val contents = documents.first { it.first.uri == document.uri }.second
        contents.setLength(0)
        contents.append(text)
    }
}
