# Cobalt 2.3.0 release preparation — 2026-11-11

The next release is **2.3.0 / version code 20**. GitHub currently lists v2.2.2 as the latest published release. The unpublished 2.2.3/code-19 changes are consolidated into 2.3.0; there is no separate 2.2.3 release entry or Fastlane changelog.

## Release contents

- Root and bundled changelogs match, with highlights followed by the full development record since 2.2.2.
- F-Droid metadata targets v2.3.0/code 20, and its build recipe expects the v2.3.0 tag created at publication. Fastlane code-20 release notes are 443 characters. Full description is 3,576 characters; short description is 67.
- Release summary date, website download label, issue template and security version references match 2.3.0. Trakt's User-Agent reads the actual BuildConfig version.
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

## Git and publication

The integration includes the feature/local-web-companion history and all reviewed local changes, merged into local main. The feature branch remains available for further web development.

No remote publication is part of this preparation. Pushing main with the changed app/build.gradle.kts triggers .github/workflows/build-release.yml, which builds a signed APK and automatically creates a public v2.3.0 GitHub release. Build and verify the final Android application before that push. No v2.3.0 tag is created locally by this preparation.
