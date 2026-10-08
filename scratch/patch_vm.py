with open("app/src/foss/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt", "r") as f:
    content = f.read()

# Map out Archive from flows
content = content.replace(
    "repository.getAllFoldersFlow()",
    "repository.getAllFoldersFlow().map { folders -> folders.filter { it != \"Archive\" } }"
)
content = content.replace(
    "repository.getAllFolderEntitiesFlow()",
    "repository.getAllFolderEntitiesFlow().map { folders -> folders.filter { it.name != \"Archive\" } }"
)
import re
content = re.sub(r'import kotlinx\.coroutines\.flow\.map\n?', '', content)
content = content.replace("import kotlinx.coroutines.flow.combine", "import kotlinx.coroutines.flow.combine\nimport kotlinx.coroutines.flow.map")

# Patch archiveItem / unarchiveItem
old_archive = """    fun archiveItem(item: SavedItem) {
        viewModelScope.launch {
            // Pre-create "Archive" custom folder if it does not exist
            if (!customFolders.value.contains("Archive")) {
                repository.addCustomFolder("Archive")
            }
            val updatedFolders = if (item.folders.contains("Archive")) item.folders else item.folders + "Archive"
            repository.saveItem(item.copy(folders = updatedFolders))
            showToast("Item archived successfully.")
        }
    }

    fun unarchiveItem(item: SavedItem) {
        viewModelScope.launch {
            val updatedFolders = item.folders.filter { it != "Archive" }
            repository.saveItem(item.copy(folders = updatedFolders))
            showToast("Item unarchived successfully.")
        }
    }"""

new_archive = """    fun archiveItem(item: SavedItem) {
        viewModelScope.launch {
            repository.saveItem(item.copy(isArchived = true))
            showToast("Item archived successfully.")
        }
    }

    fun unarchiveItem(item: SavedItem) {
        viewModelScope.launch {
            val updatedFolders = item.folders.filter { it != "Archive" }
            repository.saveItem(item.copy(isArchived = false, folders = updatedFolders))
            showToast("Item unarchived successfully.")
        }
    }"""
content = content.replace(old_archive, new_archive)

# Patch filteredItems
old_filter = """        // 1. Filter by Folder (System Category or Custom Folder)
        if (folder != "All") {
            val systemCategory = SavedItemType.entries.find { it.displayName == folder }
            filtered = if (systemCategory != null) {
                // System folder filter (e.g. Images, Links, Text, etc.) - hide archived
                filtered.filter { it.type == systemCategory && !it.folders.contains("Archive") }
            } else {
                // Custom folder filter (e.g. "Work" or "Archive")
                filtered.filter { it.folders.contains(folder) }
            }
        } else {
            // Hide archived items from "All" main feed
            filtered = filtered.filter { !it.folders.contains("Archive") }
        }"""

new_filter = """        // 1. Filter by Folder (System Category or Custom Folder)
        val isItemArchived: (SavedItem) -> Boolean = { it.isArchived || it.folders.contains("Archive") }
        
        if (folder != "All") {
            val systemCategory = SavedItemType.entries.find { it.displayName == folder }
            filtered = if (systemCategory != null) {
                // System folder filter - hide archived
                filtered.filter { it.type == systemCategory && !isItemArchived(it) }
            } else {
                if (folder == "Archive") {
                    // Show ONLY archived items
                    filtered.filter { isItemArchived(it) }
                } else {
                    // Custom folder filter
                    filtered.filter { it.folders.contains(folder) && !isItemArchived(it) }
                }
            }
        } else {
            // Hide archived items from "All" main feed
            filtered = filtered.filter { !isItemArchived(it) }
        }"""
content = content.replace(old_filter, new_filter)

with open("app/src/foss/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt", "w") as f:
    f.write(content)
