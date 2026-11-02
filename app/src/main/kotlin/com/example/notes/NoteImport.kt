package com.example.notes

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONTokener

/** Recognizes Cobalt exports with bounded lookahead; other Markdown is preserved verbatim. */
object NoteImport {
    const val HEADER_MAX_BYTES = 64 * 1024
    data class Prepared(val note: SavedItem, val body: InputStream)

    fun prepare(source: InputStream, fallbackTitle: String): Prepared {
        val input = BufferedInputStream(source, NoteSizePolicy.IO_CHUNK_BYTES)
        input.mark(HEADER_MAX_BYTES + 1)
        val bytes = java.io.ByteArrayOutputStream()
        var line = StringBuilder()
        val lines = mutableListOf<String>()
        var complete = false
        while (bytes.size() < HEADER_MAX_BYTES) {
            val value = input.read()
            if (value < 0) break
            bytes.write(value)
            if (value == 10) {
                val text = line.toString().removeSuffix("\r")
                line = StringBuilder()
                lines.add(text)
                if (lines.size == 1 && text != "---") break
                if (lines.size > 1 && text == "---") { complete = true; break }
            } else line.append(value.toChar())
        }
        val fallback = SavedItem(title = fallbackTitle, content = "", type = SavedItemType.TEXT)
        if (complete) {
            val header = bytes.toByteArray().toString(Charsets.UTF_8).lineSequence()
                .filter { ": " in it }.associate { it.substringBefore(": ") to it.substringAfter(": ") }
            val parsed = runCatching {
                require(header["cobalt_format"] == "1")
                fun string(key: String) = JSONTokener(header.getValue(key)).nextValue() as String
                val tags = JSONArray(header.getValue("tags"))
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                    timeZone = TimeZone.getTimeZone("UTC"); isLenient = false
                }
                fallback.copy(title = string("title"), folders = (0 until tags.length()).map { tags.getString(it) },
                    timestamp = date.parse(string("created"))!!.time, updatedAt = date.parse(string("updated"))!!.time)
            }.getOrNull()
            if (parsed != null) return Prepared(parsed, input)
        }
        input.reset()
        return Prepared(fallback, input)
    }
}
