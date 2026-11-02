# Migration and recovery UI redesign — 2026-11-03

Source changes only. **No Gradle build, compilation, unit tests or APK installation
was run for this redesign**, as requested. Prior APK results do not validate this
source revision. The user's screenshots show an intermediate revision they built;
there is no rendered screenshot of the final source changes in this report.

## Evidence and audit

The supplied Appearance & Display screenshot is the visual target. Reviewed the
shared settings header/groups/dialogs, Appearance, reminder and organization
screens, folder-card long-press menus, startup theme wiring and navigation.
This is a focused comparison of those patterns, not a claim that every app screen
or accessibility behavior has been exhaustively audited.

1. **Startup prompt / ready / running:** oversized copy and duplicate headings
   weakened the hierarchy. Startup used default light/static colors instead of
   saved appearance preferences. Reuse the shared settings shell and theme.
2. **Completed migration:** the first revision improved the palette/header but
   retained a decorative icon, redundant result groups and an unnecessary menu.
   Remove decorative migration icons and the overflow/details modal; place recovery
   in a normal settings navigation row. Use one context-dependent primary action,
   concise results and last-checked text. Show progress only while running.
3. **Recovery list:** long titles wrapped mid-word, sizes had weak visual weight,
   and a switch could leave the same top rows visible after filtering. Use one-line
   ellipsis titles, semibold size badges and counted All/Large controls. Switching
   filters resets the scroll position and explicitly labels the visible subset.
4. **Recovery actions:** square, text-only popups did not match folder-card menus.
   Reuse the same neutral surface overrides, 20dp corners, 220dp width, semibold
   labels and 18dp action icons. Support long press and the overflow affordance.
   Read/export/delete act on the chosen note; deletion retains confirmation.
5. **Accessibility / motion limits:** semantic theme colors, native size formatting,
   native progress semantics and labelled action buttons are used in source.
   Contrast, large-font layouts, TalkBack and predictive-back commit/cancellation
   still need device testing. Screenshots cannot establish those behaviors.

Supplied reference copies, kept as ignored local evidence:

- Appearance: `app/build/migration-ui-audit/00-appearance-reference.png`
- Intermediate migration: `app/build/migration-ui-audit/01-migration-before.png`
- Intermediate recovery: `app/build/migration-ui-audit/02-recovery-before.png`
- Intermediate menu: `app/build/migration-ui-audit/03-recovery-menu-before.png`

<details>
<summary>Supplied visual references</summary>

![Appearance reference](/home/hanan-bhatti/StudioProjects/second-brain/app/build/migration-ui-audit/00-appearance-reference.png)

![Intermediate migration before the final simplification](/home/hanan-bhatti/StudioProjects/second-brain/app/build/migration-ui-audit/01-migration-before.png)

![Recovery before single-line titles and counted filters](/home/hanan-bhatti/StudioProjects/second-brain/app/build/migration-ui-audit/02-recovery-before.png)

![Recovery menu before folder-style action icons](/home/hanan-bhatti/StudioProjects/second-brain/app/build/migration-ui-audit/03-recovery-menu-before.png)

</details>

## Source changes

- `NoteStorageUi.kt`: appearance-aware standalone theme without the normal
  ViewModel/credential initialization; shared settings layout, native size units,
  predictive-back surface and folder-style context menus.
- `CollapsibleCommonTopBar.kt`: render its existing actions slot and reserve title
  space for actions while collapsing, preserving its current typography/geometry.
- `NoteMigrationScreen.kt`: compact ready/running/completed/error states, a single
  primary action, inline recovery navigation, concise prompt and result details.
- `NoteRecoveryScreen.kt`: virtualized compact note cards; one-line titles; size
  badges; counted filters; icon menus and long press; consistent export progress,
  plain-text reading and confirmation dialogs. Body IO remains in the existing
  recovery store. Safe mode remains independent of the normal ViewModel.
- `MainActivity.kt`: use saved themes for startup/recovery and the redesigned
  storage-preparation error page.
- Added `ic_custom_note_export.xml`, a native vector matching the app's 1.5dp
  rounded outline strokes. Existing eye/delete assets are reused. No dependencies,
  networking, application ID, signing or database migration logic changes.

Checks actually run: `git diff --check`, new vector XML parsing, Python syntax
parsing and source-reference checks. These are not Kotlin compilation or visual QA.
Unrelated editor, Home, Folders, OCR, ViewModel, icon and IDE changes in the shared
workspace were left untouched and excluded from these commits.

## Requested debug-data reset

Only `com.hanan_bhatti.cobalt.debug` on `emulator-5554` was cleared, after a local
private backup of its files, database/WAL and preferences. Production was untouched.

- Backup: `app/build/note-stress/reset-20261102T200945Z/before-reset.tar`
- Backup SHA-256: `1c1c7520d4540174013173f7072a71ea49ff99335bbcb43c6c2dc9fb61925370`
- Reset result: `app/build/note-stress/reset-20261102T200945Z/result.json`
- Restored **60 legacy INLINE notes**, 1 KB–2 MB, **20,115,000 bytes** total.
- Fixture manifest: `app/build/note-stress/migration-seed-20261102T200954Z/manifest.json`
- Used the previously compiled test helper; no helper/app build was run.
- The app was left stopped, with migration required and its prompt unacknowledged.
  The next launch can show the one-time prompt. The redesigned UI requires the
  user to build/install this source; the currently installed APK is unchanged.
- The 1 GB stress/performance test remains deferred; these fixtures are for the
  migration flow, not a completed performance benchmark.
