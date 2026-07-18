# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
