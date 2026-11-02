package com.example.notes

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyNoteRecoveryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var reader: LegacyNoteRecovery

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        reader = LegacyNoteRecovery(context, database)
    }

    @After fun tearDown() { database.close() }

    private suspend fun insert(body: String) {
        database.savedItemDao().insertItem(SavedItemEntity(
            id = "original", type = "TEXT", title = "Existing note", content = body,
            timestamp = 1, foldersJson = "[]", extractedText = null, thumbnailPath = null
        ))
    }

    @Test fun recoveryRowsAndSlicesDoNotLoadOrOverwriteTheOriginal() = runBlocking {
        val body = (1..5_000).joinToString("\n\n") { "[Link $it](https://example.org/$it)" }
        insert(body)
        val summary = reader.observeNotes().first().single()
        assertEquals(body.toByteArray(Charsets.UTF_8).size.toLong(), summary.byteLength)
        assertEquals("Existing note", summary.title)
        assertEquals(NoteSizePolicy.IO_CHUNK_BYTES,
            database.savedItemDao().readRecoveryBytes("original", 1, Int.MAX_VALUE)!!.size)
        assertEquals(body, LegacyBodyInputStream(database.savedItemDao(), "original").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    @Test fun everyUtf8BoundaryPreservesEmojiMultibyteCharactersNullsAndLineEndings() = runBlocking {
        val body = "a😀漢字\u0000\r\nb\n".repeat(25)
        insert(body)
        val summary = reader.observeNotes().first().single()
        for (chunkSize in listOf(1, 2, 3, 4, 7, 31, 128)) {
            val actual = buildString {
                val count = ((summary.byteLength + chunkSize - 1) / chunkSize).toInt()
                for (index in 0 until count) append(reader.displayChunk(summary, index, chunkSize))
            }
            assertEquals("chunk size $chunkSize", body, actual)
        }
    }

    @Test fun rawRecoveryExportIsByteExactAndLeavesTheOriginalIntact() = runBlocking {
        val body = "# Keep me\r\n😀\u0000\n".repeat(2_000)
        insert(body)
        val note = reader.observeNotes().first().single()
        val output = File.createTempFile("recovery-export", ".md", context.cacheDir)
        try {
            reader.export(note, Uri.fromFile(output)) {}
            assertArrayEquals(body.toByteArray(Charsets.UTF_8), output.readBytes())
            assertEquals(body, LegacyBodyInputStream(database.savedItemDao(), note.id).bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { output.delete() }
    }
}
