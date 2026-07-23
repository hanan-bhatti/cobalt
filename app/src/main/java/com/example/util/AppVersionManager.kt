package com.example.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Supported release tags/channels with curated, high-contrast M3 colors.
 */
enum class AppVersionTag(
    val label: String,
    val description: String
) {
    ALPHA("ALPHA", "Experimental feature build"),
    BETA("BETA", "Preview build for testing new features"),
    NIGHTLY("NIGHTLY", "Bleeding-edge daily development build"),
    RC("RC", "Release candidate build"),
    STABLE("STABLE", "Official stable production release"),
    DEV("DEV", "Local developer build");

    @Composable
    fun containerColor(): Color {
        return when (this) {
            ALPHA -> Color(0xFFFCE4EC) // Light Magenta/Pink
            BETA -> Color(0xFFFFF3E0) // Warm Amber/Orange
            NIGHTLY -> Color(0xFFEDE7F6) // Deep Lavender/Indigo
            RC -> Color(0xFFE0F7FA) // Cyan/Teal
            STABLE -> Color(0xFFE8F5E9) // Mint Green
            DEV -> Color(0xFFFFF8E1) // Soft Gold
        }
    }

    @Composable
    fun contentColor(): Color {
        return when (this) {
            ALPHA -> Color(0xFFC2185B)
            BETA -> Color(0xFFE65100)
            NIGHTLY -> Color(0xFF512DA8)
            RC -> Color(0xFF00796B)
            STABLE -> Color(0xFF2E7D32)
            DEV -> Color(0xFFF57F17)
        }
    }
}

/**
 * Data model for an app release note entry.
 */
data class ReleaseNote(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val tag: AppVersionTag,
    val isCurrent: Boolean = false,
    val isLatest: Boolean = false,
    val highlights: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val bugFixes: List<String> = emptyList()
)

/**
 * Central manager for App Version information, release history, and update checks.
 */
object AppVersionManager {

    /**
     * Parses the current version name from BuildConfig to extract the dynamic release tag.
     */
    fun parseVersionTag(versionName: String = com.example.BuildConfig.VERSION_NAME): AppVersionTag {
        val lower = versionName.lowercase()
        return when {
            lower.contains("alpha") -> AppVersionTag.ALPHA
            lower.contains("beta") -> AppVersionTag.BETA
            lower.contains("nightly") -> AppVersionTag.NIGHTLY
            lower.contains("rc") -> AppVersionTag.RC
            lower.contains("dev") -> AppVersionTag.DEV
            else -> AppVersionTag.BETA // Default if unrecognized pre-release
        }
    }

    val currentTag: AppVersionTag
        get() = parseVersionTag(com.example.BuildConfig.VERSION_NAME)

    val currentVersionName: String
        get() = com.example.BuildConfig.VERSION_NAME

    val currentVersionCode: Int
        get() = com.example.BuildConfig.VERSION_CODE

    /**
     * Complete history of releases for Second Brain.
     */
    val releaseHistory: List<ReleaseNote> = listOf(
        ReleaseNote(
            versionName = "0.9.4-beta02",
            versionCode = 7,
            releaseDate = "July 24, 2026",
            tag = AppVersionTag.BETA,
            isCurrent = false,
            isLatest = true,
            highlights = listOf(
                "Dynamic Versioning & Expressive Release Notes",
                "App-wide RTL support for Urdu, Arabic, & Hebrew text",
                "Unified Material 3 Expressive Dialogs & Wavy Progress Indicators"
            ),
            features = listOf(
                "New 'App Updates & Release Notes' hub in Settings",
                "Dynamic build badge colors (Beta, Nightly, Alpha, Stable)",
                "Interactive update checker with real-time feedback"
            ),
            improvements = listOf(
                "Full RTL alignment in Markdown renderers and Rich Text editor",
                "Seamless model list refresh upon API key update",
                "Unified dialog corner radii to 28.dp surface container high"
            ),
            bugFixes = listOf(
                "Fixed 404 error on initial Gemini model request",
                "Fixed text direction mismatch for right-to-left scripts"
            )
        ),
        ReleaseNote(
            versionName = "0.9.3-beta01",
            versionCode = 6,
            releaseDate = "July 23, 2026",
            tag = AppVersionTag.BETA,
            isCurrent = true,
            isLatest = false,
            highlights = listOf(
                "Voice Memo AI Transcription",
                "Material 3 Expressive UI Polish",
                "Multi-folder management for saved memories"
            ),
            features = listOf(
                "Audio recording to AI note conversion with automatic transcriptions",
                "Global expanding action button with animated speed dial",
                "Advanced search filtering with tags and cloud media indicators"
            ),
            improvements = listOf(
                "Haptic feedback on item swipe gestures",
                "Optimized database queries for fast search performance"
            ),
            bugFixes = listOf(
                "Fixed cloud sync state initialization bug",
                "Corrected audio playback progress bar accuracy"
            )
        ),
        ReleaseNote(
            versionName = "0.9.0-alpha01",
            versionCode = 5,
            releaseDate = "July 10, 2026",
            tag = AppVersionTag.ALPHA,
            isCurrent = false,
            isLatest = false,
            highlights = listOf("Initial Alpha Release of Second Brain"),
            features = listOf(
                "Local-first memory architecture",
                "Gemini AI integration for note summarization",
                "Folder organization and tagging system"
            )
        )
    )

    fun getLatestRelease(): ReleaseNote = releaseHistory.firstOrNull { it.isLatest } ?: releaseHistory.first()

    fun isUpdateAvailable(): Boolean {
        val latest = getLatestRelease()
        return latest.versionCode > currentVersionCode
    }
}
