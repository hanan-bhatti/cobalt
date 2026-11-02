package com.example.notes

import android.app.Application
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
        val id = java.util.UUID.randomUUID().toString()
        val body = "# Keep exactly\r\n😀漢字\u0000\n---\n".repeat(100)
        val bytesOfBody = body.toByteArray(Charsets.UTF_8)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytesOfBody)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        // This test checks the streaming export/import contract, not Android directory fsync.
        // Real atomic storage is covered by the instrumented MarkdownStorageTest.
        val store = object : NoteBodyStore {
            override fun open(id: String, hash: String) = ByteArrayInputStream(bytesOfBody)
            override fun size(id: String, hash: String) = bytesOfBody.size.toLong()
            override fun verify(id: String, hash: String, bytes: Long) = bytes == bytesOfBody.size.toLong()
            override fun readRange(id: String, hash: String, offset: Long, count: Int): ByteArray = error("Not used by export")
            override fun writeAtomic(id: String, input: java.io.InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef = error("Not used by export")
            override fun delete(id: String, hash: String) = Unit
            override fun deleteAll(id: String) = Unit
        }
        val ref = NoteBodyRef(hash, bytesOfBody.size.toLong(), "")
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
