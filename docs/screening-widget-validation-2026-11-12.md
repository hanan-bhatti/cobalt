# Screening Card and shared widget settings validation

The selected artwork widget and movie playback integration are now built and verified. See [13 November validation and APKs](screening-widget-validation-2026-11-13.md) for the current implementation; the sections below record the earlier settings and concept work.

## Delivered

Screening Card is implemented as a native launcher widget with independent per-card source settings and pick cycles. The preview and launcher share the Canvas renderer. View details opens the saved title; Next advances without recording a dislike. Unsupported RemoteViews Space elements were replaced with accessibility-hidden TextViews. Configuration cannot save a widget removed during setup.

The latest settings edits remove Screening from Media & Providers, its transient setup loading bar, its separate Why this pick and Appearance sections, and the verbose instruction beneath the preview. Screening Card and Pocket Folio now use the existing SettingsSubsectionHeader with a small heading, brief description, canonical info icon and dismissible information dialog.

## Figma review

- [Screening setup, review, sources, feedback and optional morning page](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=142-1965)
- [Pocket Folio and matching shared tooltip states](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=145-2595)

The first board contains five editable views. Feedback is three plain rows in a bottom sheet. Sources contain Everything, Movies, TV shows, Anime and Manga, with text-only category rows. Theme variables, existing settings instances and canonical icon components are reused. Fonts are Roboto and Noto Serif for settings, plus Source Serif 4 for the widget title. The only raster fills are discrete title artwork; full screens are editable layers. Both composition screenshots were inspected and text sizing, sheet anchoring and content spacing were corrected.

The second board shows Pocket Folio using the same header component, plus the information dialog for each widget. Screenshots and node inventories are under design/widgets/validation/.

The redesigned feedback/navigation flow and source selection are implemented, as detailed below. The current ranking engine still normalizes anime to TV and excludes manga; widget source filtering distinguishes TV from Anime. Manga chapter/volume progress must not be fabricated from episode data. Existing watching/reading progress belongs in title details.

## Verification

The completed widget feature passed 63 targeted host tests with no failures or errors: recommendation engine 20, learning store 10, schedule 5, Screening widget 10, Folio 11, note migration gate 7. The Android instrumentation run passed five tests, including real bound RemoteViews, PendingIntent isolation/idempotence and dynamic palettes. Logs: /tmp/cobalt-screening-final-validation.log and /tmp/cobalt-screening-final-device-results.log.

Launcher cancellation and successful native placement were checked. The temporary widget-binding permission was revoked after device testing. Test fixtures were cleaned up; a later instrumentation cleanup refresh was added after that run.

The latest settings removal and both shared-header edits passed :app:assembleDebug and were installed on emulator-5554. Build log: /tmp/cobalt-screening-folio-headers-build.log. The running Screening setup shows the new small heading, description and info button, without the removed sections. Its tooltip opens with the intended copy and dismisses with Got it. Pocket Folio was checked separately with the same component. The earlier feature tests were not rerun for these presentation-only changes.

## Implemented UI revision

The approved setup/review/feedback/morning hierarchy is now implemented. Pocket Folio and Screening use the same collapsible settings page/header as Appearance. Source rows are text-only, with Everything plus Movies, TV shows, Anime and Manga. TV and Anime filters are distinct. Feedback is three plain bottom-sheet rows; watching/reading status stays in title details. Morning questions and reset controls have their own page. Existing ranking weights and eligibility are unchanged; Manga ranking remains for the next algorithm pass.

Final UI build passed 23 widget host tests and five Android tests. The source sheet, large/collapsed Folio header, review/feedback sheet and morning page were checked on emulator-5554. Configuration selection of Anime/Manga was tested in a staged setup and canceled; the placed card’s original ALL source and appearance preferences were preserved. Temporary widget bind permission was revoked.

Built APK: build/Cobalt-widget-ui-20261112-debug.apk. SHA-256: ca5f3cd4a099ef69de69f25446c704fcf1d2dc8ecb9af9b8fe07cf9382b581a2. The APK is installed on the emulator. Logs: /tmp/cobalt-widget-redesign-build.log and /tmp/cobalt-widget-redesign-device-tests.log.

The user subsequently requested a new launcher-card design after testing its excess side gaps and two large buttons. [Four editable launcher concepts](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=217-1729) are now available for selection before changing the launcher renderer. The gap comes from fitting the fixed 316×300 renderer inside the host and adding horizontal centering padding when height constrains scale. Each concept fills the available width and replaces the two large navigation buttons.

The comparison board and component shelf contain 102 editable text nodes, 68 component instances and seven local main components. The only four image-filled nodes are two artwork regions and their instances; no full-screen raster is used. Fonts are explicitly validated as Source Serif 4 and Roboto. Canonical controls reuse Sync, More options, Thumbs up and History. The comparison screenshot was visually inspected, then launcher dock placement was corrected consistently across all four screens. Evidence: design/widgets/validation/screening-launcher-concepts.json and screening-launcher-concepts.png.
