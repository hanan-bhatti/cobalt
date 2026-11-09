package com.example.notes

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDraftRecoveryTest {
    private lateinit var context: Context
    private lateinit var originalContext: Context
    private lateinit var directory: File
    private lateinit var database: AppDatabase
    private lateinit var persistence: NotePersistence
    private lateinit var prefix: String
    private val preferenceNames = mutableSetOf<String>()

    @Before fun setUp() {
        originalContext = ApplicationProvider.getApplicationContext()
        check(originalContext.packageName.endsWith(".debug"))
        prefix = "draft-test-${UUID.randomUUID()}-"
        directory = File(originalContext.cacheDir, prefix).also { check(it.mkdirs()) }
        context = object : ContextWrapper(originalContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                preferenceNames.add(prefix + name)
                return originalContext.getSharedPreferences(prefix + name, mode)
            }
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        persistence = NotePersistence(context, database, PrivateMarkdownStore(context, database = database))
    }
    @After fun tearDown() {
        database.close()
        directory.deleteRecursively() // Only this test's randomized sandbox directory.
        preferenceNames.forEach { originalContext.deleteSharedPreferences(it) }
    }
    private fun drafts() = QuickNoteDraftStore(context) { persistence }
    private fun sections() = SectionDraftStore(context) { persistence }

    @Test fun legacyLargeDraftRestoresWithoutLoadingItsBodyIntoStartupState() = runBlocking {
        withContext(Dispatchers.IO) {
            val body = "[Link](https://example.org/)\n\n".repeat(5_000)
            context.getSharedPreferences("draft_prefs", Context.MODE_PRIVATE).edit().putString("quick_note", body).commit()
            val loaded = drafts().load()
            assertEquals("", loaded.text)
            assertNotNull(loaded.snapshot)
            assertFalse(context.getSharedPreferences("draft_prefs", Context.MODE_PRIVATE).contains("quick_note"))
            val saved = drafts().promote(listOf("Restored"))
            assertEquals(body, persistence.store.open(saved.id, saved.contentHash!!).bufferedReader().use { it.readText() })
            assertEquals(listOf("Restored"), saved.folders)
            assertNull(drafts().load().snapshot)
        }
    }

    @Test fun emptyQuickDraftAndTitlePersistAcrossStoreRecreation() = runBlocking {
        withContext(Dispatchers.IO) {
            drafts().write("")
            drafts().writeTitle("Keep the title")
            val loaded = drafts().load()
            assertEquals(0L, loaded.snapshot!!.bytes)
            assertEquals("Keep the title", loaded.title)
            val saved = drafts().promote(emptyList())
            assertEquals("Keep the title", saved.title)
            assertEquals(0L, saved.byteLength)
            assertTrue(persistence.store.verify(saved.id, saved.contentHash!!, 0))
        }
    }

    @Test fun sectionJournalSurvivesRecreationAndRejectsConcurrentEdits() = runBlocking {
        withContext(Dispatchers.IO) {
            val saved = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Original", content = "original body"))
            val entry = sections().save(saved.id, saved.contentHash!!, 0, 8, "replacement")
            assertEquals(entry, sections().load(saved.id))
            val newer = persistence.save(saved.copy(content = "newer body", bodyLoaded = true))
            assertTrue(runCatching { sections().apply(saved.id, entry) }.isFailure)
            assertEquals("newer body", persistence.store.open(newer.id, newer.contentHash!!).bufferedReader().use { it.readText() })
            val recovered = sections().recoverAsNote(saved.id, entry)
            assertEquals("replacement", persistence.store.open(recovered.id, recovered.contentHash!!).bufferedReader().use { it.readText() })
            assertNull(sections().load(saved.id))
        }
    }
    @Test fun captureJournalRestoresWithoutSavingOverTheOriginal() = runBlocking {
        val originalText = "first paragraph\n\n" + "more text\n".repeat(1_000)
        val original = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Original", content = originalText))
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = FileEditorSession(context, firstScope, persistence)
        first.open(original)
        val oldEnd = first.state.value.window.end.toInt()
        first.edit("edited 😀 paragraph\n")
        first.flushJournal()
        firstScope.cancel()
        assertEquals(original.contentHash, persistence.load(original.id, false)!!.contentHash)
        val restoredScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val restored = FileEditorSession(context, restoredScope, persistence)
            restored.open(original)
            assertEquals("edited 😀 paragraph\n", restored.state.value.text)
            val saved = restored.save(original)
            val actual = withContext(Dispatchers.IO) { persistence.store.open(saved.id, saved.contentHash!!).bufferedReader().use { it.readText() } }
            assertEquals("edited 😀 paragraph\n" + originalText.substring(oldEnd), actual)
        } finally { restoredScope.cancel() }
    }

    @Test fun largeCapturePasteIsOnlyCommittedWhenSaveIsRequested() = runBlocking {
        val original = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Original", content = "old body"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val editor = FileEditorSession(context, scope, persistence)
            val pasted = "[link](https://example.org/)\n\n".repeat(5_000)
            pasted.byteInputStream().use { editor.stage(original, it) }
            assertEquals(original.contentHash, persistence.load(original.id, false)!!.contentHash)
            val saved = editor.save(original)
            val actual = withContext(Dispatchers.IO) { persistence.store.open(saved.id, saved.contentHash!!).bufferedReader().use { it.readText() } }
            assertEquals(pasted, actual)
        } finally { scope.cancel() }
    }

}
