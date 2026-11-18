# Screening card · Figma reconciliation

Completed 2026-11-14 in `dJOugcd2V82nQUA1MjyINI`, on **Concepts · Android home widgets** (`77:915`).

The chosen Screening card is **Artwork A / Floating feedback**, master `224:2210`. It now has an approved showcase and separate reusable component shelf. Earlier directions are preserved in a concepts archive on the same page.

- [Approved Screening design](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=309-5020)
- [Approved component shelf](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=309-5169)
- [Concepts archive](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=309-5180)

All **12 active card previews** reference the chosen master and retain artwork at **336 × 264**. Seven previews were migrated from the retired poster/details card, including setup, source, feedback, information, and the Your taste prototype copies. The showcase includes neutral, liked, disliked and tracked-playback states. The master exposes Title, Metadata and Show progress; progress defaults to hidden.

The archived source groups retain their original IDs: `77:1833`, `77:1214`, `217:1729`, `217:1733`, `224:2082` and `224:2085`. The only remaining instance of the retired master is `77:1851`, inside the archive. The archive is arranged in two columns at **3120 × 2809**. Other widget concepts remain in their existing board.

The gallery now marks Screening as available, opens the current setup prototype, and describes the full-artwork card. Active helper text describes opening the artwork and reversible thumbs. Seven status rows were repaired to keep the battery text within their bounds.

Android was cross-checked in `ScreeningRenderer.kt`, `ScreeningWidgetProvider.kt` and `screening_widget.xml`. The provider uses the renderer that explicitly cites Figma `224:2210`: full artwork, a 28 dp radius, a 108 × 56 dp floating pill, two 48 dp touch targets, and an optional 3 dp playback edge inset 28 dp. Progress requires valid tracked playback. **No Android source changes were needed in this reconciliation pass.** No ADB device was connected, so this was a source cross-check.

Validation passed: active-card references and artwork; six feedback variants and preserved prototype destinations; editable component/text/vector structure; status-row bounds; archive membership; visual reviews of the approved showcase, setup screens, information dialog, gallery and compact archive. The construction script passes an AsyncFunction syntax check.

Reproduction source: `design/widgets/approve-screening-artwork-figma.js`. It includes the final archive arrangement and copy/status repairs. **Do not rerun it wholesale in this file:** its existing-board guard requires targeted edits after inspection.

State ledger: `/tmp/design-system-state-screening-approved-20261114-1dd6e7d6.json`.
