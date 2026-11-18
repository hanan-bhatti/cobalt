# Notes & folders Settings · implementation validation

Built and installed the redesigned Notes & folders subpage on `emulator-5554` using the debug package `com.hanan_bhatti.cobalt.debug`. The page extends the approved Settings system with a compact folder-order example, the existing suggestions switch, an explanation sheet, and an archive-view choice sheet.

The example uses the real Cobalt folder icon resolver and preset colors: Work/Royal Indigo, Ideas/Soft Gold, and Reading/Emerald Sage. The app’s book option supplies the Reading glyph. Folder identities remain attached when the example switches to alphabetical order. `Example` and its accessibility description distinguish these illustrative folders from saved data.

Copy follows the actual ranking engine: item count, capture timestamps, content and item-type matches, pinned folders first, and alphabetical order for remaining folders when suggestions are off. The archive selector names its current choice and says where it appears. No “Changes apply automatically.” text is present in the Settings sources.

## Build and behavior

`./gradlew :app:testDebugUnitTest --tests 'com.example.ui.screens.NotesAndFoldersSettingsScreenTest' --tests 'com.example.util.FolderRankingEngineTest' --tests 'com.example.ui.components.HeatmapSummaryTest' :app:assembleDebug --console=plain` passed.

| Check | Result |
| --- | --- |
| New Settings UI tests | 4 passed |
| Existing folder-ranking tests | 4 passed |
| Existing heatmap-summary tests | 3 passed |
| Suggestions switch and example order | On/off state, alphabetical fallback, and saved preference verified |
| Help sheet | Rules, close behavior, Back cancellation and completion verified |
| Archive sheet | Native selection, dismissal, and Back cancellation verified |
| Actual Settings overview | Both Activity heatmap and Category totals verified; total remained 517 saved items |
| Persistence | Both preferences verified after a cold start |
| Page Back | Cancellation retained the page; completion returned to Settings |
| Light/Dark | Page and archive sheet visually inspected |
| Compact width | 340dp with font scale 1.3; switch, help, and both choices reachable |
| Landscape | Font scale 1.3; controls reachable and the full explanation accessible by scrolling |
| Restoration | Original preference values and absence of the ranking key verified on disk and in the UI; display configuration restored |

Navigation, `MainActivity`, `ScreenWrapper`, transitions, shared sheet components, and the tick/cross switch match this task’s starting hashes. The existing collapsing header is reused; its compact state was inspected while scrolling. Unrelated work was preserved. No commit or staging was performed.

Evidence comes from a phone emulator at 1344 × 2992 px and density 480, plus temporary compact-width and landscape configurations. These checks establish the rendered behavior on that emulator; hardware frame timing was not measured. Detailed results and the APK fingerprint are in [checks.json](validation/checks.json).

## Design and captures

[Native Light/Dark preview](preview.png) · [Editable Figma screens](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI/Cobalt?node-id=466-20299) · [Figma sheet states](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI/Cobalt?node-id=472-20467)

The Figma screens reuse the approved header, switches, archive icons, native sheet structure, and semantic app/folder variables. New SVG geometry comes directly from current Android drawables, recorded in `assets.json`. Figma’s warm theme values and the emulator’s wallpaper palette resolve the same app roles independently.

| Evidence | Captures |
| --- | --- |
| Dark, suggestions off, and sheets | [Dark states](validation/native-dark-review.png) |
| Light and choice sheet | [Light states](validation/native-light-review.png) |
| Larger text on a compact phone | [Compact states](validation/native-compact-review.png) |
| Landscape and scrollable help | [Landscape states](validation/native-landscape-review.png) |
| Predictive Back cancellation | [Back states](validation/native-back-review.png) |

The scoped direction is in [direction.md](direction.md). A fresh native finish review is recorded separately in [finish-review.md](finish-review.md).
