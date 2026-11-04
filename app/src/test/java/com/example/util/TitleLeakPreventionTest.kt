/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example.util

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TitleLeakPreventionTest {

    private val forbiddenPatterns = listOf(
        Regex("^/"),
        Regex("^file:", RegexOption.IGNORE_CASE),
        Regex("^content:", RegexOption.IGNORE_CASE),
        Regex("/cache/", RegexOption.IGNORE_CASE),
        Regex("/files/", RegexOption.IGNORE_CASE),
        Regex("/data/user/", RegexOption.IGNORE_CASE),
        Regex("/data/data/", RegexOption.IGNORE_CASE),
        Regex("^(?:media|shared_img|shared_vid|audio|thumb|share|note)_[0-9a-fA-F]{8,}")
    )

    data class TableScenario(
        val entryPoint: String,
        val type: SavedItemType,
        val userTitle: String?,
        val candidateName: String?,
        val rawPathOrContent: String
    )

    private val scenarios = listOf(
        // Image scenarios
        TableScenario(
            entryPoint = "Gallery Picker (empty user title, raw cache path)",
            type = SavedItemType.IMAGE,
            userTitle = "",
            candidateName = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            rawPathOrContent = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg"
        ),
        TableScenario(
            entryPoint = "Gallery Picker (valid display name from OpenableColumns)",
            type = SavedItemType.IMAGE,
            userTitle = null,
            candidateName = "IMG_20261104_143022.jpg",
            rawPathOrContent = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg"
        ),
        TableScenario(
            entryPoint = "Camera Capture (null candidate, timestamp fallback)",
            type = SavedItemType.IMAGE,
            userTitle = null,
            candidateName = null,
            rawPathOrContent = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg"
        ),
        TableScenario(
            entryPoint = "Share Single Image (empty title, media_uuid name)",
            type = SavedItemType.IMAGE,
            userTitle = "   ",
            candidateName = "media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            rawPathOrContent = "/data/user/0/com.example/cache/shared_img_c3b71a11.jpg"
        ),

        // Video scenarios
        TableScenario(
            entryPoint = "Video Picker (empty title, raw cache path)",
            type = SavedItemType.VIDEO,
            userTitle = null,
            candidateName = "/data/user/0/com.example/cache/media_eef01234-abcd-1234-5678-0123456789ab.mp4",
            rawPathOrContent = "/data/user/0/com.example/cache/media_eef01234-abcd-1234-5678-0123456789ab.mp4"
        ),
        TableScenario(
            entryPoint = "Video Picker (original display name)",
            type = SavedItemType.VIDEO,
            userTitle = null,
            candidateName = "VID_20261104_120000.mp4",
            rawPathOrContent = "/data/user/0/com.example/cache/media_eef01234-abcd-1234-5678-0123456789ab.mp4"
        ),
        TableScenario(
            entryPoint = "Share Video (content URI as candidate)",
            type = SavedItemType.VIDEO,
            userTitle = null,
            candidateName = "content://media/external/video/media/12345",
            rawPathOrContent = "/data/user/0/com.example/cache/shared_vid_12345.mp4"
        ),

        // Audio scenarios
        TableScenario(
            entryPoint = "Voice Memo (timestamp format)",
            type = SavedItemType.AUDIO,
            userTitle = null,
            candidateName = null,
            rawPathOrContent = "/data/user/0/com.example/cache/audio_c3b71a11.mp4"
        ),
        TableScenario(
            entryPoint = "Audio Picker (file URI as candidate)",
            type = SavedItemType.AUDIO,
            userTitle = null,
            candidateName = "file:///storage/emulated/0/Music/audio_c3b71a11.mp3",
            rawPathOrContent = "/data/user/0/com.example/cache/audio_c3b71a11.mp3"
        ),
        TableScenario(
            entryPoint = "Audio Picker (original song name)",
            type = SavedItemType.AUDIO,
            userTitle = null,
            candidateName = "Podcast_Episode_42.mp3",
            rawPathOrContent = "/data/user/0/com.example/cache/audio_c3b71a11.mp3"
        ),

        // Note & Code scenarios
        TableScenario(
            entryPoint = "Quick Capture Note (first line heading)",
            type = SavedItemType.TEXT,
            userTitle = null,
            candidateName = null,
            rawPathOrContent = "# Project Roadmap 2026\nQuarterly goals"
        ),
        TableScenario(
            entryPoint = "Note with empty content",
            type = SavedItemType.TEXT,
            userTitle = null,
            candidateName = null,
            rawPathOrContent = ""
        ),
        TableScenario(
            entryPoint = "Code Snippet",
            type = SavedItemType.CODE,
            userTitle = null,
            candidateName = null,
            rawPathOrContent = "fun calculateTotal() {\n    return 42\n}"
        ),

        // Link scenarios
        TableScenario(
            entryPoint = "Web Link with URL candidate",
            type = SavedItemType.LINK,
            userTitle = null,
            candidateName = "/data/user/0/com.example/cache/cached_web.html",
            rawPathOrContent = "https://example.com/article/1"
        ),
        TableScenario(
            entryPoint = "Web Link with clean title",
            type = SavedItemType.LINK,
            userTitle = "Awesome Tech News",
            candidateName = null,
            rawPathOrContent = "https://example.com/article/2"
        ),

        // Media (Movie / Anime) scenario
        TableScenario(
            entryPoint = "Media Search result",
            type = SavedItemType.MEDIA,
            userTitle = null,
            candidateName = "/cache/media_c3b71a11.jpg",
            rawPathOrContent = "Overview of the movie"
        )
    )

    @Test
    fun tableDrivenLeakPreventionAcrossAllItemTypesAndEntryPoints() {
        val fallbacks = TitleFallbacks(
            untitledImage = "Untitled image",
            untitledVideo = "Untitled video",
            untitledAudio = "Untitled audio",
            untitledVoiceMemo = "Untitled voice memo",
            untitledFile = "Untitled file",
            untitledScreenshot = "Untitled screenshot",
            untitledNote = "Untitled note",
            untitledLink = "Untitled link",
            untitledItem = "Untitled item",
            voiceMemoDateFormatter = { "Voice memo, Nov 4" },
            cameraPhotoDateFormatter = { "Photo, Nov 4" }
        )

        for (scenario in scenarios) {
            val resolvedTitle = ItemTitleResolver.resolveItemTitle(
                userTitle = scenario.userTitle,
                displayName = scenario.candidateName,
                type = scenario.type,
                timestamp = 1730700000000L,
                fallbacks = fallbacks
            )

            // 1. Must never be blank
            assertTrue(
                "Scenario [${scenario.entryPoint}] resolved to a blank title",
                resolvedTitle.isNotBlank()
            )

            // 2. Must not contain any forbidden path or URI patterns
            for (pattern in forbiddenPatterns) {
                assertFalse(
                    "Scenario [${scenario.entryPoint}] leaked pattern '$pattern' in title: '$resolvedTitle'",
                    pattern.containsMatchIn(resolvedTitle)
                )
            }

            // 3. Display-time guard check
            val guardedTitle = ItemTitleResolver.getDisplaySafeTitle(
                rawTitle = scenario.rawPathOrContent,
                type = scenario.type,
                fallbacks = fallbacks
            )
            for (pattern in forbiddenPatterns) {
                assertFalse(
                    "Display-time guard for [${scenario.entryPoint}] leaked pattern '$pattern': '$guardedTitle'",
                    pattern.containsMatchIn(guardedTitle)
                )
            }

            // 4. Description guard check
            val guardedDesc = ItemTitleResolver.getDisplaySafeDescription(
                rawDescription = scenario.rawPathOrContent,
                type = scenario.type
            )
            for (pattern in forbiddenPatterns) {
                assertFalse(
                    "Description guard for [${scenario.entryPoint}] leaked pattern '$pattern': '$guardedDesc'",
                    pattern.containsMatchIn(guardedDesc)
                )
            }
        }
    }

    @Test
    fun scansSourceForRiskyTitleAssignments() {
        val srcDir = File("src/main/kotlin")
        if (!srcDir.exists()) {
            // Test might run from root or app module directory
            val altDir = File("app/src/main/kotlin")
            if (!altDir.exists()) return
        }
        val targetDir = if (srcDir.exists()) srcDir else File("app/src/main/kotlin")

        val riskyRegex = Regex("""(?:title\s*=\s*(?:file\.|uri\.|.*absolutePath|.*canonicalPath))|(?:title\s*=\s*.*\.path)""", RegexOption.IGNORE_CASE)

        val violations = mutableListOf<String>()

        targetDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                        if (riskyRegex.containsMatchIn(trimmed)) {
                            violations.add("${file.name}:${index + 1}: $trimmed")
                        }
                    }
                }
            }

        // Output findings for observability
        if (violations.isNotEmpty()) {
            println("Source code scanner observed the following potential risky pattern candidates:")
            violations.forEach { println("  - $it") }
        }

        // Assert that critical capture or UI files do not contain un-sanitized risky assignments
        val criticalViolations = violations.filter { v ->
            v.startsWith("CobaltViewModel.kt") ||
            v.startsWith("QuickShareActivity.kt") ||
            v.startsWith("CobaltRepository.kt") ||
            v.startsWith("ItemTitleResolver.kt")
        }

        assertTrue(
            "Found risky un-sanitized title assignments in critical files: $criticalViolations",
            criticalViolations.isEmpty()
        )
    }
}
