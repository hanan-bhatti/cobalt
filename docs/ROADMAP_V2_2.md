# Cobalt v2.2+ Evolution: Architecture, Design & Roadmap

This document outlines the architecture, specifications, and execution plan for the next major evolution of **Cobalt**.

---

## 1. High-Level Vision & Priorities

Cobalt is evolving from a passive local bookmark vault into a proactive, delightful **Second Brain**. The focus is strictly on **refining and supercharging core experiences**, eliminating rough edges, and making the app feel fluid, fast, and alive without adding unnecessary bloat.

### The Core Loop
```
[ 1. Save It ]       --> Zero-friction, instant capture (Share Sheet & redesigned Capture screen)
[ 2. Read It ]       --> In-app Chrome Custom Tabs with unified theme & actions
[ 3. Track It ]      --> Ultra-fast, low-CPU GitHub-style heatmap grid & API usage stats
[ 4. Remember It ]   --> 100% local, privacy-first reminders (no background cloud daemons)
[ 5. Optimize It ]   --> DB indexing, Coil memory tuning, bug fixes & broken flow repairs
```

---

## 2. Pillar 1: "Save It" (Capture Screen Redesign & Ingestion Pipeline)

### Problems Identified
- Capture can feel blocking or heavy when metadata scraping is slow.
- In-app capture and share sheet flow have friction points.

### Architecture & Plan
1. **Optimistic Capture (Zero-Lag)**:
   - When a URL is shared into Cobalt or entered in the capture screen, the item is immediately written to Room DB with a placeholder title and state `isScraping = true`.
   - The user gets instant visual confirmation (toast / haptic) and is never locked on a progress indicator.
   - Background worker (Coroutines / WorkManager) fetches OpenGraph metadata, resolves cover images, and updates the Room entity asynchronously.
2. **Redesigned Capture Interface**:
   - Clean, tactile input modal ready for custom redesign.
   - Quick-tag chips with intelligent auto-suggestions based on domain (e.g., `youtube.com` $\to$ `#video`, `github.com` $\to$ `#dev`).
   - Seamless switching between Link, Markdown Note, Audio, and Image OCR.

---

## 3. Pillar 2: "Read It" (Chrome Custom Tabs Integration)

### Architecture & Plan
Instead of launching an external browser task that interrupts the user flow:
1. **Chrome Custom Tabs (CCT) Manager**:
   - A dedicated utility `CobaltCustomTabsLauncher` using `androidx.browser:browser`.
   - **Theme Integration**: Toolbar color automatically matches Cobalt's Material 3 surface / primary container color (supports dark and AMOLED modes).
   - **Custom Actions**:
     - Custom close button (returns immediately to Cobalt feed preserving exact scroll position).
     - Custom toolbar buttons: "Share Link", "Copy Link", and "Star/Favorite Item".
   - **Fallback**: Gracefully falls back to standard `ACTION_VIEW` intent if no compatible browser supports Custom Tabs.

---

## 4. Pillar 3: "Track It" (Analytics, Knowledge Heatmap & API Stats)

### A. High-Performance GitHub-Style Heatmap Grid (`KnowledgeHeatmap`)
A reusable Jetpack Compose component visualizing knowledge capture activity over 3, 6, or 12 months.

#### Technical Specifications (Zero Recomposition Lag & Low CPU):
- **Canvas Rendering**: Avoid nesting hundreds of individual `Box` or `Surface` composables. Render the entire grid (7 rows $\times$ 13–52 columns) on a single hardware-accelerated `Canvas` via `drawRoundRect` or `drawRect`.
- **Precomputed Color Buckets**:
  - Intensity levels 0 to 4:
    - Level 0: Surface variant / subtle outline (no items)
    - Level 1: Light accent (1–2 items)
    - Level 2: Medium accent (3–5 items)
    - Level 3: Strong accent (6–9 items)
    - Level 4: Vibrant primary (10+ items)
- **Memoized Date Aggregation**:
  - Run SQL aggregation in Room: `SELECT strftime('%Y-%m-%d', datetime(createdAt/1000, 'unixepoch')) AS day, COUNT(*) AS count FROM items GROUP BY day`.
  - Memoize the resulting `Map<LocalDate, Int>` in ViewModel using `derivedStateOf`.
- **Interactive Tooltip**: Fast tap/hover showing date and item count without re-rendering the whole grid.

### B. API Key Usage Tracker
Give users complete transparency over their local API usage:
- **Metrics Tracked** (stored in local Datastore / Room table):
  - Gemini AI: Total requests, estimated tokens processed (OCR + transcription).
  - TMDb: Total movie/series metadata lookups performed.
  - Jikan (MyAnimeList): Total anime searches executed.
- **Visuals**: Clean card with breakdown by service, monthly usage reset option, and status indicator (Healthy / Rate-Limited / Invalid Key).

---

## 5. Pillar 4: "Remember It" (Smart Resurfacing & Reminders)

### Architecture & Privacy (100% Local & FOSS-Compliant)
- **No Background Polling Services**: Pure Android `AlarmManager` with exact/inexact alarms and a BroadcastReceiver.
- **Zero Cloud**: Complies strictly with F-Droid and privacy standards.
- **Two Modalities**:
  1. **Item Reminder**: Quick action on card $\to$ "Remind me tonight", "Tomorrow morning", "This weekend", or custom date/time.
  2. **Serendipity / Knowledge Resurface**: Optional weekly notification highlighting an unread or archived item saved in the past.

---

## 6. Pillar 5: Performance Optimization & Bug Fixes

### A. Database & Query Tuning
- Add composite indices on `SavedItemEntity`:
  - `(isArchived, createdAt DESC)`
  - `(type, createdAt DESC)`
- Implement SQLite Full-Text Search (FTS4 / FTS5) virtual table for instant full-text searching across markdown notes and scraped article descriptions.

### B. Image & Memory Optimization (Coil)
- Configure global Coil image loader with explicit memory cache limits (e.g., 25% of app memory) and disk cache bounds.
- Crossfade transitions enabled with hardware bitmap caching.

### C. Repairing Broken Functionality
- **Jikan API Integration**: Add retry logic, backoff, and user-agent headers to prevent 429 rate-limiting failures on anime lookups.
- **Search State Consistency**: Keep Home filter and Search tab state decoupled or synchronized reliably without resetting feed views on back navigation.
- **Lifecycle & Navigation Audit**: Clean up view model scopes and state holders.

---

## 7. Execution Checklist for New Chat

- [x] **Phase 1**: Chrome Custom Tabs launcher integration (`CobaltCustomTabsLauncher.kt`) & click wiring on link cards.
- [ ] **Phase 2**: High-performance Canvas-based `KnowledgeHeatmap` component + Room aggregation DAO.
- [ ] **Phase 3**: API Key Usage tracker screen/card in Settings.
- [ ] **Phase 4**: Capture screen redesign implementation & asynchronous ingestion pipeline.
- [ ] **Phase 5**: Local `AlarmManager` reminder system with notification channels.
- [ ] **Phase 6**: Performance pass (Room indices, FTS search, Jikan API reliability).
