# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.1-beta01] - 2026-07-21

### Added
- **Widget Customizer Engine:** Production-grade `WidgetSettingsScreen` with live interactive previews, real user archive data rendering, category filtering, opacity controls, and active widget detection.

### Changed
- **Settings UI:** Redesigned the main Settings screen into clean, logical section groups with clear visual badges.
- **Window Animation Geometry:** Corrected collapse corner radii math and tuned morph curve interpolation for the edge panel.
- **Media Availability Logic:** Scoped `isUnavailable` state evaluation strictly to media items (images, videos, audio) to prevent text file false-positives.

### Fixed
- **Oppo/ColorOS Crash:** Replaced reflection-based Moshi JSON parsing in Glance widgets with `org.json` to resolve stuck loading states on custom Android ROMs.
- **Back Gesture Navigation:** Restored the `FLAG_NOT_FOCUSABLE` window flag upon overlay collapse to fix Android system back-swipe gesture blocking.
- **OCR Workflow:** Prevented tab switches during image saves, defaulted to styled Markdown previews, and enabled instant task exit.
- **Metadata Extraction:** Resolved relative `og:image` preview URLs using absolute URL resolution (`absUrl`) and added graceful HTTP error handling.

## [0.9.0-beta02] - 2026-07-20

### Added
- **Glance Widgets:** Dedicated `RecentItemsWidget` and `QuickCaptureWidget` components featuring dynamic color support.
- **Background Data Downloader:** `DataDownloadService` and `DataDownloadManager` for asynchronous background media retrieval.
- **Search Capabilities:** Fuzzy and synonym-aware search ranking utilizing Levenshtein distance scoring.
- **Device Session Management:** Remote session tracking, device type detection, and profile session management.
- **Code Syntax Highlighting:** Custom `CodeHighlighter` utility for real-time Markdown code block styling.
- **Media Processing:** `Coil-Video` support for inline video thumbnail rendering and preservation of audio transcripts during cloud sync.
- **UI Blur Effects:** Performance-gated dynamic background blur (`Haze`) with automatic RAM/OS capability detection.
- **Theming:** Dynamic Material You color extraction on Android 12+ across UI screens and widgets.
- **Telemetry:** Firebase Crashlytics, Firebase Performance monitoring, and privacy-first Analytics with query masking.
- **Battery Management:** `BatteryOptimizationHelper` to request background execution permissions.
- **Repository Standards:** AGPLv3 license headers, issue/PR templates, and updated contributor documentation.

### Changed
- **Quota Calculations:** Updated cloud quota logic to treat text/link items as free, counting only media files against the 512MB limit.
- **Unavailable Media Handling:** Added an `isUnavailable` flag and custom visual placeholders for items deleted from cloud storage.
- **Edge Panel Animation:** Implemented per-frame window layout bounds morphing during edge panel expansion and collapse to prevent visual snapping.

## [0.9.0-beta01] - 2026-07-18

### Added

#### Capture
- Added system share sheet support to instantly capture text, images, videos, and links shared from other applications.
- Added quick capture actions through an expanding floating button to manually type notes, bookmarks, or code snippets.
- Added Android app launcher shortcuts for initiating smart selection scanning and quick note drafts directly from the home screen.
- Added home screen widgets for fast capture access and previewing recently archived items.

#### Floating OCR
- Added a swipeable floating edge-handle panel to invoke capture tools over any running application.
- Added region-marking selection capability to crop specific screen segments for content ingestion.
- Added automated image text extraction using Google Gemini to convert screenshots into digital notes.
- Added fuzzy link extraction to detect shared URLs in captured screenshots and review them before saving.

#### Voice Memos
- Added a voice recording interface with live audio waveform feedback.
- Added automated Gemini audio transcription to convert spoken notes into text.
- Added smart speech-formatting to clean up voice transcriptions and generate titles in markdown format.

#### Organization
- Added custom color-coded folders using design system stickers to group relevant notes.
- Added drag-and-drop gestures to reorder folder positions and rearrange archive lists.
- Added automated media categorization to organize elements by note type.
- Added folder archiving to remove items from the active dashboard without permanent deletion.

#### Privacy & Sync
- Added offline-first storage using a local database to ensure instant app launch and accessibility without network connection.
- Added cloud backup sync to secure database updates and media files on Firebase.
- Added secure, passwordless authentication using Google Sign-In and email login links.
- Added storage diagnostics to compress cached media and export or import database backups.
-
