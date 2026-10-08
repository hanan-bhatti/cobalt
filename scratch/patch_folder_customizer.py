import re

with open("app/src/main/kotlin/com/example/ui/screens/FoldersScreen.kt", "r") as f:
    content = f.read()

# Modify FolderCustomizerDialog to get count
old_dialog = """fun FolderCustomizerDialog(
    folder: CustomFolderEntity,
    viewModel: CobaltViewModel,
    onDismiss: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {"""

new_dialog = """fun FolderCustomizerDialog(
    folder: CustomFolderEntity,
    viewModel: CobaltViewModel,
    onDismiss: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val allItems by viewModel.savedItems.collectAsState()
    val itemCount = remember(allItems, folder.name) {
        allItems.count { it.folders.contains(folder.name) }
    }

    if (showDeleteConfirm) {"""
content = content.replace(old_dialog, new_dialog)

old_bottom_sheet = """    com.example.ui.components.FolderBottomSheet(
        isVisible = !showDeleteConfirm,
        onDismiss = onDismiss,
        initialFolder = folder,
        onSave = { updatedFolder, originalName ->"""

new_bottom_sheet = """    com.example.ui.components.FolderBottomSheet(
        isVisible = !showDeleteConfirm,
        onDismiss = onDismiss,
        initialFolder = folder,
        initialItemCount = itemCount,
        onSave = { updatedFolder, originalName ->"""
content = content.replace(old_bottom_sheet, new_bottom_sheet)

with open("app/src/main/kotlin/com/example/ui/screens/FoldersScreen.kt", "w") as f:
    f.write(content)
