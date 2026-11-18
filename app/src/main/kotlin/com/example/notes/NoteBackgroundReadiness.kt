package com.example.notes

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Eager readers must respect the same recovery/migration boundary as background workers. */
fun noteDatabaseReadiness(context: Context): Flow<Boolean> = callbackFlow {
    val app = context.applicationContext
    val migration = app.getSharedPreferences("note_migration", Context.MODE_PRIVATE)
    val recovery = app.getSharedPreferences("startup_recovery", Context.MODE_PRIVATE)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "migration_required" || key == "completed_format" || key == "startup_crashes" || key == null) trySend(Unit)
    }
    migration.registerOnSharedPreferenceChangeListener(listener)
    recovery.registerOnSharedPreferenceChangeListener(listener)
    trySend(Unit)
    awaitClose {
        migration.unregisterOnSharedPreferenceChangeListener(listener)
        recovery.unregisterOnSharedPreferenceChangeListener(listener)
    }
}.conflate().map {
    !StartupCrashGuard.shouldPause(context) && !NoteMigrationManager.shouldPauseBackground(context)
}.distinctUntilChanged().flowOn(Dispatchers.IO)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun <T> backgroundNoteDatabaseReads(context: Context, source: () -> Flow<T>): Flow<T> =
    noteDatabaseReadiness(context).flatMapLatest { allowed -> if (allowed) source() else emptyFlow() }.flowOn(Dispatchers.IO)
