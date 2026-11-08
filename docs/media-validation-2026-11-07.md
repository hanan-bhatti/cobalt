# Media validation — 7 November 2026

## Executed

- Isolated one-worker Gradle run, 1536 MiB Java heap / 384 MiB metaspace.
- Targeted debug JVM/Robolectric run: **91 tests, 0 failures, 0 errors, 0 skipped**; Gradle elapsed **10m 21s**. Includes media identity/settings, outbox batching/pause/generation, episode catalogue, rating/control UI, migration, Trash and note storage cases. This is not a complete application test suite or device performance result.
- The queue regression inserted 1,000 intents, simulated a failed batch, and verified ten batches of 100 on recovery against a fake API. This is **not** evidence of 1,000 live Trakt writes.
- R8 release assembly: **BUILD SUCCESSFUL**, **2m 23s** (23 tasks from cache).
- First release APK SHA-256: `dc0d7d8a5fbf0b9d0b72f3c9f4d9b1b986ac4023f131b0653dac543ad56d7db5`.
- Installed with `adb install -r` on `emulator-5554`; no application-data clear or uninstall.
- First launch found a real upgrade failure: existing early v13 schema had `thumbnailUrl` but lacked `playbackProgress`, `mediaFactsJson`, `watchStatusOverride` and `media_tombstones`. The app displayed recovery; Room rejected the mismatched same-version identity hash. The earlier minimal SQL migration test did not cover this installed schema.
- A separately signed, read-only instrumentation probe retrieved table definitions and database version only. No note body, API key or OAuth token was retrieved. Emulator root was unavailable.

## Correction and regression

Database version 14 with additive, column-aware v13→v14 migration accepts both prior v13 layouts. A schema-only fixture from the emulator is used for a full Room validation regression, preserving a seeded body, rating and episode thumbnail. Both targeted migration tests passed (0 failures/errors/skips), Gradle elapsed **4m 51s**. Replacement release/device upgrade validation is pending. Before upgrade: **42 saved items, 12 episode rows, 0 pending intents, 0 Trash rows**. Counts were read without retrieving records.

## Still pending

- Replacement release upgrade and actual TMDB→Trakt save/rating/watchlist cleanup.
- Final APK season pager, trailer, offline cache, provider toggle/restart and local Jikan fixture checks.
- Instrumented migration matrix, process-death and failure simulations.
- 1,000-item live sync and large-note/media stress/performance measurements.
- Account quota handling: Trakt HTTP 420 is a capacity limit, not a transient rate limit. The worker must not blindly retry it. Read `GET /users/settings` limits before bulk watchlist testing.

The emulator has a TMDB key and an authorized Trakt test account. This report deliberately does not include credentials. No all-features or release-readiness claim is made.
