# Network inventory

Cobalt has no analytics SDK or automatic crash-report upload. The retained crash
handler writes a local report; Send opens an external email chooser. Sending the
email requires user action in that application.

This inventory describes current behavior, including remaining opt-out gaps.
It does not claim that all network paths already have a global disable switch.

| Destination | Purpose / data | Activation and control |
| --- | --- | --- |
| generativelanguage.googleapis.com | Gemini OCR, transcription and generation; selected input and user API key | User configures Gemini in Appearance & Preferences and requests the feature |
| api.themoviedb.org, image.tmdb.org | Media search, metadata, posters and backdrops | Movie & TV Metadata API-key setting; remote images may reload when saved media is displayed |
| api.jikan.moe | Anime search and metadata | User requests anime/media search; no dedicated network toggle |
| www.omdbapi.com | Optional movie metadata | Media lookup with configured credentials; no dedicated network toggle |
| api.trakt.tv, trakt.tv | User-authorized watch tracking, authentication, synchronization | Trakt settings and connection controls; scheduled work can run after enabling sync; manual Sync Now remains in Trakt settings |
| api.github.com, github.com and release redirect hosts | Release checks and downloads | Release & Updates: automatic checks default off; manual check/download is explicit |
| www.youtube.com, img.youtube.com and playback redirect hosts | Trailer links and thumbnails | User opens media details or taps a trailer; thumbnails may load automatically; no dedicated network toggle |
| www.justwatch.com, www.themoviedb.org, Netflix, Disney+, Amazon, Apple TV, Hulu, Max, Paramount+, Peacock, Crunchyroll, Tubi, Pluto TV, Vudu | Streaming-provider and media information links | Explicit link tap opens an external browser/application |
| aistudio.google.com, ai.google.dev, cloud.google.com, www.gnu.org | API-key setup, help and license links | Explicit link tap; documentation URL literals alone do not initiate requests |
| User-supplied link hosts and their redirects | Link metadata, preview images, opening links | Saving a link can fetch metadata automatically; opening it launches the browser. No dedicated preview network toggle yet |
| Remote media hosts and their redirects | Display/play/cache user-saved remote media | Displaying saved remote media can load it automatically; no dedicated network toggle yet |
| github.com | Feedback issues and discussions | Explicit action opens the browser with the composed report |
| User-selected email/share applications | Crash reports and shared content | Explicit chooser; destination and delivery controlled by selected application |

Cobalt-owned cloud sync is discontinued. No runtime network
calls were added by this cleanup. Gradle/Maven/GitHub Actions downloads are build
time connections, not application telemetry.
