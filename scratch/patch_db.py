import re

with open("app/src/main/kotlin/com/example/data/local/AppDatabase.kt", "r") as f:
    content = f.read()

# Change version from 3 to 4
content = re.sub(r'version = 3', r'version = 4', content)

# Add Migration(3, 4)
migration_code = """
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_items ADD COLUMN releaseYear TEXT")
        db.execSQL("ALTER TABLE saved_items ADD COLUMN rating REAL")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_items ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
    }
}
"""
content = content.replace("""
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_items ADD COLUMN releaseYear TEXT")
        db.execSQL("ALTER TABLE saved_items ADD COLUMN rating REAL")
    }
}
""", migration_code)

# Add it to addMigrations
content = content.replace("addMigrations(MIGRATION_1_2, MIGRATION_2_3)", "addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)")

with open("app/src/main/kotlin/com/example/data/local/AppDatabase.kt", "w") as f:
    f.write(content)
