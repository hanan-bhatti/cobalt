package com.example.notes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Room's generated schema validation against a historical v8 fixture. */
@RunWith(AndroidJUnit4::class)
class NoteMigrationTest {
    @Test fun emptyDatabaseMigratesWithoutInventingNotes() = verify(emptyList())
    @Test fun normalAndFiveThousandLinkNotesKeepExactBodies() = verify(listOf(
        "one line", "", "😀漢字\u0000\r\n".repeat(100),
        (1..5_000).joinToString("\n\n") { "[Test link $it](https://example.org/$it)" }
    ))

    private fun verify(bodies: List<String>) = runBlocking {
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            check(context.packageName.endsWith(".debug"))
            val name = "note-migration-test-${UUID.randomUUID()}.db"
            val ids = bodies.map { UUID.randomUUID().toString() }
            val store = PrivateMarkdownStore(context)
            val path = context.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            var room: AppDatabase? = null
            try {
                SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
                    val schema = InstrumentationRegistry.getInstrumentation().context.assets
                        .open("room-v8.sql").bufferedReader().use { it.readText() }
                    schema.lineSequence().filterNot { it.trim().startsWith("--") }.joinToString("\n")
                        .split(';').filter { it.isNotBlank() }.forEach { legacy.execSQL(it) }
                    val columns = mutableListOf<Pair<String, String>>()
                    legacy.rawQuery("PRAGMA table_info(saved_items)", null).use { cursor ->
                        while (cursor.moveToNext()) if (cursor.getInt(3) != 0) columns.add(cursor.getString(1) to cursor.getString(2))
                    }
                    bodies.forEachIndexed { index, body ->
                        val values = ContentValues()
                        columns.forEach { (column, type) ->
                            if (type == "TEXT") values.put(column, if (column.endsWith("Json")) "[]" else "")
                            else values.put(column, 0)
                        }
                        values.put("id", ids[index]); values.put("type", "TEXT")
                        values.put("title", "Original $index"); values.put("content", body)
                        values.put("timestamp", 1L); values.put("updatedAt", 1L)
                        legacy.insertOrThrow("saved_items", null, values)
                    }
                    legacy.version = 8
                }
                val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(AppDatabase.MIGRATION_8_9).build().also { room = it }
                database.openHelper.writableDatabase // Runs Android Room's generated migration validator.
                val dao = database.savedItemDao()
                assertEquals(bodies.size, dao.getAllItems().size)
                val persistence = NotePersistence(context, database, store)
                bodies.forEachIndexed { index, expected ->
                    val id = ids[index]
                    assertEquals(expected, LegacyBodyInputStream(dao, id).bufferedReader().use { it.readText() })
                    val ref = NoteWriteLock.mutex.withLock { persistence.migrateOneLocked(id) }
                    assertEquals("FILE", ref.bodyLocation)
                    assertTrue(store.verify(id, ref.contentHash!!, expected.toByteArray(Charsets.UTF_8).size.toLong()))
                    assertEquals(expected, store.open(id, ref.contentHash).bufferedReader().use { it.readText() })
                    assertTrue(dao.readRecoveryBytes(id, 1, 16_384)!!.isEmpty())
                }
            } finally {
                room?.close()
                context.deleteDatabase(name)
                ids.forEach { store.deleteAll(it) }
            }
        }
    }
}
