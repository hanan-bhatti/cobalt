package com.example.ui.dock

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.notes.LegacyBodyInputStream
import com.example.notes.PrivateMarkdownStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Reader

/** Detail-only bounded read. Recent cards continue to use Room's short projections. */
internal suspend fun loadDockNotePreview(context: Context, item: SavedItem): String = withContext(Dispatchers.IO) {
    val stream = if (item.bodyLocation == "FILE" && item.contentHash != null) {
        PrivateMarkdownStore(context).open(item.id, item.contentHash)
    } else LegacyBodyInputStream(AppDatabase.getDatabase(context).savedItemDao(), item.id)
    stream.reader(Charsets.UTF_8).use(::readDockPreview)
}

internal fun readDockPreview(reader: Reader): String {
    val buffer = CharArray(DOCK_NOTE_PREVIEW_CHARACTERS)
    var count = 0
    while (count < buffer.size) {
        val read = reader.read(buffer, count, buffer.size - count)
        if (read < 0) break
        if (read == 0) break
        count += read
    }
    // Do not end a preview halfway through an emoji.
    if (count > 0 && buffer[count - 1].isHighSurrogate()) count--
    val text = String(buffer, 0, count)
    var lines = 0
    for (index in text.indices) {
        if (text[index] == '\n' || (text[index] == '\r' && text.getOrNull(index + 1) != '\n')) {
            if (++lines == DOCK_NOTE_PREVIEW_LINES) return text.substring(0, index + 1)
        }
    }
    return text
}

internal const val DOCK_NOTE_PREVIEW_CHARACTERS = 4096
internal const val DOCK_NOTE_PREVIEW_LINES = 120
