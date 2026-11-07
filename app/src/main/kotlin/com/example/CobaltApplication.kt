/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CobaltApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        // Install local reporting first so the startup guard chains to it.
        com.example.util.LocalCrashReporter.init(this)
        com.example.notes.StartupCrashGuard.install(this)
        // Defer expiry if WorkManager is temporarily unavailable; opening Trash also retries.
        runCatching { com.example.notes.TrashCleanupWorker.schedule(this) }

        // Trigger one-time legacy path title cleanup off the main thread (Issue #21)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // Wake durable link jobs after a crash between the item commit and WorkManager enqueue.
            runCatching { com.example.links.LinkPreviewWorker.schedule(this@CobaltApplication) }
            if (!com.example.notes.StartupCrashGuard.shouldPause(this@CobaltApplication) &&
                !com.example.notes.NoteMigrationManager.shouldPauseBackground(this@CobaltApplication)) runCatching {
                val auth = com.example.data.remote.trakt.TraktAuthManager.getInstance(this@CobaltApplication)
                if (auth.hasValidToken() && com.example.data.local.AppDatabase.getDatabase(this@CobaltApplication)
                    .traktOutboxDao().pending(auth.syncSessionId(), 1).isNotEmpty())
                    com.example.util.TraktSyncWorker.enqueueOneTimeSync(this@CobaltApplication, uploadOnly = true)
                if (auth.hasValidToken()) com.example.util.TraktSyncWorker.scheduleDeferredRetry(this@CobaltApplication)
            }
            com.example.data.cleanup.LegacyPathTitleCleanup.runCleanup(this@CobaltApplication)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
