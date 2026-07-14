package com.example.utils

import android.content.Intent
import android.media.projection.MediaProjection

object MediaProjectionCache {
    var resultCode: Int = 0
    var resultData: Intent? = null
    var activeMediaProjection: MediaProjection? = null

    fun clear() {
        try {
            activeMediaProjection?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        activeMediaProjection = null
        resultData = null
        resultCode = 0
    }
}
