with open("app/src/main/kotlin/com/example/ui/screens/FoldersScreen.kt", "r") as f:
    content = f.read()

old_filter = """        val systemCategory = SavedItemType.entries.find { it.displayName == folderName }
        if (systemCategory != null) {
            items.filter { it.type == systemCategory && !it.folders.contains("Archive") }
        } else {
            items.filter { it.folders.contains(folderName) }
        }"""

new_filter = """        val isItemArchived: (com.example.domain.model.SavedItem) -> Boolean = { it.isArchived || it.folders.contains("Archive") }
        val systemCategory = SavedItemType.entries.find { it.displayName == folderName }
        if (systemCategory != null) {
            items.filter { it.type == systemCategory && !isItemArchived(it) }
        } else {
            if (folderName == "Archive") {
                items.filter { isItemArchived(it) }
            } else {
                items.filter { it.folders.contains(folderName) && !isItemArchived(it) }
            }
        }"""
content = content.replace(old_filter, new_filter)

with open("app/src/main/kotlin/com/example/ui/screens/FoldersScreen.kt", "w") as f:
    f.write(content)
