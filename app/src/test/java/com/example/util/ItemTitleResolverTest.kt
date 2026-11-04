/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example.util

import com.example.data.model.SavedItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ItemTitleResolverTest {

    private val testFallbacks = TitleFallbacks(
        untitledImage = "Untitled image",
        untitledVideo = "Untitled video",
        untitledAudio = "Untitled audio",
        untitledVoiceMemo = "Untitled voice memo",
        untitledFile = "Untitled file",
        untitledScreenshot = "Untitled screenshot",
        untitledNote = "Untitled note",
        untitledLink = "Untitled link",
        untitledItem = "Untitled item",
        voiceMemoDateFormatter = { ts -> "Voice memo, formatted_$ts" },
        cameraPhotoDateFormatter = { ts -> "Photo, formatted_$ts" }
    )

    @Test
    fun userTitleWinsWhenValid() {
        val title = ItemTitleResolver.resolveItemTitle(
            userTitle = "My Summer Vacation",
            displayName = "IMG_9999.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("My Summer Vacation", title)
    }

    @Test
    fun userTitleWinsWithTrimming() {
        val title = ItemTitleResolver.resolveItemTitle(
            userTitle = "   Trip to Mountains   ",
            displayName = "photo.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("Trip to Mountains", title)
    }

    @Test
    fun blankAndWhitespaceOnlyUserTitleFallsThroughToDisplayName() {
        val titleBlank = ItemTitleResolver.resolveItemTitle(
            userTitle = "",
            displayName = "sunset.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("sunset", titleBlank)

        val titleWhitespace = ItemTitleResolver.resolveItemTitle(
            userTitle = "   \n\t  ",
            displayName = "sunset.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("sunset", titleWhitespace)
    }

    @Test
    fun userTitleThatLooksLikeInternalPathIsRejected() {
        val title = ItemTitleResolver.resolveItemTitle(
            userTitle = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            displayName = "real_photo.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("real_photo", title)
    }

    @Test
    fun displayNameWithAndWithoutExtension() {
        val withExt = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "document.pdf",
            type = SavedItemType.TEXT,
            fallbacks = testFallbacks
        )
        assertEquals("document", withExt)

        val withoutExt = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "JustAName",
            type = SavedItemType.TEXT,
            fallbacks = testFallbacks
        )
        assertEquals("JustAName", withoutExt)
    }

    @Test
    fun namesWithMultipleDotsRemovesOnlyLastExtension() {
        val title = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "my.photo.final.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("my.photo.final", title)
    }

    @Test
    fun leadingDotWithoutExtensionPreserved() {
        val title = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = ".nomedia",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals(".nomedia", title)

        val leadingDotWithExt = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = ".hidden.png",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals(".hidden", leadingDotWithExt)
    }

    @Test
    fun unicodeAndEmojiNamesPreserved() {
        val title = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "Urdu اردو اور جاپانی 日本語 📸.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("Urdu اردو اور جاپانی 日本語 📸", title)
    }

    @Test
    fun veryLongNameTruncationWithoutSplittingSurrogates() {
        // Emoji 📷 is surrogate pair (2 chars: \uD83D\uDCF7)
        val repeated = "A".repeat(119) + "📷" // 119 + 2 = 121 chars
        val truncated = ItemTitleResolver.truncateSafely(repeated, 120)
        // Since index 119 is high surrogate of 📷, truncation must end at 119 chars to avoid splitting surrogate
        assertEquals(119, truncated.length)
        assertEquals("A".repeat(119), truncated)

        // Normal long string truncation
        val normalLong = "B".repeat(150)
        val normalTruncated = ItemTitleResolver.truncateSafely(normalLong, 120)
        assertEquals(120, normalTruncated.length)
        assertEquals("B".repeat(120), normalTruncated)
    }

    @Test
    fun pathSeparatorsAndControlCharactersStripped() {
        val raw = "folder/subfolder\\my\u0000photo\u001F.png"
        val title = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = raw,
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("foldersubfoldermyphoto", title)
    }

    @Test
    fun nullOrBlankDisplayNameFallsThroughToFallback() {
        val imgTitle = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = null,
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("Untitled image", imgTitle)

        val vidTitle = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "   ",
            type = SavedItemType.VIDEO,
            fallbacks = testFallbacks
        )
        assertEquals("Untitled video", vidTitle)
    }

    @Test
    fun rejectCachePathFileUriContentUriAndMediaUuid() {
        // Absolute cache path
        assertTrue(
            ItemTitleResolver.isInternalPathOrGeneratedName("/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg")
        )
        // file URI
        assertTrue(
            ItemTitleResolver.isInternalPathOrGeneratedName("file:///data/user/0/com.example/cache/sample.jpg")
        )
        // content URI
        assertTrue(
            ItemTitleResolver.isInternalPathOrGeneratedName("content://com.android.providers.media.documents/document/image%3A123")
        )
        // Cobalt generated media_<uuid>
        assertTrue(
            ItemTitleResolver.isInternalPathOrGeneratedName("media_c3b71a11-6655-4abc-8def-0123456789ab.jpg")
        )
        assertTrue(
            ItemTitleResolver.isInternalPathOrGeneratedName("media_c3b71a11-6655-4abc-8def-0123456789ab")
        )
        assertTrue(
            ItemTitleResolver.isInternalPathOrGeneratedName("shared_img_c3b71a11-6655-4abc-8def-0123456789ab.jpg")
        )
        assertTrue(
            ItemTitleResolver.isInternalPathOrGeneratedName("shared_vid_c3b71a11-6655-4abc-8def-0123456789ab.mp4")
        )

        // When passed as candidates to resolver, all must fall back to localized fallback
        val resolvedCachePath = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("Untitled image", resolvedCachePath)

        val resolvedFileUri = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "file:///storage/emulated/0/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("Untitled image", resolvedFileUri)

        val resolvedContentUri = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "content://media/external/images/media/1234",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("Untitled image", resolvedContentUri)

        val resolvedMediaUuid = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("Untitled image", resolvedMediaUuid)
    }

    @Test
    fun acceptableOriginalUserFileNames() {
        // User original camera files: IMG_..., VID_..., numeric, bare UUID
        val imgTemp = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "IMG_20261104_123456.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("IMG_20261104_123456", imgTemp)

        val vidTemp = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "VID_20261104_091500.mp4",
            type = SavedItemType.VIDEO,
            fallbacks = testFallbacks
        )
        assertEquals("VID_20261104_091500", vidTemp)

        val numericName = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "20261104123456.png",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("20261104123456", numericName)

        val bareUuid = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = "c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("c3b71a11-6655-4abc-8def-0123456789ab", bareUuid)
    }

    @Test
    fun fallbackPerType() {
        assertEquals("Untitled image", ItemTitleResolver.resolveItemTitle(null, null, SavedItemType.IMAGE, fallbacks = testFallbacks))
        assertEquals("Untitled video", ItemTitleResolver.resolveItemTitle(null, null, SavedItemType.VIDEO, fallbacks = testFallbacks))
        assertEquals("Untitled audio", ItemTitleResolver.resolveItemTitle(null, null, SavedItemType.AUDIO, fallbacks = testFallbacks))
        assertEquals("Untitled note", ItemTitleResolver.resolveItemTitle(null, null, SavedItemType.TEXT, fallbacks = testFallbacks))
        assertEquals("Untitled note", ItemTitleResolver.resolveItemTitle(null, null, SavedItemType.CODE, fallbacks = testFallbacks))
        assertEquals("Untitled link", ItemTitleResolver.resolveItemTitle(null, null, SavedItemType.LINK, fallbacks = testFallbacks))
        assertEquals("Untitled item", ItemTitleResolver.resolveItemTitle(null, null, SavedItemType.MEDIA, fallbacks = testFallbacks))
        assertEquals("Untitled screenshot", ItemTitleResolver.resolveItemTitle(null, null, SavedItemType.IMAGE, isScreenshot = true, fallbacks = testFallbacks))
    }

    @Test
    fun voiceMemoAndCameraReadableFormattedName() {
        val fixedTimestamp = 1793781120000L // 4 Nov 2026
        val voiceMemoTitle = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = null,
            type = SavedItemType.AUDIO,
            isVoiceMemo = true,
            timestamp = fixedTimestamp,
            fallbacks = testFallbacks
        )
        assertEquals("Voice memo, formatted_$fixedTimestamp", voiceMemoTitle)

        val cameraPhotoTitle = ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = null,
            type = SavedItemType.IMAGE,
            isCameraCapture = true,
            timestamp = fixedTimestamp,
            fallbacks = testFallbacks
        )
        assertEquals("Photo, formatted_$fixedTimestamp", cameraPhotoTitle)
    }

    @Test
    fun displayTimeGuardSafeguardsStoredBadTitles() {
        val safeTitle = ItemTitleResolver.getDisplaySafeTitle(
            rawTitle = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("Untitled image", safeTitle)

        val safeDescription = ItemTitleResolver.getDisplaySafeDescription(
            rawDescription = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            type = SavedItemType.IMAGE
        )
        assertEquals("", safeDescription)

        val validUserTitle = ItemTitleResolver.getDisplaySafeTitle(
            rawTitle = "My Trip to Kyoto",
            type = SavedItemType.IMAGE,
            fallbacks = testFallbacks
        )
        assertEquals("My Trip to Kyoto", validUserTitle)
    }
}
