import re

with open("app/src/main/kotlin/com/example/ui/screens/FoldersScreen.kt", "r") as f:
    content = f.read()

# Add Archive item to the end of the grid
archive_item = """
                        // Archive Section
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            val archiveCount = remember(allItems) {
                                allItems.count { it.isArchived || it.folders.contains("Archive") }
                            }
                            SystemCategoryCard(
                                name = "Archive",
                                count = archiveCount,
                                iconResId = com.example.R.drawable.ic_custom_archive,
                                onClick = { activeBrowseFolder = "Archive" },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }"""

content = content.replace("""                        }
                    }
                }
            }""", archive_item, 1)

with open("app/src/main/kotlin/com/example/ui/screens/FoldersScreen.kt", "w") as f:
    f.write(content)
