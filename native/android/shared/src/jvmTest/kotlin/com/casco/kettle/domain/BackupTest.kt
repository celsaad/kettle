package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** The backup folder's pure half (#237): `backup.test.ts`'s `backupFolderLabel`, and the record of last time. */
class BackupTest {
    @Nested
    inner class FolderLabel {
        // Purely so the user recognises what they picked. It is their own path, so it renders verbatim.
        @Test
        fun `shows the path under the volume rather than the whole tree URI`() {
            assertEquals(
                "Documents/Kettle",
                backupFolderLabel("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FKettle"),
            )
        }

        /**
         * A provider whose document id isn't `<volume>:<path>` has no path to show. Asserted exactly: a
         * cut at the last colon finds the scheme's, and answers `//provider/tree/opaque-id`.
         */
        @Test
        fun `falls back to the whole URI, unmangled, when there is no volume marker to cut at`() {
            assertEquals("content://provider/tree/opaque-id", backupFolderLabel("content://provider/tree/opaque-id"))
        }

        // decodeURIComponent's UTF-8, which a folder named in Portuguese or Japanese needs.
        @Test
        fun `decodes a path that isn't ASCII`() {
            assertEquals(
                "Documentos/Treino",
                backupFolderLabel("content://com.android.externalstorage.documents/tree/primary%3ADocumentos%2FTreino"),
            )
            assertEquals(
                "書類/ケトル",
                backupFolderLabel(
                    "content://com.android.externalstorage.documents/tree/primary%3A" +
                        "%E6%9B%B8%E9%A1%9E%2F%E3%82%B1%E3%83%88%E3%83%AB",
                ),
            )
        }

        // decodeURIComponent throws on a stray %, and RN answered the URI as it was.
        @Test
        fun `answers the URI as it is when it won't decode`() {
            assertEquals(
                "content://provider/tree/primary%3A50%",
                backupFolderLabel("content://provider/tree/primary%3A50%"),
            )
        }
    }

    @Nested
    inner class Targets {
        @Test
        fun `reads back what it wrote`() {
            val targets =
                mapOf(
                    "kettle-library.yaml" to "content://drive/doc=a\"b",
                    "kettle-history.yaml" to "content://drive/c",
                )

            assertEquals(targets, parseBackupTargets(serializeBackupTargets(targets)))
        }

        // A convenience: losing it costs one more pair of files on a provider that needs it, never the backup.
        @Test
        fun `reads an unreadable record as nothing remembered`() {
            assertEquals(emptyMap<String, String>(), parseBackupTargets("{ not json"))
            assertEquals(emptyMap<String, String>(), parseBackupTargets("""{"kettle-library.yaml": 3}"""))
            assertEquals(emptyMap<String, String>(), parseBackupTargets("[]"))
        }

        // RN wrote the record with JSON.stringify, and an upgrade mustn't forget what it remembered.
        @Test
        fun `reads the record RN wrote`() {
            assertEquals(
                mapOf("kettle-history.yaml" to "content://drive/c"),
                parseBackupTargets("""{"kettle-history.yaml":"content://drive/c"}"""),
            )
        }
    }
}
