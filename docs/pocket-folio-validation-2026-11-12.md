# Pocket Folio implementation and validation

Pocket Folio is implemented and installed as a debug build on `emulator-5554`. The remaining four widget designs appear as “Coming next” in the Home Screen Widgets gallery, as agreed.

## Behavior

- Home Screen Widgets opens the five-design gallery, then the dedicated Pocket Folio screen using Cobalt's existing navigation, settings groups and bottom sheets.
- Live preview and launcher use the same Canvas renderer, canonical icons and supported app fonts. Each confirmed widget has independent folder, color override and item order. A horizontal pager manages placed Folios and the New Folio draft.
- Folder picker shares `FolderSearchField` with the existing move-to-folder flow: rounded 14dp corners, matching search/clear icons, colors and typography. Inline creation reuses `InlineNewFolderCreator`.
- A nonblank folder color takes precedence. When the folder has no color, the stored Folio override applies; otherwise the widget has its own gold appearance. This never assigns a default color to the folder. Selecting a colored folder does not erase the override.
- Light/dark appearance follows the app theme. Custom font preferences apply only when custom fonts are enabled and applied to app UI; note-only font changes do not affect widgets.
- Header/footer opens the selected folder. The two note rows open their respective items. Empty folders offer capture with the folder selected. Removed folders show a useful replacement state.
- Settings changes save automatically per widget. Launcher configuration stages edits and commits only on Save; Back cancels. Placement requires the launcher's confirmation. Unsupported pinning shows manual placement instructions.
- The previous Quick Capture and Recent Items widget source, providers, loading layouts and runtime Glance dependencies were removed. Historical changelog and unused catalog aliases remain.

## Loading reliability

The widget publishes native RemoteViews with cached or useful empty content before querying data. Durable WorkManager refreshes retry failures; data queries have an eight-second timeout. A failed query preserves the last good snapshot instead of publishing an empty folder. Snapshots contain at most two item IDs/titles, folder metadata and the total count, without note bodies. Bitmap dimensions and font caching are bounded.

The previous Glance path fetched note data and serialized its cache before publishing widget content, while refresh failures could be swallowed. That is a plausible contributor to the reported loading failure, not a verified diagnosis of the user's physical device.

Actual emulator testing also found and corrected:

- Migration state `COMPLETE` with `migration_required=true` could leave background widget work paused despite offering “Open Cobalt.” Required migration now returns to READY; regression coverage verifies this.
- Canceling placement could change pager size during rendering and crash with `IndexOutOfBoundsException`. Pager key/body lookups now use current pages with bounds checks. Unconfirmed host allocations do not appear as placed Folios.

Native RemoteViews suits this small, fixed widget and provides broad launcher compatibility. The shared bitmap renderer preserves the approved geometry and local fonts. Compose remains inside app settings. Launcher animation performance and RAM/CPU consumption were not benchmarked.

## Verification

Environment: Android 17/API 37 emulator, 1344×2992 pixels, density 480. Debug package `com.hanan_bhatti.cobalt.debug`; release package was left installed.

| Check | Result |
| --- | --- |
| Final `:app:assembleDebug` | Passed; `/tmp/cobalt-folio-delivery-build.log`, 2m59s |
| `:app:testDebugUnitTest` for FolioTest and NoteMigrationGateTest | 18 tests, zero failures/errors |
| `git diff --check` | Passed |
| Gallery, rounded folder search, inline creation, live preview | Visually inspected |
| Native placement cancel and confirm | Passed; no new app crash |
| Two placed Folios and swipe between them | Different folder/color/order persisted independently |
| Empty folder capture, save, one/two-note refresh | Passed on launcher |
| Header, launcher note tap and preview note tap | Correct folder/item opened |
| Actual folder color versus stored override | Folder color updated preview/launcher; override remained stored |
| Kill app process | Published widget stayed visible; reopening refreshed successfully |
| Folder deletion | Replacement state appeared; surviving Folio unchanged |
| Launcher reconfiguration and Back | Opened correctly; cancellation preserved current config |
| Launcher removal | Extra widgets removed; placed list returned to one Folio |

Host tests cover stable filtering/order, independent widget IDs, folder rename/delete, pin expiry/callback, canceled allocations, database failure/timeout cache preservation, RemoteViews inflation, JSON folder names, folder-color precedence, theme/font gating and visual fixtures. A direct shell `APPWIDGET_UPDATE` broadcast was denied by Android's protected-broadcast policy; refresh was verified through app lifecycle/data changes instead.

The physical-device loading failure remains unverified on the user's phone. OEM launchers, older Android versions, device restore/reboot and sustained performance need device testing; emulator validation does not certify those cases.

## Artifacts and test data

- [Approved editable settings design](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=92-2204)
- [Original five widget concepts](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=77-1320)
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- JUnit results: `app/build/test-results/testDebugUnitTest/TEST-com.example.widget.FolioTest.xml` and `TEST-com.example.notes.NoteMigrationGateTest.xml`.
- [Launcher with two notes](../design/widgets/validation/folio-launcher.png), [independent Folio settings](../design/widgets/validation/folio-settings.png), [folder color precedence](../design/widgets/validation/folio-folder-color.png), [widget after process kill](../design/widgets/validation/folio-after-process-kill.png), [folder removed](../design/widgets/validation/folio-folder-removed.png).

One working Folio remains on the debug emulator with the uncolored test folder `Folio_Test_Ideas` and two clearly named test notes. The empty `Folio_Test_Work` folder, its widget and the earlier unconfigured placement were removed. No original archive items were deleted. The original Hide content in Recents preference was restored to enabled after screenshots.

Figma settings layers remain editable; no interactive prototype was created. Canonical icon masters, welcome artwork and logo concepts were preserved during widget work.
