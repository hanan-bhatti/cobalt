package com.example.notes

import com.example.data.model.SavedItem
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

object NoteExport {
    /** The caller owns the destination; no Markdown body is loaded into a whole String. */
    fun write(note: SavedItem, store: NoteBodyStore, output: OutputStream, checkCancelled: () -> Unit = {}) {
        val hash = note.contentHash ?: throw IOException("Body reference is missing")
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val frontmatter = "---\ntitle: ${JSONObject.quote(note.title)}\ntags: [${note.folders.joinToString(", ") { JSONObject.quote(it) }}]\ncreated: ${JSONObject.quote(date.format(java.util.Date(note.timestamp)))}\nupdated: ${JSONObject.quote(date.format(java.util.Date(note.updatedAt)))}\n---\n"
        output.write(frontmatter.toByteArray(Charsets.UTF_8))
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        store.open(note.id, hash).use { input ->
            val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                bytes += count
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(actual == hash && bytes == note.byteLength) { "Export verification failed; original retained" }
    }
}
