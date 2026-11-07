# Release validation continuation — 7 November 2026

Status: incomplete; this is not release approval. Keep the failed performance
budgets and untested scenarios in the previous report open.

## Installed artifact

- User-built release, version 2.2.3 / code 19, package
  `com.hanan_bhatti.cobalt`, non-debuggable, installed with `adb install -r`.
- Preserved APK: `app/build/release-device-validation-20261107/tested-release.apk`.
- SHA-256: `4b6023f199aa9c30181778a7cf6f596fc3cb3de8f1b45a210561f98ba4d84e1c`.
- Matching R8 mapping archived alongside the APK as `mapping.txt.gz`.
- Same API 37 emulator as the previous report. Its existing synthetic library
  and test account were retained. No uninstall or data clear was performed.
- Artifact verification now passes for both WorkManager input-merger public
  constructors; real Trakt workers also completed successfully.
- `apksigner` identifies this local APK as signed with the Android Debug
  certificate (SHA-256 `06:A1:D8:F4:91:4D:A1:F4:38:42:9E:80:41:CE:A9:CC:91:FC:02:FB:19:F9:16:AE:82:E5:C9:A0:91:FC:A9:1B`).
  Non-debuggable release behavior does not establish production signing.
  Signing configuration was not changed. Confirm the intended update key before
  publishing; do not replace the signing key of existing installs.

## Real Trakt account checks

The user explicitly authorized reads and writes on the emulator test account.
Only a temporary personal rating and watchlist change were made. Existing watch
history and watched timestamps were left unchanged.

- PKCE authorization reaches `auth.trakt.tv`, but the provider rejects the HTTPS
  redirect for this Development registration. The user-supplied developer-page
  screenshot states that Development apps accept localhost redirects only.
- Device-code authentication through the existing test-account browser session
  succeeded. The app profile loaded and background import completed with one
  movie, two TV shows and a personal rating update.
- Set Avengers: Infinity War (2018) to 7/10. The authenticated Trakt website
  independently showed 3.5/5 stars, proving outbound rating delivery.
- Added the movie to the watchlist. The first worker completed without delivering
  that second mutation. An explicit subsequent Sync Now delivered it, and the
  authenticated website then offered Remove from Watchlist.
- This exposed a scheduling gap when an edit arrives during a running KEEP
  worker. Source now schedules a serialized follow-up and drains again after
  import. These changes compile, but their new release-runtime behavior still
  needs a rebuilt APK and regression test.
- Cleanup completed: cleared the temporary rating and removed the temporary
  watchlist entry in Cobalt, explicitly synced, and independently verified five
  empty stars plus Add to Watchlist on Trakt. Evidence:
  `054-trakt-cleanup-remote.png` and `events.jsonl` in the ignored evidence folder.
- Allowed the Android notifications permission prompt on this disposable test
  emulator during manual sync. No authentication credentials were printed or
  extracted from application private storage.

Unauthenticated public API probes returned empty lists despite authenticated
account data. Those probes are **not** evidence of remote synchronization.

## Callback verification

- `https://cobalt.hanan-bhatti.site/callback`: HTTP 200, HTML callback present,
  `no-referrer` policy present.
- `https://cobalt.hanan-bhatti.site/.well-known/assetlinks.json`: HTTP 200,
  `application/json`, correct package and the installed signing fingerprint.
- Android `pm get-app-links` reports `cobalt.hanan-bhatti.site: verified`.
- A real successful HTTPS OAuth round trip remains blocked by the Trakt app
  registration. App Links verification cannot override that provider rule.
  No localhost listener or insecure redirect workaround was added.

## Rating artwork

Commit `714c9cd` converts the supplied IMDb, Rotten Tomatoes and Popcornmeter
artwork to native Android vector XML and adds source-specific cached-score
badges. Original colors and aspect ratios are retained, including dark mode.
The badges never label the generic Trakt/TMDB community score as IMDb.
Missing, invalid and out-of-range scores are hidden. No ratings service or
scraper was added; supplying logos does not supply ratings data.

Executed focused command: `:app:testDebugUnitTest --tests
com.example.ui.components.MediaRatingsRowTest`, offline, one worker, in-process
Kotlin, 1536 MiB Gradle heap and 384 MiB test heap. Result: **3 tests, 0 failures,
0 errors; BUILD SUCCESSFUL in 5m 4s**. This also compiled current debug Kotlin.

Native Android vector render outputs were inspected visually at badge size;
brand colors were checked in the test. Light/dark artwork previews were also
inspected. The new badges have not yet been installed and visually tested in a
rebuilt release APK. No release or performance result is implied by unit tests.

## Current targeted regression run

After the artwork tests, ran the editor, selection-menu, storage, Trash,
Trakt authentication/outbox and migration suites against the current source:
**67 tests across 10 classes, zero failures/errors/skips; BUILD SUCCESSFUL in
1m 14s**. The same one-worker/heap caps were used. Log:
`/tmp/cobalt-editor-storage-trakt-regression-current.log`.

These are unit/Robolectric checks, not the complete device test matrix. Gradle
still reports existing Gradle 10 compatibility deprecations, and test runtime
libraries emit native-access/Unsafe JVM warnings. The reported Trakt sheet
Kotlin deprecation did not recur in the current debug Kotlin compilation.

## Outstanding release validation

- Rebuild/install the latest source, including pending Trakt and deletion fixes.
- Validate sync-toggle persistence and category behavior after restart.
- Test read-only season/episode progress, half-star control, search without a
  TMDB key and optional TMDB enrichment in the actual release UI.
- Handle and test remote removals, unmatched queued IDs and account quota errors.
- The requested 1,000-item real remote synchronization is not complete.
- Rerun Capture table/IME, rapid undo/redo and selection UI checks on the new APK.
- Execute MB image/video/audio accounting, Trash restore and permanent deletion
  scenarios. Prepared fixtures are not executed test results.
- Validate origin-screen Undo placement, navigation dismissal and border timer.
- The older scrolling/jank budget failures remain open. No new CPU, peak heap,
  typing latency or first-content performance measurements were made here.
- Full Android API matrix, process-death/fault-injection matrix and physical
  device stress testing remain incomplete.
