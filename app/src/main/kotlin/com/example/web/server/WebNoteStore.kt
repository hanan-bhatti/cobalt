package com.example.web.server

import com.example.data.model.SavedItem
import com.example.data.repository.CobaltRepository
import com.example.data.local.CustomFolderEntity
import kotlinx.coroutines.flow.first

/** Test seam; production delegates every body read/write to the native note repository. */
interface WebNoteStore {
    suspend fun list(query: String?): List<SavedItem>
    suspend fun read(id: String): SavedItem?
    suspend fun save(item: SavedItem, beforeCommit: () -> Unit): SavedItem
    suspend fun create(item: SavedItem): SavedItem = error("Capture unavailable")
    suspend fun folders(): List<CustomFolderEntity> = list(null).flatMap { it.folders }.distinct().map { CustomFolderEntity(it) }
    suspend fun createFolder(name: String, color: String? = null, icon: String? = null, isPinned: Boolean = false) { error("Folder creation unavailable") }

    companion object { const val MAX_BODY_BYTES = 5_000_000 }
}

class RepositoryWebNoteStore(private val repository: CobaltRepository) : WebNoteStore {
    override suspend fun list(query: String?): List<SavedItem> {
        val all = repository.getAllItems().filterNot { it.isDeleted }
        if (query.isNullOrBlank()) return all
        val bodyMatches = repository.searchNoteFiles(query).map { it.id }.toSet()
        return all.filter { it.id in bodyMatches || it.title.contains(query, true) ||
            it.content.contains(query, true) || it.preview.contains(query, true) }
    }
    override suspend fun read(id: String) = repository.getItemForWeb(id, WebNoteStore.MAX_BODY_BYTES)
    override suspend fun save(item: SavedItem, beforeCommit: () -> Unit) = repository.saveNoteFromWeb(item, beforeCommit)
    override suspend fun create(item: SavedItem) = repository.saveItem(item)
    override suspend fun folders() = repository.getAllFolderEntitiesFlow().first()
    override suspend fun createFolder(name: String, color: String?, icon: String?, isPinned: Boolean) { repository.addCustomFolder(name, color, icon, isPinned) }
}
