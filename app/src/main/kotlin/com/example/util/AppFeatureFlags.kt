package com.example.util

import com.example.BuildConfig

/** Development capabilities are unavailable in distributed release builds. */
object AppFeatureFlags {
    val webCompanion: Boolean = BuildConfig.DEBUG && BuildConfig.WEB_COMPANION_ENABLED
}
