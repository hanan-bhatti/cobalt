package com.example.notes

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.SavedItem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TextFileImporter(private val context: Context) {
    suspend fun importFile(uri: Uri, progress: (Long) -> Unit = {}): SavedItem = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a document from a file provider" }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        check(TextFiles.accepts(name, context.contentResolver.getType(uri))) { "Only text documents are supported" }
        context.contentResolver.openInputStream(uri)?.use { source ->
            TextFiles.openUtf8(source).use { text ->
                val prepared = NoteImport.prepare(text, TextFiles.title(name ?: "Imported note"))
                prepared.body.use { NotePersistence(context).importMarkdown(prepared.note, it, progress) }
            }
        } ?: throw IOException("Document is unavailable; original retained")
    }
}
