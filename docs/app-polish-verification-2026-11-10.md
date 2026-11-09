# Cobalt app polish verification — 10 November 2026

Scope: survey redesign, six improvements to existing behavior, and smaller share-card text on `cleanup/cobalt-single-app`. App version remains **2.2.3 / code 19**. These changes do not introduce backup, web companion, biometric locking, or encrypted notes.

## Changes

| Change | Result | Verification |
| --- | --- | --- |
| Survey | Two short steps using existing icons, theme colors and rounded surfaces. Both reaction and recommendation use optional 1–10 sliders, with Clear actions. Other answers are unselected by default. Direct **Send via email** opens plain-text feedback addressed to the existing feedback email; no save-to-device step or claim of successful delivery. | Response/intent tests, interactive Compose tests, light/dark renders, signed release build. Final device results recorded below. |
| Migration wording | Describes note-storage verification accurately and displays cumulative verified/total counts. Avoids promising a separate Markdown file for each note. Startup dismissal explains returning to the launcher. | Migration gate tests; manual page showed **320 of 320 notes verified**. Startup text reviewed in source; no new downgrade/reseed performed during this polish pass. |
| Deletion actions | **Move to Trash** when Trash is enabled; **Delete permanently** when disabled. Reader, capture, home, folders and recovery labels agree with the setting. Trash-off explanation includes the existing 30-second Undo window. | Policy/Trash tests; both runtime dialogs opened and cancelled. Trash restored to enabled; no fixture deleted. |
| Storage/recovery wording | Explains eligible cache and older saved versions, current-content/draft exclusions, irreversible removal of older versions, and resetting saved Undo/Redo. Removes absolute safety guarantees. | Storage and draft-recovery pages inspected; cleanup algorithms unchanged. No destructive cleanup run. |
| Reader feedback | Adds explicit loading, loading-more and empty-note feedback while preserving the existing bounded reader/session behavior. | Reader session tests; 100,000-byte and 399,995-byte notes displayed content on the emulator without visible render errors. No measured speed/FPS improvement claimed. |
| Share excerpts | Shortened Markdown keeps fence boundaries and table headers/complete rows, adds continuation indicators, and preserves Unicode. Compact cards prefer real prose/quotes over code. | Excerpt tests and card composition checks; Document, Minimal and Compact visited. Compact displayed the fixture's final prose rather than fenced Python code. |
| Navigation labels | Consistent Saved notes, Draft recovery, Saved history and TV shows labels. Find in note is directly accessible from the reader toolbar and note actions. | Runtime labels checked; searching `END` in the mixed Markdown fixture returned `1/2`, reflecting two matches. |
| Share typography | Smaller body text and longer budgets: Document 9sp, Minimal 9.5sp, Compact 12sp; Document title 13sp. Compact allows up to six lines. Link, image and movie card text is reduced consistently. | Card composition checks and emulator preview inspection; chosen fonts/line-height ratios retained. |

## Build and test evidence

The final slider revision passed **77 targeted tests**, with zero failures, errors or skips. This includes eight survey UI cases covering default answers, endpoints, a physical tap at the first position, Clear, navigation, text input, direct email and real light/dark rendered images. Production-signed `:app:assembleRelease` and release lint passed (22m 56s). A subsequent asset-only build passed (1m 6s) after synchronizing the in-app changelog; Kotlin compilation and R8 were up to date on that pass.

| Suite | Passed |
| --- | ---: |
| SurveyResponseTest | 6 |
| SurveySheetContentTest | 8 |
| NoteShareExcerptTest | 8 |
| NoteMigrationGateTest | 6 |
| TrashManagerTest | 10 |
| TrashPolicyTest | 2 |
| MarkdownReadSessionTest | 7 |
| NoteShareCardScreenshotTest | 8 |
| LinkShareCardScreenshotTest | 9 |
| ImageShareCardScreenshotTest | 7 |
| MovieShareCardScreenshotTest | 6 |

Suites cover survey data/email intents, survey interaction/rendering, note excerpts, migration gate, Trash policy/manager, Markdown reader sessions, and note/link/image/movie card composition. Legacy card screenshot calls did not produce recorded PNGs; they establish successful composition, not a pixel regression comparison. The survey rendering cases explicitly record images and assert nonempty output.

## Installed app and retained data

The final slider revision was installed successfully in place with `adb install -r` on **emulator-5554**, Pixel 10 Pro XL, Android 17. Production certificate SHA-256 was verified before installation:

```text
9af192bc1865c315591539bd4022e69e6a35e597384b846d3b3f272e74c6e2cb
```

After the final slider APK update, an inspector compared the whole item rows and note/media hashes with the pre-polish baseline: **500 items**, **320 note bodies**, **70 local media files**, and **two folders** remained identical. Database schema stayed 15; the TMDB credential digest matched. No credential value is included in this report. Installed version was verified as **2.2.3 / code 19**.

Fixture types: 300 text, 20 code, 100 links, 50 images, 10 video, 10 audio and 10 media-tracking items. There are 17 archived items (483 on Home).

## Release metadata

`CHANGELOG.md` and `app/src/main/assets/CHANGELOG.md` are identical, and the APK's bundled changelog was checked against that content before installation. They now include the six polish changes, share typography, revised survey and verification limits instead of the obsolete survey-revision warning.

F-Droid/Fastlane `en-US/changelogs/19.txt` and `full_description.txt` describe the revised survey, smaller share text and existing capabilities without claiming the deferred web/backup/encryption/biometric features. Length checks passed: changelog **482**, full description **3,361**, existing short description **67** characters. Version remains 2.2.3/code 19. Store screenshots are unchanged pending the user's screenshot work; metadata has not been published.

## Email and verification limits

The direct email button opened Gmail on the emulator. Gmail stopped at its Welcome/setup screen, so an actual populated composer and delivery could not be verified there. Unit tests verify the existing recipient, Unicode-safe subject/body encoding, plain-text formatting, and failure behavior. Opening a mail app neither marks the survey submitted nor writes a local response file. No email was sent by automation.

These checks are targeted verification of the changed areas, not an exhaustive test of every app feature or every device. No forced crash, destructive cleanup, or fresh startup migration was performed in this polish pass. The existing version-comparison/migration reports remain separate.

## Final slider verification

The installed release passed physical device interaction checks for **1 and 10 on both sliders**, initially unanswered ratings, and both Clear actions. A fresh survey had no selected choices. Reaction labels changed to Very poorly at 1 and Very well at 10. The direct email button opened Gmail; delivery remains unverified for the setup limitation above. The final retention comparison passed with no changed item fields.

Sanitized evidence: [test results](evidence/app-polish-2026-11-10/test-results.json), [slider interaction](evidence/app-polish-2026-11-10/slider-runtime.json), [retention](evidence/app-polish-2026-11-10/retention.json), and [APK identity](evidence/app-polish-2026-11-10/apk-identity.json). Survey test renders are generated at `app/build/reports/survey-ui/`; emulator review images remain local test artifacts, separate from the deferred store screenshots.
