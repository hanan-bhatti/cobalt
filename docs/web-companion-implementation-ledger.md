# Cobalt Local Web Companion: Implementation Ledger

This document tracks every atomic change, file created/modified, and verification status for the **Cobalt Local Web Companion** on branch `feature/local-web-companion`.

---

## Progress Overview

| Step | Component | Status | Description |
| :--- | :--- | :---: | :--- |
| **01** | **PIN Creator & Security Manager** | ✅ Complete | 4-digit rotating PIN, progressive lockout, global tripwire, session tokens, and rate limiter. |
| **02** | **Editor Session Registry** | ✅ Complete | Active note lock registry preventing writes to notes open in mobile editor; hooked into `CobaltViewModel`. |
| **03** | **Embedded Server Engine** | ✅ Complete | Lightweight coroutine-based HTTP/1.1 server for LAN endpoints, zero external dependencies. |
| **04** | **Notes API Endpoints** | ✅ Complete | Paginated note listing, detail retrieval, PIN auth login/logout, media streaming. |
| **05** | **Web Client Assets** | ✅ Complete | Clean, responsive SPA (HTML/CSS/JS) mirroring phone's theme and dynamic wallpaper colors. |
| **06** | **Android UI & Service** | ⏳ Pending | Web Companion settings card, start/stop toggle, active PIN & IP display. |

---

## Atomic Step Details

### Step 05: Web Client Assets & Real-Time Phone Theme Sync
- **Package / Assets**: `app/src/main/assets/web/`
- **Files**:
  - `app/src/main/kotlin/com/example/web/server/WebThemeExporter.kt`
  - `app/src/main/assets/web/index.html`
  - `app/src/main/assets/web/style.css`
  - `app/src/main/assets/web/app.js`
- **Key Capabilities**:
  - **Dynamic Theme Synchronization**: `GET /api/theme` exports the active Android theme (Dark, Light, or Android 12+ Material You Dynamic wallpaper colors) as CSS custom properties (`--primary-color`, `--bg-color`, `--surface-color`, etc.). The web app immediately adopts your phone's exact palette.
  - **PIN Auth Modal**: 4-digit PIN authentication overlay with lockout countdown timers and auto-focus.
  - **Fast Card Grid**: Paginated 25-note chunks with search debounce (300ms) and type filters (Notes, Code, Links, Media).
  - **On-Demand Reader**: Loads full markdown body only when note is clicked.
  - **Mobile Lock Alert**: Displays warning badge on cards and banner in reader if note is actively being edited on the phone.
  - **Self-Contained Markdown Engine**: Formats headers, code fences, blockquotes, lists, tables, and images without external libraries.

- **Package**: `com.example.web.server`
- **Source**: `app/src/main/kotlin/com/example/web/server/WebCompanionRouter.kt`
- **Key Capabilities**:
  - `POST /api/auth/login`: PIN verification, issues 256-bit token + `Set-Cookie: cobalt_session` (HttpOnly, SameSite=Strict).
  - `POST /api/auth/logout`: Revokes session token and clears session cookie.
  - `GET /api/auth/status`: Lightweight session validity check.
  - `GET /api/status`: System telemetry and note count.
  - `GET /api/notes`: Paginated card streaming (`page`, `limit` bounded to 1-100, `q` search query, `folder`, `type`).
    - Excludes deleted notes.
    - Annotates items with `isLockedByMobile: true/false`.
    - Never dumps full note bodies.
  - `GET /api/notes/{id}`: On-demand full markdown body and metadata with mobile lock warning.
  - `GET /api/media/{id}`: Local media attachment streaming.
  - Static asset streaming with MIME type resolution and bootstrap HTML fallback.
- **Unit Tests**: `app/src/test/java/com/example/web/server/WebCompanionRouterTest.kt`

- **Package**: `com.example.web.server`
- **Source**:
  - `app/src/main/kotlin/com/example/web/server/HttpModels.kt`
  - `app/src/main/kotlin/com/example/web/server/CobaltEmbeddedServer.kt`
- **Key Capabilities**:
  - Pure Kotlin/Java socket server running on `Dispatchers.IO` (0% CPU at idle, zero external dependencies, 100% safe for F-Droid).
  - Path traversal defense (`HttpRequest.sanitizePath` filters `..` and relative jumps).
  - 15-second socket read timeouts against slowloris attacks.
  - Automatic DNS-rebinding verification against allowed local network hosts.
  - Per-IP rate limiting before request dispatch.
  - Automatic CORS `OPTIONS` preflight resolution.
  - Safe request bounding (16KB headers, 10MB body max).
- **Unit Tests**: `app/src/test/java/com/example/web/server/CobaltEmbeddedServerTest.kt`

- **Package**: `com.example.web.session`
- **Source**: `app/src/main/kotlin/com/example/web/session/EditorSessionRegistry.kt`
- **Integration**: `app/src/main/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt`
- **Key Capabilities**:
  - `mobileActiveNoteId`: StateFlow tracking the note ID open on the phone.
  - Multi-client exclusive web lock with heartbeat tracking (60s stale timeout).
  - Preempts web write attempts with `LockAcquisitionResult.ConflictMobile`.
  - **Edge-Case Hardening**:
    - **Back Button / BackHandler**: Both `CaptureScreen.kt:1634` and `MainActivity.kt:296` call `viewModel.cancelCapture()` on Android back button/gesture, setting `editOriginalId = null` and releasing the mobile lock.
    - **Close / Cancel Icon**: Top-bar close button invokes `cancelCapture()`, releasing the lock.
    - **Save Completion**: Successful save automatically releases the lock.
    - **ViewModel Teardown**: `CobaltViewModel.onCleared()` guarantees lock release on activity/viewmodel destruction.
- **Unit Tests**: `app/src/test/java/com/example/web/session/EditorSessionRegistryTest.kt`

- **Package**: `com.example.web.auth`
- **Source**: `app/src/main/kotlin/com/example/web/auth/WebCompanionSecurityManager.kt`
- **Key Capabilities**:
  - `SecureRandom` 4-digit numeric PIN (`0000`–`9999`).
  - Ephemeral PIN rotation on server start, manual trigger, or security tripwire.
  - Progressive IP lockout (5 failed attempts = 10-minute lockout).
  - Global tripwire: auto-rotates PIN if cumulative failed attempts across IPs exceed 12.
  - 256-bit cryptographically secure session token issuance.
  - Session expiration after 2 hours of inactivity.
  - Per-IP API rate limiting (token bucket: 60 req/min, burst 20).
  - Host-header verification to thwart DNS rebinding attacks.
- **Unit Tests**: `app/src/test/java/com/example/web/auth/WebCompanionSecurityManagerTest.kt`
