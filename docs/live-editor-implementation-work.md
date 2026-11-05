# Live editor implementation work (in progress)

The current checkpoint is [editor-logic-validation-2026-11-05.md](editor-logic-validation-2026-11-05.md).
It supersedes the historical pending-work and test-count notes below. The latest
editor logic passes 144 host tests and packages a debug APK; device acceptance is pending.

Current user instruction: finish remaining editor/Markdown code, then mobile stress
validation. Preserve the existing frameless editor and normal capture toolbar.
Do not claim release/performance readiness while integration is unfinished.

## Rendering fixes in this continuation

- MarkdownBlocks keeps ordinary fenced examples together beyond ordinary row limits;
  huge fences have bounded continuation metadata. Blank lines inside fences are data.
- Native code block surface uses explicit monospace text style and joined continuation
  edges. Native Code-note editor switches to Typeface.MONOSPACE.
- Bare email links use mailto and ACTION_SENDTO only on tap. No added network call.
- Increase ordinary paragraph spacing, including lazy boundaries.
- Reconstructed Tideline regression fixture covers provided screenshot cases. The
  original raw README was not supplied; do not mislabel this as a byte-exact golden.
- Added instrumented code typography/whitespace test; not executed on a device yet.
- Last completed host run:119 tests passed after fixing continuation-inserted blank
  lines. New disk model tests were NOT in that run: their shell creation did not
  execute when automatic permission review timed out. They have since been written
  separately and the bounded runner is running again (session38291).

## New backing model (not integrated into Capture yet)

- IndexedUtf8Units presents virtual UTF-16 code units over immutable UTF-8 bytes.
  Sparse bookmarks map offsets; no UTF-16 copy of the original file is made.
- PieceTable gained descriptor-only state serialization/restoration.
- DiskTextModel stores inserted UTF-16 units in an append file, persists checksummed
  descriptor/Undo/Redo/selection state using sync + same-directory atomic rename.
  Large deletion retains descriptors; saved versions remain a separate concern.
- DiskJournalRecovery's bounded UTF-16 reader/UTF-8 stream are internal reusable types.
- New DiskTextModelTest covers Unicode bookmarks, reopened Undo/Redo/selection,
  failed publication, corruption, and a synthetic 50 MB deletion/restoration.

Still needed before activating model: invalid-edit/cancellation recovery handling,
serialized controller with grouped native delete+insert transactions, store-owned
artifact paths/leases, FileEditorSession integration, and a seamless native viewport.
Do not wire an untested model into user data or remove resident admission guards yet.

## Intended native viewport bridge

Keep one Sora View for native IME, bidi, selection handles and accessibility, but
materialize only a bounded logical window from the disk model. No visible range
cards/buttons. Maintain global character coordinates separately, rewindow outside
the visible region while restoring the exact visual anchor, preserve selection
anchors crossing the materialized range, intercept global select-all/copy/cut/
replacement/Undo/Redo, and stream large paste/find/export outside Main. Restart
stale IME connections on window changes, defer rewindowing during composition,
and test selection and composition over boundaries. Must not silently operate
on only the materialized portion of a global selection.

The existing Sora Content owns private resident line arrays; changing parser caches
alone cannot meet the disk-backed-live-editor requirement. The native InputConnection
contract requires selection updates and composing regions to stay consistent:
https://developer.android.com/reference/android/view/inputmethod/InputConnection
CodeMirror's viewport approach is a useful architectural reference, not a new dependency:
https://codemirror.net/docs/guide/

No commits made in this continuation yet. Shared renderer/lazy-reader files already
had unrelated changes. Before snapshots are in app/build/editor-review; stage only
task hunks using HEAD-derived copies. Other modified files/NETWORK.md are concurrent
work and must be preserved, not staged wholesale. No subagents authorized.


Build isolation update: session38291 failed unit compilation with unresolved references
across virtually all app classes. Another 4 GiB Gradle daemon is active in this shared
checkout and the main compile jar was only362KiB. Do not kill it. Added opt-in
`tools/check-editor-isolated.sh` + init script to put this verification's outputs
and project cache under ignored `build/editor-validation/`; use this runner from
now on. The failure is not a passing model test and must not be counted as such.

## 2026-11-04 resumed integration

User committed prior work, merged fonts/Trakt, and requested completion urgently.
Turn began clean at1eebdf7. Read font integration e6bfea2 and TypographyProvider.
Approval is working again after prior usage-limit block. No new subagents.

Last completed check:125 targeted host tests and Android-test APK build passed
before active integration. Later integrated Capture/core suite125 passed3m22s;
it did not yet include new native disk-viewport tests. Current check session29423
adds DiskMarkdownViewTest and TypographyPrefsTest, still running at this update.

Current active code: ContinuousNoteDocument can own DiskEditorSession. FileEditorSession
opens disk for >=200KB or restored diskToken, checkpoints through a stream, and has
preview byte→UTF16 edits. ContinuousNoteEditor now selects DiskMarkdownView for disk
models, routes find/outline, and exports through session streams. Native typography
shares LocalCobaltTypography preferences through NativeEditorTypography.

DiskMarkdownView materializes <=32,768 UTF16 chars and caps dense text at about1024
lines. Global selection survives viewport clipping. Select-all replacement, copy/cut,
find, outline, format wrappers, paste and model Undo are routed globally. View-scoped
native edit batches transfer synchronously to a session-owned outbox before disposal.
The session owns an independent bounded worker lifetime so normal VM teardown can drain
accepted edits. Stale IME commits are rejected after disposal. THESE native paths
are under test, not yet validated on device.

Pending draft app/build/editor-review/FileEditorSession.all-disk.draft changes all
file notes to disk, eliminating small→large paste promotion/RAM-history discontinuity.
It also syncs revision callbacks after Capture closes and drains native edits before
checking whether a closing session is dirty. Copy/review after current compile ends.
Still need clean indentation and unchanged-close retirement handling verification.

Important unfinished edge cases:
- Emergency unsaved export after disk publication failure: controller currently rejects
  reads while failed. Need read-only recovery stream overlay of held native batches,
  never omit pending accepted text or require a successful internal write to export.
- Native boundaries: hardware arrows/delete and IME global selections/code-point deletion;
  composition during rewindowing; current shortcut handling is partial. No device claims.
- Model metadata write/read descriptor budget must match; readState currently caps200000
  descriptors but writeState does not enforce that cap. Prevent publishing unreopenable
  state. Prefer bounded metadata and clear refusal over silently trimming user history.
- LegacyNoteRecovery does not yet understand diskToken; add recovery/export for new live
  state so conflicting drafts remain accessible through Storage.
- Very long fenced code inside lists/quotes needs stronger continuation indentation/gutter
  context tests. Ordinary fence/list cases and bare email tests already passed.
- Check code typography with device test; do not claim normal-scale screenshots exist.

Build isolation still essential if another agent builds this checkout. Use
./tools/check-editor-isolated.sh; outputs build/editor-validation/_app. One worker,
1536MiB heap,384MiB metaspace. Existing font tests use deprecated Compose test API;
that warning predates this turn. No new APK/stress result claimed yet.
