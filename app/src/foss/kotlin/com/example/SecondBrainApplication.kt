/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class SecondBrainApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // FOSS build: no Firebase initialization
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
