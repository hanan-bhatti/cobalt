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
 * Synchronized directly with root CHANGELOG.md.
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
            else -> AppVersionTag.STABLE // Default to STABLE for official production releases
        }
    }

    val currentTag: AppVersionTag
        get() = parseVersionTag(com.example.BuildConfig.VERSION_NAME)

    val currentVersionName: String
        get() = com.example.BuildConfig.VERSION_NAME

    val currentVersionCode: Int
        get() = com.example.BuildConfig.VERSION_CODE

    /**
     * Authentic release history of Second Brain synchronized with CHANGELOG.md.
     */
    val releaseHistory: List<ReleaseNote> = listOf(
        ReleaseNote(
            versionName = "1.0.0-nightly01",
            versionCode = 8,
            releaseDate = "July 23, 2026",
            tag = AppVersionTag.NIGHTLY,
            isCurrent = true,
            isLatest = true,
            highlights = listOf(
                "Bleeding-Edge Nightly Build (1.0.0-nightly01)",
                "Android 12+ Tuned Haptic Engine Manager (Disabled by default)",
                "App-wide User-Friendly Error Banners & Network Timeout Protection",
                "Search Panel Movie Icon & Gamified In-App Experience Survey Sheet"
            ),
            features = listOf(
                "Bleeding-edge Nightly testing channel build",
                "Tuned Haptic Feedback Engine for presses, long presses, and success actions",
                "Tactile feedback toggle in Profile ➔ Settings ➔ Display & Interface",
                "AppFeedbackBanner with non-infinite network call timeouts",
                "Gamified 2-step In-App Experience Survey Sheet with Firestore sync",
                "Profile sections reorganization & deep Account Deletion flow"
            ),
            improvements = listOf(
                "Movie search results in Search panel now correctly render movie icons & poster art",
                "Network calls bounded to maximum 15s timeouts to prevent hanging",
                "Full RTL text direction in Markdown renderers and Rich Text editor"
            ),
            bugFixes = listOf(
                "Fixed movie item type icon in SearchScreen panel",
                "Fixed Send icon and Clipboard deprecation warnings with modern APIs"
            )
        ),
        ReleaseNote(
            versionName = "1.0.0-rc01",
            versionCode = 7,
            releaseDate = "July 23, 2026",
            tag = AppVersionTag.RC,
            isCurrent = false,
            isLatest = false,
            highlights = listOf(
                "First Official Release Candidate (1.0.0-rc01)",
                "App-wide RTL support for Urdu, Arabic, & Hebrew text",
                "Dynamic Version Channel Badges & Expressive Release Notes Hub",
                "Unified Material 3 Expressive Dialogs & Wavy Progress Indicators"
            ),
            features = listOf(
                "Release Candidate build status with dynamic channel colors",
                "New 'App Updates & Release Notes' hub in Settings",
                "Interactive update checker with real-time feedback",
                "Voice Memo AI Transcription and recording waveform"
            ),
            improvements = listOf(
                "Full RTL text direction in Markdown renderers and Rich Text editor",
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
            releaseDate = "July 22, 2026",
            tag = AppVersionTag.BETA,
            isCurrent = false,
            isLatest = false,
            highlights = listOf(
                "Global Dynamic Light/Dark Theme Color Adaptation",
                "Header Top Inset Spacing Cleanups"
            ),
            features = listOf(
                "Implemented HSL-based color transformation (Color.toThemeColor(isDark)) across all folders, categories, widgets, OCR overlay, and settings"
            ),
            improvements = listOf(
                "Consolidated top bar header spacing immediately below system status bar across all main screens"
            )
        ),
        ReleaseNote(
            versionName = "0.9.2-beta01",
            versionCode = 5,
            releaseDate = "July 22, 2026",
            tag = AppVersionTag.BETA,
            isCurrent = false,
            isLatest = false,
            highlights = listOf(
                "Media Hub Screen & TMDb Movie/TV/Anime Enrichment",
                "Auto-Complete Media Search & OCR Integration"
            ),
            features = listOf(
                "Built dedicated Media Hub featuring watch status filter chips, category tabs, poster cards, and pull-to-refresh",
                "Interactive bottom sheet search for real-time movie, TV show, and anime queries",
                "Automated fetching of TMDb streaming watch providers, genres, release years, vote ratings, and trailers",
                "East Asian anime auto-detection algorithms and genre filter chips",
                "OCR Overlay direct media searching and single-tap saving"
            ),
            improvements = listOf(
                "Embedded section headers and filter chips directly into home screen staggered grid",
                "Relocated TMDb API key configuration to a dedicated subscreen inside Settings",
                "Updated capture and recent item widgets to support media category items"
            ),
            bugFixes = listOf(
                "Separated OCR tab save button logic to allow independent link and note saving",
                "Enabled instant DB saves for extracted links prior to background metadata fetching",
                "Resolved Firestore folder document path slashes and early exit bugs in syncUnsyncedItems"
            )
        ),
        ReleaseNote(
            versionName = "0.9.1-beta02",
            versionCode = 4,
            releaseDate = "July 21, 2026",
            tag = AppVersionTag.BETA,
            isCurrent = false,
            isLatest = false,
            highlights = listOf(
                "Edge Panel Animation Studio",
                "Dynamic Category Tinting & Real-Time Widget Sync"
            ),
            features = listOf(
                "Subpage featuring 5 animation presets, custom duration/scale/easing controls, and live hot-reloading",
                "QuickCapture action button background tinting using category accent colors",
                "Real-Time Widget Sync mirroring app theme with WidgetUpdater"
            ),
            improvements = listOf(
                "Synchronous Preference Persistence with commit() for immediate writes",
                "Added bottom padding across subpages and horizontal scrolling for filter chips"
            ),
            bugFixes = listOf(
                "Clamped color, alpha, radii, and layout fraction bounds in expandPanel/collapsePanel to eliminate color extrapolation artifacts",
                "Replaced deprecated TabRow with SecondaryTabRow in WidgetSettingsScreen"
            )
        ),
        ReleaseNote(
            versionName = "0.9.1-beta01",
            versionCode = 3,
            releaseDate = "July 21, 2026",
            tag = AppVersionTag.BETA,
            isCurrent = false,
            isLatest = false,
            highlights = listOf(
                "Widget Customizer Engine",
                "Settings UI Redesign"
            ),
            features = listOf(
                "Production-grade WidgetSettingsScreen with live interactive previews, user archive rendering, category filtering, and opacity controls"
            ),
            improvements = listOf(
                "Redesigned main Settings screen into clean, logical section groups with clear visual badges",
                "Corrected collapse corner radii math and morph curve interpolation for edge panel"
            ),
            bugFixes = listOf(
                "Replaced reflection-based Moshi JSON parsing in Glance widgets with org.json for ROM stability",
                "Restored FLAG_NOT_FOCUSABLE window flag upon overlay collapse to fix Android back-swipe gesture blocking"
            )
        ),
        ReleaseNote(
            versionName = "0.9.0-beta02",
            versionCode = 2,
            releaseDate = "July 20, 2026",
            tag = AppVersionTag.BETA,
            isCurrent = false,
            isLatest = false,
            highlights = listOf(
                "Glance Widgets & Background Data Downloader",
                "Fuzzy Levenshtein Search & Code Highlighting"
            ),
            features = listOf(
                "Glance Widgets: RecentItemsWidget and QuickCaptureWidget components",
                "DataDownloadService for asynchronous background media retrieval",
                "Fuzzy and synonym-aware search ranking utilizing Levenshtein distance scoring",
                "Custom CodeHighlighter utility for real-time Markdown code block styling"
            ),
            improvements = listOf(
                "Cloud quota logic treats text/link items as free against 512MB limit",
                "Performance-gated dynamic background blur (Haze) with OS capability detection"
            )
        ),
        ReleaseNote(
            versionName = "0.9.0-beta01",
            versionCode = 1,
            releaseDate = "July 18, 2026",
            tag = AppVersionTag.BETA,
            isCurrent = false,
            isLatest = false,
            highlights = listOf(
                "Initial Beta Release of Second Brain",
                "Floating OCR & System Share Sheet Capture"
            ),
            features = listOf(
                "System share sheet support for text, images, videos, and links",
                "Swipeable floating edge-handle panel over any application for OCR scanning",
                "Automated Gemini image text extraction and speech-to-text voice memos",
                "Offline-first Room database storage with Firebase cloud sync"
            )
        )
    )

    fun getLatestRelease(): ReleaseNote = releaseHistory.firstOrNull { it.isLatest } ?: releaseHistory.first()

    fun isUpdateAvailable(): Boolean {
        val latest = getLatestRelease()
        return latest.versionCode > currentVersionCode
    }
}
