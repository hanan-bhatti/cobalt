import re

with open("app/src/main/kotlin/com/example/ui/components/FolderBottomSheet.kt", "r") as f:
    content = f.read()

# Add initialItemCount parameter
content = content.replace("initialFolder: CustomFolderEntity? = null,", "initialFolder: CustomFolderEntity? = null,\n    initialItemCount: Int = 0,")

# Update state variables
content = content.replace("""    var folderNameInput by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf(folderPresetColors.first().first) }
    var selectedIconName by remember { mutableStateOf(folderPresetIcons.first()) }
    var isPinned by remember { mutableStateOf(false) }
    val originalName = remember { initialFolder?.name }""", """    var folderNameInput by remember(isVisible, initialFolder) { mutableStateOf(if (isVisible) initialFolder?.name ?: "" else "") }
    var selectedColorHex by remember(isVisible, initialFolder) { mutableStateOf(if (isVisible) initialFolder?.colorHex ?: folderPresetColors.first().first else folderPresetColors.first().first) }
    var selectedIconName by remember(isVisible, initialFolder) { mutableStateOf(if (isVisible) initialFolder?.iconName ?: folderPresetIcons.first() else folderPresetIcons.first()) }
    var isPinned by remember(isVisible, initialFolder) { mutableStateOf(if (isVisible) initialFolder?.isPinned ?: false else false) }
    val originalName = remember(isVisible, initialFolder) { if (isVisible) initialFolder?.name else null }""")

# Remove the reset logic in LaunchedEffect since we handle it in remember now
reset_logic = """    // Reset state when opened
    LaunchedEffect(isVisible) {
        if (isVisible) {
            folderNameInput = initialFolder?.name ?: ""
            selectedColorHex = initialFolder?.colorHex ?: folderPresetColors.first().first
            selectedIconName = initialFolder?.iconName ?: folderPresetIcons.first()
            isPinned = initialFolder?.isPinned ?: false
            try {
                titleFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus error
            }
        }
    }"""

new_logic = """    LaunchedEffect(isVisible) {
        if (isVisible) {
            try {
                titleFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus error
            }
        }
    }"""
content = content.replace(reset_logic, new_logic)

# Replace hardcoded count = 0
content = content.replace("count = 0,", "count = initialItemCount,")

with open("app/src/main/kotlin/com/example/ui/components/FolderBottomSheet.kt", "w") as f:
    f.write(content)
