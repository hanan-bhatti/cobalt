# Media validation — 8 November 2026

This report includes the code checks and the subsequently authorized live
emulator/account tests. The emulator is `emulator-5554`, Android SDK 37. The
same-package validation build retains the existing Cobalt library and settings.

## Corrections

- Settings use the app's rounded surfaces and clearer connection/search/archive
  groups. The Jikan endpoint handles keyboard insets, focus scrolling and
  Done-to-save. Pending Trakt Changes has its own queue page, separate from
  the account page, with title/action rows, pause/error states and retry.
- Existing TMDB movie/TV choices seed a durable, session-bound outbox on login.
  Capture and Media Hub share typed provider identity and transactional saves.
  Duplicate selections retain the saved ID and personal notes. Ratings,
  watchlist and watched-history snapshots reconcile removals while protecting
  pending edits and locally deleted titles. Unknown IDs remain queued while
  other imports continue. Movie and TV identities stay separate.
- Trakt changed watched-show responses in June 2026: `extended=full` no longer
  supplies season/episode progress. The app now requests `extended=progress`.
  All six account pagination loops continue to an empty array, since the server
  can cap pages below the requested limit. An upgrade marker forces one fresh
  account import even when activities are unchanged; it is saved only after a
  complete successful sync. Old checkpoints cannot skip the repaired phase.
  Missing seasons/episode lists fail safely without erasing saved progress.
  Missing episode totals do not make a partially watched show appear completed.
  References: [Trakt API update](https://github.com/trakt/trakt-api/discussions/775)
  and [intentional empty sync overviews](https://github.com/trakt/trakt-api/issues/837).
- Account sync handles account state/basic title identity. Opening a movie or
  show renders saved details immediately and silently refreshes catalogue data
  through the shared TMDB/Trakt/OMDb hydrator. Catalogue merges preserve notes,
  personal rating/status, watched flags and cached season/episode details.
  Deletion, cancellation, newer edits and account changes are guarded.
  Selected-season episode requests retain cache on failure. Home descriptions
  exclude URLs; cards distinguish movies, TV, anime and manga.
- More Info groups catalogue facts, source ratings, community/distribution data,
  season scores and source links. It remains reachable when facts exist without
  a synopsis and includes manga facts/MyAnimeList links. Manga uses reading
  language in controls and status confirmations. Unknown counts stay unknown.
- Credential checking has a bounded timeout and no profile request blocking
  account data delivery. A five-minute refresh window avoids unnecessary token
  refreshes on ordinary app openings. Eligible foreground starts schedule sync.
- YouTube trailers play in an in-app WebView and reuse the existing play/pause
  icons. Live testing exposed a zero-height iframe despite advancing audio/time;
  explicit viewport pixel sizing now keeps the video visible. Playback pauses
  offscreen/background, and timeout/provider errors provide retry. The narrow
  event bridge has no file/account access and is retained through R8.

## Live results

| Check | Result |
| --- | --- |
| Existing library hydration | All 14 original movie/TV records receive synopsis/runtime; TV receives seasons |
| Stranger Things catalogue | Regular seasons 1–5; episode rows 8, 9, 8, 9, 8, with titles and thumbnails |
| American Horror Story catalogue | Specials plus regular seasons 1–13 retained |
| TMDB capture | Interstellar saved, linked to Trakt, enriched with cast/synopsis; duplicate save keeps its ID |
| Remote Trakt addition | Dark watchlist addition imports into Cobalt |
| Remote progress | Temporary Dark S1E1 accepted, returned in account watched snapshot, shown locally, then removed and reconciled |
| Real local Jikan | Official v4 server supplies Berserk manga and Cowboy Bebop anime; anime has 26 episodes |
| Jikan failures | Ten repeated searches pass; fresh containers/index migration plus six searches pass; empty result, invalid-ID 404 and stopped-server cache checked |
| Provider switches | Disabled Jikan keeps saved titles; probes restore original source preferences |
| Manga UI | Real cover/synopsis/genres, Plan to Read/Reading states; manga never enters the Trakt outbox |
| Home/details offline | Saved descriptions/catalogue remain readable |
| Keyboard/queue UI | Real Gboard field remains reachable; Done saves; distinct queue page shows delivered changes |
| Trailer playback | Visible Interstellar and Dark video frames; native pause/resume; offscreen and background pause; offline timeout/retry |

The successful live Trakt progress report records one accepted episode,
`serverSnapshotContainsEpisode=true`, `remoteProgressDelivered=true` and
`temporaryHistoryRestored=true`. Earlier failures were traced to the obsolete
extension, rather than server propagation delay.

The library has **59 records**: all **55 original IDs** plus Interstellar, Dark,
Berserk and Cowboy Bebop. All **41 original non-media records** remain unchanged.
The added titles are retained for the user's inspection. The temporary episode
history is removed. Backups stay outside the repository under `/tmp`.

Actual source ratings include Interstellar IMDb 8.7, Rotten Tomatoes critics
73% and Metacritic 74/100; Endgame IMDb 8.4, critics 94% and Metacritic 78/100.
The configured APIs did not provide Popcornmeter. No audience score is fabricated
or substituted from critics/community ratings.

## Reproduce local Jikan

```sh
python3 tools/media-local-jikan.py start
adb reverse tcp:8702 tcp:8702
python3 tools/media-local-jikan.py status
```

Use `http://127.0.0.1:8702/v4/` in the emulator's Jikan setting. The official
Jikan image and isolated MongoDB containers are bound to host loopback. This
small real validation catalogue contains Cowboy Bebop and Berserk; a full
catalogue requires the upstream indexers. `stop` preserves the container data.
A separate fresh-container setup verified the helper from an empty database, including migration, real MAL fetches and repeated searches; those temporary containers were removed. The main server was restarted after the unavailable-server test. Original app
provider preferences are restored, so the user can explicitly choose this URL.

## Automated and final release evidence

The Trakt contract regressions and initial targeted suite pass: **84 tests,
11 classes, zero failures/errors**. They cover capped short pages across all
six snapshots, upgraded unchanged accounts, missing episode history, catalogue
preservation and complete-snapshot retry. Other media checks cover pre-login
seeding, batches, identity collisions, deletion races, offline source failures,
minimum-SDK dates/PKCE, settings and trailer navigation/bridge behavior.

The final full JVM/Robolectric suite passes: **425 tests in 87 classes, zero
failures, zero errors, zero skipped**. This includes the corrected disconnect
race test, which waits for the actual in-flight request instead of assuming a
50 ms scheduling delay, and real-format Metacritic (`78/100`) UI coverage.
The final normal Gradle invocation completed **BUILD SUCCESSFUL in 36m 51s**.
`assembleRelease` and full `lintRelease` pass. Lint reports **0 errors, 317
warnings and 11 hints**; the largest groups remain UseKtx (171),
GradleDependency (24) and ModifierParameter (22). No blanket lint baseline was
introduced. `git diff --check` passes.

The optimized, non-debuggable APK is installed on the emulator. Cobalt still
shows 59 records and Trakt remains connected. Release smoke checks verify
Interstellar facts/IMDb/Rotten Tomatoes/Metacritic, cached manga facts/rating/link,
Stranger Things at 1 of 42 watched with all eight Season 5 episode titles and
thumbnails, retained completed movies, and the separate delivered queue page.
The first release trailer load reached the bounded timeout; Retry played a
visible video frame. Native pause/resume and background/return pause pass.
The temporary Android test package and WebView debug forward were removed;
the optimized release and healthy main local Jikan server remain installed/running.

`app/build/outputs/apk/release/app-release.apk` is **2.2.3 (19)**,
minimum SDK **24**, **8,413,440 bytes**. APK v2 signature verification passes.
The project uses its **Android Debug signing fallback** for this artifact;
publishing requires the configured production keystore.

APK SHA-256: `59938ad437ef3de57b99fcde8305331a3e2f5b06f6481b3ae75830808e4907ff`.
R8 retains `TrailerEvents` and the `ready`, `state`, `error` callback names.

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease \
  --continue --no-daemon --max-workers=1 --no-configuration-cache \
  -Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' \
  -I tools/notes-test-limits.gradle
```

Full reports: `app/build/reports/tests/testDebugUnitTest/index.html` and
`app/build/reports/lint-results-release.html`.

Live JSON reports are in `app/build/reports/media-live-validation/`. The
opt-in Android harness uses `MediaDeviceValidationTest` with `media_live=yes`;
ordinary CI never performs live account writes. Server-side API additions and
removals were exercised; browser-click Trakt website testing was not performed.
A single emulator and the configured account cannot establish every device,
region, account quota or third-party provider restriction.
