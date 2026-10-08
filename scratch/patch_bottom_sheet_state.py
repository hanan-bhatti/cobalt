import re

with open("app/src/main/kotlin/com/example/ui/components/FolderBottomSheet.kt", "r") as f:
    content = f.read()

old_state = """    var folderNameInput by remember(isVisible, initialFolder) { mutableStateOf(if (isVisible) initialFolder?.name ?: "" else "") }
    var selectedColorHex by remember(isVisible, initialFolder) { mutableStateOf(if (isVisible) initialFolder?.colorHex ?: folderPresetColors.first().first else folderPresetColors.first().first) }
    var selectedIconName by remember(isVisible, initialFolder) { mutableStateOf(if (isVisible) initialFolder?.iconName ?: folderPresetIcons.first() else folderPresetIcons.first()) }
    var isPinned by remember(isVisible, initialFolder) { mutableStateOf(if (isVisible) initialFolder?.isPinned ?: false else false) }
    val originalName = remember(isVisible, initialFolder) { if (isVisible) initialFolder?.name else null }"""

new_state = """    var folderNameInput by remember { mutableStateOf(initialFolder?.name ?: "") }
    var selectedColorHex by remember { mutableStateOf(initialFolder?.colorHex ?: folderPresetColors.first().first) }
    var selectedIconName by remember { mutableStateOf(initialFolder?.iconName ?: folderPresetIcons.first()) }
    var isPinned by remember { mutableStateOf(initialFolder?.isPinned ?: false) }
    var originalName by remember { mutableStateOf(initialFolder?.name) }

    var wasVisible by remember { mutableStateOf(isVisible) }
    if (isVisible && !wasVisible) {
        folderNameInput = initialFolder?.name ?: ""
        selectedColorHex = initialFolder?.colorHex ?: folderPresetColors.first().first
        selectedIconName = initialFolder?.iconName ?: folderPresetIcons.first()
        isPinned = initialFolder?.isPinned ?: false
        originalName = initialFolder?.name
        wasVisible = true
    } else if (!isVisible && wasVisible) {
        wasVisible = false
    }"""

content = content.replace(old_state, new_state)

with open("app/src/main/kotlin/com/example/ui/components/FolderBottomSheet.kt", "w") as f:
    f.write(content)
