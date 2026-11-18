# Screening widget and movie playback validation — 13 November 2026

The selected Artwork A widget and movie playback integration are implemented and verified. This completes the interrupted widget build/testing work recovered from widget-chat-session.md. The planned recommendation algorithm work, including Manga ranking, remains separate.

## Current behavior

- The launcher card fills its host width, with adaptive artwork cropping and no added horizontal centering padding. Tapping the card opens its saved title. The two large navigation buttons are replaced by two 48 dp feedback targets.
- Like and Not for me switch or clear a local preference. The current card remains visible so the choice can be reversed. These preferences do not mark a title watched or write a Trakt rating.
- Artwork uses a lighter accent when the light-theme primary color has insufficient contrast against the dark footer. The same accent is used for feedback and the progress edge; a selected artwork thumb has dark ink on its filled circle. This corrects the dim controls found in the release preview.
- A saved partial Trakt movie or episode position adds one 3 dp accent edge. No progress labels are added to the widget. Missing, invalid, zero, finished, or other-account positions produce no edge.
- Movie details show the saved fractional percentage and progress bar. Unknown playback removes that area. Watching status, runtime and watched history cannot create a playback position.
- Complete movie/episode snapshots commit atomically to a separate Room cache. Partial failures preserve the last complete snapshot; successful empty responses clear it. Login replacement and disconnect hide prior-account progress.
- The approved settings hierarchy, shared Folio/Screening headers, text-only source sheet and optional morning page are retained.

The selected design is [Artwork A in Figma](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=224-2210). The local comparison is [screening-artwork-feedback.png](../design/widgets/validation/screening-artwork-feedback.png).

## Verification

All **116 host tests passed**, with no failures, errors or skips:

| Coverage | Tests |
| --- | ---: |
| Screening recommendation engine, learning store and schedule | 35 |
| Screening widget and Pocket Folio | 27 |
| Movie playback repository, Trakt sync/disconnect/episode catalog and migrations | 37 |
| Movie/media controls | 10 |
| Note migration gate | 7 |

The host run completed the debug and Android test APK builds. Final release/debug/test APK assembly passed after the artwork contrast correction. [Host build log](../build/screening-progress-validation-20261113/host-build.log) · [Host test summary](../build/screening-progress-validation-20261113/host-tests.json) · [Successful final assembly](../build/screening-progress-validation-20261113/release-build.log).

All **6 Android tests passed** on emulator-5554: three notification/learning/status tests, real bound widget thumb actions, wallpaper palettes, and the movie UI transition from 35.5% to unknown playback. Temporary test widgets and fixture records were cleaned up, and the temporary widget-binding grant was revoked. The existing notification permission was preserved. [Device log](../build/screening-progress-validation-20261113/device-tests.log) · [Device summary](../build/screening-progress-validation-20261113/device-summary.json).

Both existing launcher widgets survived a cold emulator restart. The actual Screening card fills the available width; its two targets measure 144 px at density 3, or 48 dp. A card tap opened the correct saved fictional show. The optimized release APK was installed and its Screening setup opened successfully with a saved movie. Installed APK hashes match the delivered artifacts. [Cleanup/source check](../build/screening-progress-validation-20261113/widget-cleanup.json).

- [Actual launcher after restart](../build/screening-progress-validation-20261113/launcher-final.png)
- [Title opened from the launcher card](../build/screening-progress-validation-20261113/widget-open-details.png)
- [Movie progress device fixture](../build/screening-progress-validation-20261113/movie-details-progress.png)
- [Release Screening setup](../build/screening-progress-validation-20261113/release-screening-setup.png)

The release preview exposed dim light-theme icons over artwork. The corrected rendering increases representative glyph/background contrast from 2.69:1 to 8.33:1, while retaining the accent hue. The 116 host tests and six Android tests were rerun after the correction. [Before measurement](../build/screening-progress-validation-20261113/contrast-before.json) · [Final measurement](../build/screening-progress-validation-20261113/contrast-after.json).

Initial builds were interrupted during environment/resource changes. The final assembly passed with one worker and a bounded JVM; both delivered packages were signature-verified and their installed hashes checked. The emulator used the same app data in a headless restart because its desktop display was unavailable.

## APKs

Both are Cobalt 2.3.0, version code 20. APK signature verification passed. The release artifact retains the compiled Screening provider/layout and widget classes.

- [Release APK](../build/screening-progress-validation-20261113/Cobalt-widget-20261113-release.apk), 8,337,034 bytes. SHA-256: cd91ca870ed6b979ed03a372ad22a5229a308b6343e82b2e53115590446cca04.
- [Debug APK](../build/screening-progress-validation-20261113/Cobalt-widget-20261113-debug.apk), 31,549,541 bytes. SHA-256: e344d3aa3781cb4d050676d4b3b38f4f63e025ed5a4dd283f0fc7a219757220e.

[Artifact and source identities](../build/screening-progress-validation-20261113/artifact-identity.json) record the current main worktree and the verified widget/progress files. Existing workspace changes are included in these APKs.

## Remaining algorithm scope

Manga is selectable in the source UI but is still excluded by the current ranking engine. Anime uses its existing TV eligibility rules. Manga ranking and further use of the new thumb preferences belong to the next algorithm pass requested after the widget design work.

Live account playback was not required for these tests: repository behavior used isolated API fixtures, and the device movie UI used 35.5% fixture data. Real bars require a saved position reported to Trakt by a connected player/scrobbler. The release was checked for installation and settings rendering; the six instrumentation tests ran against the debug package.
