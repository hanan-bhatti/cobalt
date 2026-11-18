package com.example.widget

import com.example.data.model.SavedItem

enum class FolioOrder(val label: String) {
    UPDATED("Recently updated"), NEWEST("Newest saved"), OLDEST("Oldest saved")
}

data class FolioConfig(
    val folderName: String? = null,
    val colorHex: String? = null,
    val order: FolioOrder = FolioOrder.UPDATED,
    val removedFolder: String? = null
)

object FolioItems {
    fun select(items: List<SavedItem>, config: FolioConfig): List<SavedItem> {
        val folder = config.folderName ?: return emptyList()
        val eligible = items.filter { !it.isDeleted && !it.isArchived && folder in it.folders }
        return when (config.order) {
            FolioOrder.UPDATED -> eligible.sortedWith(compareByDescending<SavedItem> { it.updatedAt }.thenBy { it.id })
            FolioOrder.NEWEST -> eligible.sortedWith(compareByDescending<SavedItem> { it.timestamp }.thenBy { it.id })
            FolioOrder.OLDEST -> eligible.sortedWith(compareBy<SavedItem> { it.timestamp }.thenBy { it.id })
        }
    }
}
