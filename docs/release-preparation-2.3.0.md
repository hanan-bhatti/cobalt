# Cobalt 2.3.0 release preparation — 2026-11-11

The next release is **2.3.0 / version code 20**. The GitHub API response confirmed earlier in this session lists v2.2.2 as the latest published release. The unpublished 2.2.3/code-19 changes are consolidated into 2.3.0; there is no separate 2.2.3 release entry or Fastlane changelog.

## Release contents

- Root and bundled changelogs match, with highlights followed by the full development record since 2.2.2.
- F-Droid metadata targets v2.3.0/code 20, and its build recipe expects the v2.3.0 tag created at publication. Fastlane code-20 release notes are 490 characters. Full description is 3,576 characters; short description is 67.
- Release summary date, issue template and security version references match 2.3.0. Website downloads identify the actual published APK version, which can differ until 2.3.0 is published. Trakt's User-Agent reads the actual BuildConfig version.
- All reviewed local work is included: Trakt browser sign-in/default and device-code fallback, website/legal/network disclosures, signing configuration, showcase source/previews, and the experimental web client/server work.

## Web Companion feature flag

WEB_COMPANION_ENABLED defaults to false. Release builds keep false regardless of the debug opt-in. AppFeatureFlags also requires BuildConfig.DEBUG, the settings category is filtered out, direct navigation returns to its caller, and WebCompanionManager refuses server startup. There is no user-facing switch to reveal the feature.

For development only, build the separate debug application with:

```sh
./gradlew :app:assembleDebug -Pcobalt.enableWebCompanion=true
```

A normal debug build also hides it. The existing web instrumentation workflow needs the same explicit opt-in. Release builds use the normal assembleRelease task and have no web server entry.

## Screenshots

The fifteen approved 1080 × 1920 panels, banners, panorama and gallery preview are synchronized to README, website and Fastlane/F-Droid destinations. Publication copies match the source exports byte for byte, with no obsolete screenshot in those galleries.

These are genuine captures from earlier pre-release builds. Their historical capture provenance is preserved; they were not repainted or relabelled as a freshly tested 2.3.0 APK. Refresh any visible version text after the final user-built APK if needed.

```sh
python3 design/showcase/build_assets.py --publish-only
python3 design/showcase/build_assets.py --check
```

## Verification and remaining work

- 12 client regressions passed.
- 21 isolated Chromium checks passed, including real capture scrolling, sticky formatting toolbar, selection/undo/redo, failed-save reload recovery, all native folder colors/icons, duplicate validation, pin assignment, imports and narrow layout. No JavaScript page errors.
- Native folder choices are generated from FoldersScreen.kt. The web dialog follows FolderBottomSheet.kt, including the card preview, frameless name, pin switch and scrollable circular palettes. Kotlin folder routes now persist the full palette and native pin setting; their updated host tests are included.
- Version, bundled changelog, F-Droid and Fastlane limits, feature-gate source checks, screenshot parity and git whitespace checks passed.

No Android/Gradle build, APK installation or new device test was run for these final changes, as the user is rebuilding Android. The updated Kotlin folder tests and feature flag still require that build/runtime verification. Earlier dated reports retain their actual 2.2.3/19 test versions and do not validate the final APK.

## Release notes and APK publication

The installed version's notes come from the APK's bundled CHANGELOG.md. Published history comes from the official GitHub Releases API, using each release's immutable identity and published body rather than the changelog on main. A shared schema-versioned AtomicFile cache stores raw responses, timestamps and ETags; automatic history reads reuse fresh data for six hours. Offline reads retain history. Failed refreshes retain the last checked timestamp and display a retry error; they cannot confirm an APK or report a successful update check. Update checks remain opt-in, and manual refresh/download explicitly contacts GitHub.

Download actions use only the expected uploaded asset for the selected stable version. Releases from 2.3.0 use cobalt-vVERSION.apk; older releases offer labelled FOSS/Play choices. Clicking an APK forces a fresh metadata check and rejects assets that have changed or disappeared. Release pages are separate links, and missing assets show Download unavailable.

The release workflow extracts exactly the 2.3.0 notes and requires root/bundled parity. It verifies the built APK's package, version name/code, non-debuggable manifest and official F-Droid signing certificate, then uploads the APK and SHA-256 checksum to a draft. It validates uploaded metadata and downloads the uploaded APK through the authenticated asset API to compare every byte before publishing. A completed release triggers Pages deployment, which resolves current published asset URLs before packaging the static website. The browser itself makes no new GitHub API requests.

Targeted release tests and validation are recorded in [verification/release-notes-2026-11-11.txt](verification/release-notes-2026-11-11.txt). Android UI compilation/rendering, real APK signing/upload and hosted Actions execution remain unverified locally; publication-script APK fixtures use injected SDK outputs, not a real signed build.

## Git and publication

The integration includes the feature/local-web-companion history and all reviewed local changes, merged into local main. The feature branch remains available for further web development. Before the release-feed commit, main pointed to 7465c48 and reported 534 commits ahead and 516 behind origin/main, with no merge base found. The user confirmed that a separate author/email history rewrite caused this divergence. This release-feed work did not rewrite or reconcile branch history; its changes are included in a separate local commit.

No remote publication is part of this preparation. A main push containing the app/build.gradle.kts version change triggers .github/workflows/build-release.yml, which builds, verifies, uploads and then publishes v2.3.0. Build and test the final Android application and review the diverged branch history before a push. No v2.3.0 tag is created locally by this preparation.
