with open("app/src/main/kotlin/com/example/data/model/SavedItem.kt", "r") as f:
    content = f.read()

old_fields = """    val releaseYear: String? = null,
    val rating: Double? = null
)"""

new_fields = """    val releaseYear: String? = null,
    val rating: Double? = null,
    val isArchived: Boolean = false
)"""
content = content.replace(old_fields, new_fields)

with open("app/src/main/kotlin/com/example/data/model/SavedItem.kt", "w") as f:
    f.write(content)
