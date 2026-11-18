# Screening UI redesign · validation · 14 November 2026

The dense Why this pick?, saved-title search and Your feedback screens have been redesigned in Android and the existing Cobalt Figma file. The presentation uses Cobalt's shared settings header, serif headings, rounded surfaces, consistent spacing and compact rows. The approved full-artwork Screening widget with floating thumbs remains the selected card.

## Delivered behavior

- **Why this pick?** shows the title, up to three reasons derived from the actual scoring features, and a compact ranking card. Contributions and the retained publication explanation open separately.
- **Your feedback** opens on Active. Rows show the title and reaction; provenance, date, current influence and correction controls open in a sheet. All feedback includes ignored, cleared and replaced evidence.
- **Saved titles** has a filled search field, thumbnails or format icons, eligibility badges, filters, clear-search and useful empty states.
- **Your genres** keeps five leading genres and expandable Others. Individual genre learning is retained. Learning numbers open behind Learning details.
- Ignore/restore and clear-thumb controls remain reversible. Reset still requires confirmation and preserves saved ratings, watch progress and notes.
- Back restores the prior selected title and search after state recreation. Back to genre and Close details have separate behavior.

Android source: [screen](../app/src/main/kotlin/com/example/ui/screens/ScreeningTasteScreen.kt) and [components](../app/src/main/kotlin/com/example/ui/screens/ScreeningTasteComponents.kt).

## Emulator verification

The current application and test APKs built successfully. **Eight UI instrumentation tests passed without manual input**, with zero failed or ignored cases. The clean run completed at 22:41:45 PKT, after starting at 22:40:24.

The checks cover five + Others and evidence, correction labels and reset confirmation, actual score totals and recorded publication, search/filter/empty states and unsupported formats, error recovery, navigation restoration, sparse undated feedback, and light/dark layouts with enlarged text.

The large-text test uses a real swipe after changing font scale: the semantic lazy-list scroll driver could loop while the shared collapsible header remeasured. The original run required a manual swipe; it is retained in the log as diagnostic history. The rebuilt unattended eight-test run passed.

A separate **60-genre device test passed in 10.019 seconds** with the entire emulator window temporarily set to **320 × 712 dp** (960 × 2136 px at 480 dpi), at **1.6 font scale**. It reached the final genre, its learning details and rating correction controls. The original 1344 × 2992 px size was restored; density stayed 480 dpi.

Fixtures exercise the presentation without adding ratings or reactions to the user's archive. The live Why, saved-title search and feedback screens were also inspected in Cobalt.

| Evidence | Artifact |
| --- | --- |
| Both UI runs and whole-window check | [Instrumentation log](../design/screening-insights/redesign-20261114/device-checks/instrumentation-logcat.txt) |
| Live Why screen | [Screenshot](../design/screening-insights/redesign-20261114/07-why-live.png) |
| Live saved-title search | [Screenshot](../design/screening-insights/redesign-20261114/08-search-live.png) |
| Live feedback layout | [Screenshot](../design/screening-insights/redesign-20261114/09-feedback-live.png) |
| Fixture feedback in the light theme | [Screenshot](../design/screening-insights/redesign-20261114/device-checks/feedback-light.png) |
| Score disclosure | [Screenshot](../design/screening-insights/redesign-20261114/device-checks/score-sheet-light.png) |
| Full-window 320 dp final genres | [Screenshot](../design/screening-insights/redesign-20261114/device-checks/whole-window-320dp-evidence.png) |

Tests: [UI flows](../app/src/androidTest/kotlin/com/example/ui/screens/ScreeningTasteUiTest.kt) and [60-genre device check](../app/src/androidTest/kotlin/com/example/media/recommendation/ScreeningTasteDeviceTest.kt).

The shared settings header can ellipsize long subtitles at enlarged text; body text and sheet controls wrap and remain accessible.

## Figma verification

[Current Android design](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=293-2528) has seven preview phones: overview, Why, feedback, genre evidence, first feedback, Others and saved-title search. Current, ignored-rating and reset prototype states were updated.

The final review checked 553 prototype actions across 121 scoped roots. Navigation, overlay and swap destinations resolve to valid top-level frames. No horizontal child overflow was detected. The main board contains 205 editable text layers, 142 component instances and 298 vectors, with zero image-filled UI nodes. Product text uses Noto Serif and Roboto; the inherited system status text is separate.

The final composition was visually inspected after fixing navigation sizing and preserving invisible icon-wrapper fills. The work-in-progress placeholder is removed. Dense components and superseded correction/modal concepts remain in archives on the same page.

[Final composition](../design/screening-insights/redesign-20261114/figma-final.png) · [Structural validation](../design/screening-insights/redesign-20261114/figma-final-validation.json) · [Handoff](screening-your-taste-figma-handoff-2026-11-14.md).

## Installed artifacts and limits

The installed application APK hash matches the workspace build:

- Application: `5932592fc14ce058bb9a8dac1ff4ebad720648396f5512745834c580086311ad`
- Android test APK: `587b5d6fbb9de581d843d83142a97d6846b60e1698ea287d51bd72a68916720b`

App: `com.hanan_bhatti.cobalt.debug`, on `emulator-5554`. Entry: Settings → Appearance & Preferences → Home Screen Widgets → Screening Card → Your taste.

The earlier 88 host checks and eight algorithm/device checks in the [algorithm report](screening-algorithm-validation-2026-11-14.md) are historical baseline results. This report records the new presentation checks. Recommendation usefulness and enjoyment have not been measured by this UI pass.

