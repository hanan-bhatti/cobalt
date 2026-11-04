/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CobaltRepositoryChokePointTest {

    @Test
    fun saveItemSanitizesInternalPathTitleForImages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = CobaltRepository(context)
        val badItem = SavedItem(
            type = SavedItemType.IMAGE,
            title = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            content = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg"
        )
        val saved = repo.saveItem(badItem)
        assertEquals("Untitled image", saved.title)
        assertFalse(saved.title.contains("/"))
    }

    @Test
    fun saveItemSanitizesInternalPathTitleForVideos() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = CobaltRepository(context)
        val badItem = SavedItem(
            type = SavedItemType.VIDEO,
            title = "media_c3b71a11-6655-4abc-8def-0123456789ab.mp4",
            content = "media_c3b71a11-6655-4abc-8def-0123456789ab.mp4"
        )
        val saved = repo.saveItem(badItem)
        assertEquals("Untitled video", saved.title)
    }

    @Test
    fun saveItemRetainsValidUserTitles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = CobaltRepository(context)
        val goodItem = SavedItem(
            type = SavedItemType.IMAGE,
            title = "Sunset at the Beach",
            content = "dummy_content"
        )
        val saved = repo.saveItem(goodItem)
        assertEquals("Sunset at the Beach", saved.title)
    }
}
