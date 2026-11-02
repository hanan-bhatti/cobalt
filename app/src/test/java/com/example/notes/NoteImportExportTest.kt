package com.example.notes

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoteImportExportTest {
    @Test fun exportedMarkdownRoundTripsBodyAndMetadata() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = PrivateMarkdownStore(context)
        val id = java.util.UUID.randomUUID().toString()
        val body = "# Keep exactly\r\n😀漢字\u0000\n---\n".repeat(100)
        try {
            val ref = body.byteInputStream().use { store.writeAtomic(id, it) }
            val original = SavedItem(id = id, type = SavedItemType.TEXT, title = "Quoted \" title 😀",
                content = "", folders = listOf("a,b", "漢字", "line\nbreak"), timestamp = 1_000L, updatedAt = 2_000L,
                bodyLocation = "FILE", contentHash = ref.hash, byteLength = ref.bytes)
            val bytes = ByteArrayOutputStream().also { NoteExport.write(original, store, it) }.toByteArray()
            val imported = NoteImport.prepare(ByteArrayInputStream(bytes), "Fallback")
            assertEquals(original.title, imported.note.title)
            assertEquals(original.folders, imported.note.folders)
            assertEquals(original.timestamp, imported.note.timestamp)
            assertEquals(original.updatedAt, imported.note.updatedAt)
            assertEquals(body, imported.body.bufferedReader().use { it.readText() })
        } finally { store.deleteAll(id) }
    }

    @Test fun ordinaryYamlAndIncompleteHeadersAreNeverConsumed() {
        val cases = listOf("", "one line", "---\ntitle: ordinary\n---\nbody", "---\ncobalt_format: 1\n",
            "---\n" + "x".repeat(NoteImport.HEADER_MAX_BYTES + 10) + "\n---\nbody")
        for (body in cases) {
            val prepared = NoteImport.prepare(body.byteInputStream(), "Fallback")
            assertEquals("Fallback", prepared.note.title)
            assertEquals(body, prepared.body.bufferedReader().use { it.readText() })
        }
    }
}
