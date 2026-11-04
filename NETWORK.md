# Network inventory

Cobalt has no analytics SDK or automatic crash-report upload. The retained crash
handler writes a local report to private app storage; selecting "Send" opens an
external email chooser. Sending the report requires user confirmation in that email
application. Temporary share files and crash report copies in the app cache are
retained until system or user cache clearing.

This inventory describes current implementation behavior, including remaining
opt-out gaps and automatic fetch paths. It does not claim that all network paths
already have a global disable switch.

| Destination | Purpose / data | Activation and control |
| --- | --- | --- |
| generativelanguage.googleapis.com | Gemini OCR (user-selected crop or full-screen fallback), audio memo transcription, and speech text formatting; sends cropped or full image bytes, recorded audio, prompt text, and user-provided API key as a query parameter | User enters a Gemini API key in Profile settings and explicitly requests an AI feature. Aggregate local token counters are stored on device and never uploaded. |
| api.themoviedb.org | Movie and TV multi-search, details, credits, seasons, watch providers, and trailer keys; sends search query terms, media IDs, and user-provided TMDb API key as a query parameter with a custom app User-Agent | User enters a TMDb API key in Profile settings. Activated when searching movies/TV in Media Hub or during Trakt TV sync backdrop/provider enrichment. |
| image.tmdb.org | Movie, TV, and provider poster and backdrop images | Loads automatically via Coil whenever saved media items with TMDb image paths are displayed in feeds or detail views. No dedicated toggle. |
| api.jikan.moe | Anime search and streaming provider metadata; sends query terms and anime IDs with a custom app User-Agent | User initiates an anime search in Media Hub. No API key required. Requires an active internet connection; cannot perform new searches offline. |
| www.omdbapi.com | **Unused / Dormant**. An interface is declared with a demo API key, but it is not called by any repository, ViewModel, or background worker in the app | Inactive. No runtime network requests are made to OMDb. |
| api.trakt.tv, trakt.tv (or user-configured custom base URL) | Trakt OAuth 2.0 Authorization Code with PKCE and Device Code authentication, user profile, and two-way synchronization (watched history, episode plays, ratings 1–10, and watchlist); sends Client ID, PKCE code verifier and challenge, device codes, single-use refresh tokens, Bearer auth tokens, media IDs, timestamps, and ratings | User connects Trakt via primary PKCE browser flow (with verified HTTPS callback at cobalt.hanan-bhatti.site/callback) or secondary Device Code flow at trakt.tv/activate. Initial sync runs post-auth; manual sync is available in Trakt settings. Local watch and rating changes trigger immediate outbound updates. Scheduled periodic sync is not active (the worker scheduler is unused). Disconnecting cancels pending sync tasks, removes local tokens and session data, and sends a bounded revocation request to POST /oauth/revoke with the access token and Client ID. If offline, timed out, or rejected, local clearing still completes and the user is directed to Trakt's authorized applications web settings. Attribution: "Cobalt uses Trakt but is not endorsed or certified by Trakt." |
| User-configured custom Trakt host (if enabled) | Self-hosted mirror or proxy destination for all Trakt API traffic; receives Client ID, Trakt Bearer tokens, and synchronization payloads | Configured in Trakt Advanced Settings. When set, replaces api.trakt.tv for all Trakt authentication and sync calls. |
| cobalt.hanan-bhatti.site (GitHub Pages) | Static project website hosting Privacy Policy, Terms, Android Digital Asset Links (`/.well-known/assetlinks.json`), and OAuth redirect fallback (`/callback`) | No user analytics, trackers, or dynamic server backend. GitHub Pages serves static files and handles verified Android App Links verification. |
| www.youtube.com, img.youtube.com and playback redirect hosts | Trailer playback rendered in an embedded in-app Android WebView; thumbnail previews | Requests begin automatically when opening the detail screen of any movie or show with a trailer URL. The embedded WebView runs with JavaScript, DOM storage, and third-party cookies enabled. Fallback thumbnails load automatically via Coil. |
| www.justwatch.com, www.themoviedb.org, Netflix, Disney+, Amazon, Apple TV, Hulu, Max, Paramount+, Peacock, Crunchyroll, Tubi, Pluto TV, Vudu | Streaming-provider and media information links | Explicit link tap launches an external browser or Chrome Custom Tab. |
| aistudio.google.com, ai.google.dev, cloud.google.com, www.gnu.org | API-key setup, help, and license documentation links | Explicit link tap; documentation URL literals alone do not initiate requests. |
| User-supplied link hosts and their redirects | Link metadata preview scraping (title, description, image) via Jsoup; opening links | Saving or sharing a URL automatically contacts the host website using a desktop Chrome User-Agent to fetch HTML metadata. Opening a link launches Chrome Custom Tabs or an external browser. No dedicated preview network toggle. |
| Remote media hosts and their redirects | Download and cache user-saved remote image, video, or audio URLs to local storage (`ensureMediaCached`) | Opening the detail view of an item with a remote HTTP/HTTPS URL triggers an automatic download and local cache. No dedicated network toggle. |
| api.github.com, github.com and release redirect hosts | Application release checks and manual downloads | Release & Updates: automatic startup checks are disabled by default (`auto_update_check_enabled = false`); manual checks in the Release Updates screen contact GitHub. Cobalt does not automatically download or install updates; release links open GitHub in a browser. |
| github.com | Feedback issues and discussions | Explicit action opens the browser with pre-filled title and body templates. |
| User-selected email / share applications | Crash reports, feedback exports, and shared content | Explicit chooser; destination and delivery are handled by the selected application. Crash reports are sent to hannanbhatti2006@gmail.com only when confirmed by the user in their email client. |

Cobalt-owned cloud note sync is discontinued. No runtime network calls were added
by this cleanup. Gradle, Maven, and GitHub Actions dependencies are build-time
connections, not application telemetry.
