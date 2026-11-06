package com.example.notes

import android.content.Context
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Versioned, resumable conversion. Prompt acknowledgement is independent of completion. */
object NoteMigrationManager {
    const val BODY_FORMAT_VERSION = 1
    enum class Phase { READY, RUNNING, COMPLETE, ATTENTION, INTERRUPTED }
    data class Status(
        val phase: Phase = Phase.READY, val total: Int = 0, val converted: Int = 0,
        val verified: Int = 0, val failed: Int = 0, val bytes: Long = 0,
        val errors: List<String> = emptyList(), val lastRun: Long = 0
    )
    private val mutableStatus = MutableStateFlow(Status())
    val status = mutableStatus.asStateFlow()
    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences("note_migration", Context.MODE_PRIVATE)

    fun shouldPauseBackground(context: Context): Boolean {
        val prefs = preferences(context)
        return prefs.getBoolean("migration_required", false) ||
            (context.getDatabasePath("cobalt_db").exists() && prefs.getInt("completed_format", 0) < BODY_FORMAT_VERSION)
    }

    suspend fun needsStartupMigration(context: Context): Boolean = evaluateStartupMigration(context) {
        context.getDatabasePath("cobalt_db").exists() &&
            AppDatabase.getDatabase(context).savedItemDao().hasNotesNeedingBodyMigration()
    }

    /** Room may already have created an empty database before Activity startup. File existence is not migration evidence. */
    internal suspend fun evaluateStartupMigration(context: Context, pendingBodies: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val prefs = preferences(context)
            val drafts = context.getSharedPreferences("draft_prefs", Context.MODE_PRIVATE)
            val legacyDraft = drafts.getString("quick_note", "").orEmpty()
            val phase = runCatching { Phase.valueOf(prefs.getString("phase", Phase.READY.name)!!) }.getOrDefault(Phase.READY)
            val attention = phase in setOf(Phase.RUNNING, Phase.INTERRUPTED, Phase.ATTENTION) || prefs.getInt("failed", 0) > 0
            val needed = legacyDraft.isNotEmpty() || pendingBodies() || attention
            if (drafts.contains("quick_note") && legacyDraft.isEmpty()) {
                check(drafts.edit().remove("quick_note").commit()) { "Cannot acknowledge empty legacy draft" }
            }
            val update = prefs.edit().putBoolean("migration_required", needed)
            if (!needed) update.putInt("completed_format", BODY_FORMAT_VERSION).putString("phase", Phase.COMPLETE.name)
            check(update.commit()) { "Cannot persist migration state" }
            refreshStatus(context)
            needed
        }
    }

    suspend fun acknowledgePrompt(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = preferences(context)
        if (prefs.getInt("prompt_shown", 0) >= BODY_FORMAT_VERSION) false
        else prefs.edit().putInt("prompt_shown", BODY_FORMAT_VERSION).commit()
    }

    suspend fun refreshStatus(context: Context) = withContext(Dispatchers.IO) {
        if (mutableStatus.value.phase == Phase.RUNNING) return@withContext
        val prefs = preferences(context)
        val phase = runCatching { Phase.valueOf(prefs.getString("phase", "READY")!!) }.getOrDefault(Phase.READY)
        val errors = runCatching {
            val array = JSONArray(prefs.getString("errors", "[]"))
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        mutableStatus.value = Status(
            if (phase == Phase.RUNNING) Phase.INTERRUPTED else phase,
            prefs.getInt("total", 0), prefs.getInt("converted", 0), prefs.getInt("verified", 0),
            prefs.getInt("failed", 0), errors = errors, lastRun = prefs.getLong("last_run", 0)
        )
    }

    private fun persist(context: Context, status: Status) {
        val editor = preferences(context).edit()
            .putString("phase", status.phase.name).putInt("total", status.total)
            .putInt("converted", status.converted).putInt("verified", status.verified)
            .putInt("failed", status.failed).putLong("last_run", status.lastRun)
            .putString("errors", JSONArray(status.errors).toString())
        if (status.phase == Phase.COMPLETE) editor.putInt("completed_format", BODY_FORMAT_VERSION).putBoolean("migration_required", false)
        check(editor.commit()) { "Cannot persist migration results" }
    }

    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            var result = Status(Phase.RUNNING, lastRun = System.currentTimeMillis())
            mutableStatus.value = result
            try {
                persist(context, result)
                val database = AppDatabase.getDatabase(context)
                val dao = database.savedItemDao()
                val persistence = NotePersistence(context)
                QuickNoteDraftStore(context).migrateLegacyLocked()
                // Opening Room runs the additive v8 -> v9 schema transaction first.
                val notes = dao.allNoteBodyMetadata()
                result = result.copy(total = notes.size)
                mutableStatus.value = result
                for (note in notes) {
                    currentCoroutineContext().ensureActive()
                    try {
                        persistence.migrateOneLocked(note.id) { count ->
                            mutableStatus.value = result.copy(bytes = count)
                        }
                        result = result.copy(
                            converted = result.converted + if (note.bodyLocation != "FILE") 1 else 0,
                            verified = result.verified + 1, bytes = 0
                        )
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        if (note.bodyLocation == "FILE") dao.markBodyMissing(note.id, true)
                        result = result.copy(failed = result.failed + 1,
                            errors = (result.errors + "Note ${note.id.take(16)}: verification/conversion failed; original retained").take(20))
                    }
                    mutableStatus.value = result
                    persist(context, result) // Each committed note can be skipped safely on a retry.
                }
                result = result.copy(phase = if (result.failed == 0) Phase.COMPLETE else Phase.ATTENTION)
                persist(context, result)
                mutableStatus.value = result
            } catch (cancel: CancellationException) {
                result = result.copy(phase = Phase.INTERRUPTED)
                runCatching { persist(context, result) }
                mutableStatus.value = result
                throw cancel
            } catch (_: Exception) {
                result = result.copy(phase = Phase.ATTENTION,
                    errors = (result.errors + "Migration could not complete. Existing note data has not been discarded.").take(20))
                runCatching { persist(context, result) }
                mutableStatus.value = result
            }
        }
    }
}
