# Cobalt manual test checklist

Prepared 5 November 2026. Committed baseline: `f0beddc`, branch
`cleanup/cobalt-single-app`. Reviewed the latest 180 commits; 117 commits follow
the pre-cleanup snapshot `bea90ab`. Some older commits describe behavior that was
subsequently replaced or removed. The companion `RECENT_CHANGE_INDEX.md` lists
the reviewed commits; this document groups their current user-facing outcomes.

All boxes below are **manual checks to perform**, not claims of completed testing.
The last completed targeted host run passed **148 tests with zero failures/errors**
and built a debug APK. It did not establish device speed, frame rate, peak RAM,
process-death reliability, release/R8 correctness, or the emulator/API matrix.
Older dated reports describe historical checkpoints; use the November 5 editor
and checklist/Home reports for the newer disk-backed editor implementation.

### 6 November follow-up: Dock, sharing and link metadata

These are acceptance checks for the current source changes; unchecked means not
verified on a device. The earlier 148-test baseline above is historical.

- [ ] Dock Note and Link modes show one frameless paste icon, with no separate
  clipboard strip or labelled Paste pill. The empty input previews clipboard text.
- [ ] Paste inserts only; it never saves until Send. Large pastes retain their full
  content while the Dock shows a bounded preview. Clear discards the pending input.
- [ ] Note and Link inputs start at one line with small padding and grow to three lines. Send uses the existing asset; Send stays hidden until there is text, and saving disables it. A save failure retains input for retry; rapid taps do not duplicate.
- [ ] Save a bare URL from Dock, OCR, Capture and Android sharing: it appears locally
  without waiting for metadata. Close the originating UI; background fetching continues.
- [ ] In Capture, wait for extraction, enter a description, save, then let background
  retries run: the entered description stays unchanged, including deliberate clearing.
- [ ] Explicit Refresh replaces the current description, including a manual value.
  Editing while a refresh is in flight protects the newer text from the late response.
- [ ] Untouched OCR AI descriptions are replaced by page descriptions when available;
  missing/blocked metadata retains the AI fallback. A deleted URL never reappears.
- [ ] Change a URL during fetching: the old page result cannot update the new URL.
- [ ] Offline saves survive restart and enrich after reconnecting. Safe mode and the
  migration gate pause the queue. Legacy bookmarks keep their existing single description column.
- [ ] External text/link/image/audio/video/file shares open Capture for review.
  Cancel creates no saved item; Save closes back to the source app. Multiple shared
  files each require explicit Save, and cancel stops the remaining batch.
- [ ] Import progress and completion/refusal stay readable in the bottom sheet until
  dismissed. Accepted 55 MB is success; 56 MB offers an explicit split confirmation.
- [ ] Dock text-note detail renders headings, emphasis, lists, tables and fenced code
  as Markdown. CODE notes stay monospace. Confirm the floating pill has three
  meaningful icons, and long-press/accessibility exposes the action labels.
- [ ] Recheck section 9: task toggles keep the current scroll position, without a
  progress bar or reopening the note. Verify the exported marker bytes afterward.

### What the new system contains

- One Cobalt application; discontinued Play flavor and Cobalt-owned cloud account/sync removed.
- Room metadata/previews with every text/code body in a private Markdown file,
  including one-line notes; verified/resumable legacy conversion.
- A continuous native editor surface backed by a disk piece model. Only a bounded
  text window is materialized for input; it should not look like editable blocks.
- Global text operations, descriptor-based live Undo/Redo, recoverable drafts and
  separately compacted saved-version deltas.
- Incremental Markdown blocks, background parsing, bounded caching and decoration.
- Startup crash recovery, explicit export, Storage tools, Trash and protected cleanup.
- Lightweight list/dock/widget/reminder data, local font controls and retained
  user-configured integrations. Global search and in-note Find have different scopes.

## 0. Identify the build and prepare

- [ ] Record APK version, install time, source commit/build identifier, phone model,
  Android version, available storage, theme, font settings and keyboard app.
- [ ] Use **Cobalt Debug**, package `com.hanan_bhatti.cobalt.debug`, for destructive
  tests. It should install alongside production `com.hanan_bhatti.cobalt`.
- [ ] Confirm the debug app has its own notes/settings. Never clear production data
  or use production notes as the only copy of migration/corruption fixtures.
- [ ] Keep an external copy of test inputs. Put unique `START`, `MIDDLE`, `END` and
  numbered markers in large notes so a partial display/save is detectable.
- [ ] Test offline first. Enter integration keys/connect Trakt only for those tests.
- [ ] Before a 1 GB library run, inspect free space: history, editor append data,
  migration and export need additional space beyond the original dataset.
- [ ] Save a screen recording of any failure and note the exact reproduction steps.

## 1. Fixtures and order of testing

Use `scripts/generate_note_fixture.py`. It streams one fixture and refuses to
overwrite an existing output. Example, **not run by this checklist**:

```sh
python3 scripts/generate_note_fixture.py links-5000 /tmp/cobalt-links-5000.md
```

| Dataset | Generator option / preparation | Main purpose |
|---|---|---|
| One line, empty, Unicode | Create manually | Everyday creation and edge cases |
| 1/3/5/10 thousand links | `links-1000`, `links-3000`, `links-5000`, `links-10000` | Link-heavy rendering, old crash repro |
| 100,000 characters | `characters-100000` | Paste/edit baseline |
| 50,000 lines | `lines-50000` | Line count and layout bounds |
| 5 MB plain / links | `plain-5mb`, `links-5mb` | Medium-large editing and reading |
| 50 MB plain / links | `plain-50mb`, `links-50mb` | Large-file acceptance |
| 54 MB / 56 MB | `plain-54mb`, `plain-56mb` | Tolerance / refusal |
| Exact 55 MB | Separate boundary fixture; no current generator option | Inclusive upper import boundary |
| Roughly 1 GB library | 20 distinct 50,000,000-byte notes | Collection of very large files |
| 1,000 notes | Separate collection of small/medium notes | Home, search, folders, scrollbar scaling |
| Mixed Markdown | `app/src/test/resources/markdown/tideline-regression.md` plus custom cases | Rendering regression fixture |
| Legacy database | Debug fixture scripts; developer-assisted | Real migration, not normal imports |

Bytes use decimal MB in size policy. Displayed rounded sizes may use a different
unit formatter; record exact input byte size when testing a boundary.

- [ ] Run small notes first, then link fixtures, then 5 MB, then 50–55 MB.
- [ ] Test both plain and Markdown-rich files; a plain 50 MB file alone does not
  prove link/table/code rendering performance.
- [ ] Test both many notes and large individual notes; they stress different paths.
- [ ] Use file import for multi-MB inputs. Clipboard/Binder limits vary by Android,
  source application and keyboard; distinguish platform refusal from a Cobalt freeze.

## 2. Single-app cleanup and startup

Changes: `e8bc429`, `640e197`, `a031f7c`, `bd1499a`, `7486169`.

- [ ] Launch with no internet: Home and local notes work without Cobalt sign-in.
- [ ] No Foss/Play flavor picker, Cobalt cloud account, cloud note-sync UI, Play
  download service or cloud-sync pull-to-refresh appears.
- [ ] Debug and release labels/package isolation behave correctly. Release updates
  an existing installation when signed with the same production certificate.
- [ ] Restart with a saved 5,000-link note: no launch crash loop or automatic opening
  of its full body.
- [ ] Restart with the large library: Home becomes usable without opening every file.
- [ ] Retained Trakt authentication is distinguishable from the removed Cobalt account.
- [ ] Developer release audit: dependency/DEX checks exclude Firebase, GMS and Billing;
  confirm release is not debuggable and archive R8 mapping. Manual UI checks alone
  cannot verify dependency removal.

## 3. Existing-note migration

Changes: `c975b37`, `c8ea89f`, `d731707`, migration/recovery follow-ups.

Installing a clean app or importing a new Markdown file does **not** test legacy
migration. Use a debug database containing old inline bodies. Existing fixture/reset
scripts affect test data and should be reviewed before execution; do not use them
against a personal phone's production package.

- [ ] Upgrade a fixture containing old one-line, 1 KB, 200 KB, 1–2 MB and link-heavy notes.
- [ ] The storage migration prompt appears for the applicable old storage version.
- [ ] Dismiss/reopen: the initial prompt does not repeatedly appear for that version;
  the migration/settings entry remains available for retry/results.
- [ ] Run migration: progress/counts are readable, sizes have sensible units, and
  results distinguish verified notes from failures.
- [ ] Verify note count, IDs, titles, folders, tags, dates and complete body content.
- [ ] Compare exported body text against original fixtures, including CR/LF/CRLF,
  emoji, non-Latin text and trailing blank lines.
- [ ] Interrupt migration partway through, reopen and resume: no duplicates or
  lost notes; already verified work is handled safely.
- [ ] Simulate insufficient space: originals remain; the app provides a retry path.
- [ ] Run migration again from Settings: no duplication or body truncation.
- [ ] Upgrade again after completion: the migration prompt does not return unnecessarily.
- [ ] Migration/recovery pages follow light/dark/dynamic colors, existing navigation
  transitions, readable one-line note titles, icon style and human-readable sizes.

## 4. Basic Capture and saving

Changes: original-screen integration, explicit-save staging, continuous native editor.

- [ ] Create/save/reopen a one-line text note. All text survives.
- [ ] Test empty body, blank lines, long title, punctuation-only title and a code note.
- [ ] Capture remains frameless; no visible paragraph boxes, section selectors or
  manual editing-window controls appear for large notes.
- [ ] Formatting controls appear with the keyboard and do not leave a keyboard gap
  or cover the current line after it closes.
- [ ] Open an existing note, edit and Save: Detail shows the saved result without reopening.
- [ ] Save a title/folder-only change: the body stays byte-for-byte intact.
- [ ] Developer inspection: one-line and large text/code notes both use private `.md`
  bodies; normal Room/list rows contain metadata/previews rather than giant bodies.
- [ ] Open and save without edits: no duplicated full-body checkpoint is required.
- [ ] Leave without explicit Save: the original saved note remains unchanged and
  recoverable edits follow the draft flow, rather than silently replacing the original.
- [ ] Save, close and immediately reopen repeatedly: no file-lock error, owner/layout
  crash, stale text, draft flash or wrong-note content.
- [ ] Switch between two notes: neither receives the other's typing or pending Save.
- [ ] Closing the keyboard/Capture immediately after the last keystroke does not lose it.

## 5. Continuous typing, cursor, selection and IME

Changes: `f9b7f3d`, `7958a8d`, `af83e7d` and native editor follow-ups.

- [ ] Type at the start, middle and end of each fixture size; verify exact positions after reopening.
- [ ] Scroll many screens and tap between words: the cursor follows the tap.
- [ ] Type and move across positions approximately 32 KB apart; there is no visible
  block seam, cursor trap, wrong offset or text jump.
- [ ] Backspace/delete across those boundaries, including before/after emoji and CRLF.
- [ ] Test hardware left/right arrows and Shift selection across boundaries, if available.
- [ ] Select a word/paragraph with handles, then replace/delete it; untouched text survives.
- [ ] Select all in a large note, replace it, then Undo: the complete original returns.
- [ ] Copy/cut a normal selection, paste elsewhere, and check exact spaces/line endings.
- [ ] An extremely large clipboard selection receives a clear bounded-copy/export
  message rather than silently copying only the visible window.
- [ ] Test English prediction/autocorrect and the keyboard's replacement suggestions.
- [ ] Test a composing script (Urdu/Hindi/Arabic or CJK), accents and combining marks.
- [ ] Type emoji, multi-code-point family emoji, flags and skin-tone combinations;
  compare saved/exported text. Code-point correctness alone does not prove full
  grapheme-cluster cursor behavior.
- [ ] Scroll during composition, commit a suggestion, rotate and continue: no duplicated
  composition or stale keyboard input inserted at an old position.
- [ ] Test TalkBack selection/announcements and keyboard-only operation. Global
  accessibility behavior across a virtual text window needs direct device validation.

## 6. Paste and file import/share

Changes: streaming staging, `e62f326`, `TextFiles`, `TextImportAction`.

- [ ] Paste 100,000 characters into an empty note and into the middle of an existing note.
- [ ] Paste the 1k/3k/5k/10k-link fixtures: no prolonged frozen UI or whole-note layout crash.
- [ ] A large paste shows progress/cancellation where appropriate; the keyboard and
  navigation do not become permanently stuck.
- [ ] Cancel midway: no partial replacement is committed over the saved original.
- [ ] Save immediately after paste and after allowing recognition/styling to settle.
- [ ] Import through the Add/import action; no unwanted Import button occupies the body editor.
- [ ] Share/open `.md`, `.mdx`, `.txt`, `.markdown`, JSON, YAML and source/text documents
  from real file-provider apps. MDX/JavaScript-looking content is inert text.
- [ ] Test UTF-8 with/without BOM and BOM-labelled UTF-16 LE/BE, emoji and mixed scripts.
- [ ] Malformed encoding, a binary file with a text extension, a missing URI grant,
  cancelled picker and unavailable/cloud-only provider fail clearly without altering originals.
- [ ] Share multiple files: all intended files are handled sequentially without a job storm.
- [ ] Share while Cobalt is closed, open, in Capture and awaiting migration; the content
  is not lost or applied to the wrong note.
- [ ] Import 50 MB and 54 MB: retained with appropriate warning above the soft threshold.
- [ ] Test exactly 55 MB: acceptance is inclusive under the current policy.
- [ ] Import/paste above 55 MB: clear refusal/split offer, no silent truncation.
- [ ] Existing oversized notes remain recoverable/readable/exportable; a new import
  refusal must not delete an existing saved note.

## 7. Formatting and live source styling

- [ ] Toolbar bold, italic, strike, inline code, fenced code, headings, quote, lists,
  checklist and link actions edit the intended selection/global position.
- [ ] Each formatting operation can be undone without losing surrounding text.
- [ ] Styling does not flash the entire document to unstyled text on every keystroke.
- [ ] Scrolling far away and returning restores styling for the visible region.
- [ ] A code-type note uses monospace in editing; ordinary notes use the chosen text font.
- [ ] Styling/link decoration stays bounded for thousands of links; the underlying
  source and exported links remain complete even when decoration is capped.
- [ ] Check RTL paragraphs, mixed LTR code/URLs and punctuation in both editor and preview.

## 8. Rendered Markdown and Detail

Changes include bounded lazy blocks, off-main parsing, code continuation and autolinks.

- [ ] Small and large notes open in the original Detail screen, with the expected
  title/date/actions and no separate large-note interface.
- [ ] Verify headings, bold, italic, strikethrough, nested bullets/numbered lists and rules.
- [ ] Verify inline code containing `<id>`, pipes and HTML-looking text is preserved.
- [ ] Verify fenced Kotlin, SQL, JSON, shell, Go and text blocks use monospace and
  preserve repeated spaces, tabs, indentation and blank lines.
- [ ] The short example-output and Go examples stay in one visual code box.
- [ ] Huge fenced blocks may use bounded internal fragments, but adjoining surfaces
  read as one block; no artificial gap, repeated fence/language label or lost code.
- [ ] Repeat with code inside numbered lists, nested lists and blockquotes.
- [ ] Check tables, inline formatting in cells, wide-table horizontal scrolling,
  last-column access and right-edge affordance.
- [ ] Verify note/tip/warning-style blockquotes, paragraph spacing and FAQ/license sections.
- [ ] Tap Markdown links, bare URLs and bare email addresses. Email uses a mail handler;
  absent handlers produce a useful message rather than a crash.
- [ ] Code blocks do not execute commands; MDX does not execute JavaScript.
- [ ] Fast-fling, reverse direction and jump far down: no raw-Markdown flash, missing
  tail or whole-document reload. Check `END` and the last table/code block.
- [ ] Cold uncached positions may still need preparation; record blank/skeleton time
  rather than assuming a guaranteed zero-loading result.
- [ ] Open the largest fixtures at maximum font size and narrow split-screen width:
  no giant Compose Constraints exception or cropped/unreachable tail.
- [ ] Test cross-block reference links and complex unusual Markdown separately;
  full-document reference-definition behavior is not certified by per-block tests.

## 9. Checklist regression — highest priority

Changes: `c28df70`, `f3580af`; Capture readiness changes also landed in `111b25d`.

- [ ] Scroll well below the top and check/uncheck a task: checkmark changes in place.
- [ ] No checkbox-save progress bar appears; title/body do not disappear or reload.
- [ ] The same sentence stays at the same scroll position after the save finishes.
- [ ] Toggle several tasks, including near the end of a large note, then reopen and
  export: only intended marker characters changed.
- [ ] Repeat in Capture preview: it does not switch to the native editing surface
  or restart preview while the checkbox saves.
- [ ] Rapid taps/scrolling while saving never toggle another task, lose accepted changes
  or overwrite a newer note version. Record ignored taps or delayed feedback as UX defects.
- [ ] Test quoted/ordered tasks and tasks around fenced code. Checkbox-looking code
  remains code and is not toggled.
- [ ] Simulate save failure: original/recovery data survives, the optimistic display
  rolls back appropriately, and the error is visible.
- [ ] CRLF/CR/LF, emoji and all non-marker bytes survive toggles exactly.

## 10. Find, replace and outline

Changes: continuous global search, bounded query/results, batch replace and highlight fixes.

- [ ] Find text near `START`, `MIDDLE` and `END`, including beyond the Home search prefix.
- [ ] Next/previous visit the right occurrence and handle wrap-around sensibly.
- [ ] Typing in the query does not steal focus or move the caret into the document.
- [ ] Highlight only matching text; distinguish the active occurrence from other matches.
- [ ] The search bar respects keyboard insets, blur settings, dark/light mode and
  outside interaction without blocking unrelated controls.
- [ ] Replace one match: correct global range only. Undo restores it.
- [ ] Replace all with a longer string, shorter string, empty string and a replacement
  that itself contains the query: no looping, skipped text or offset corruption.
- [ ] Test thousands of replacements across batches; Undo restores the pre-operation text.
- [ ] Long/empty queries and result/replacement caps give useful feedback. Current
  source has a 256-character query cap, 500-heading outline cap and 10,000 replacement
  budget; exercise the actual active path rather than treating this as unlimited.
- [ ] Outline headings in nested content, around code fences and far down the file;
  tap a heading and confirm the correct location without parsing all skipped text first.
- [ ] Close search/outline and return to normal typing without a stuck keyboard/freeze.

## 11. Undo/redo, autosave and recovery

- [ ] Perform 10+ inserts, deletes, large pastes and formatting operations; Undo/Redo
  restores exact text and useful cursor positions in both directions.
- [ ] Undo then type a new edit: the abandoned redo branch does not reappear.
- [ ] Repeat in a 50 MB note. Live undo uses piece descriptors/append data, not a
  deliberate new full-file copy for every Undo step. Measure actual disk/RAM growth.
- [ ] Pause/background, rotate, resize and reopen while edits are pending: no lost final keystroke.
- [ ] Close while edits are queued: accepted edits drain rather than being discarded.
- [ ] Developer-assisted process kill during edit/save: reopening offers a complete
  verified checkpoint/recovery state, without silently promoting incomplete data.
- [ ] Simulate failed journal publication/full storage, free space and retry Save:
  retained edits and Undo survive; retry does not reload an older state over them.
- [ ] Export unsaved text during an internal-write failure: pending text is included
  without requiring another successful internal checkpoint first.
- [ ] Storage → Draft recovery shows interrupted saved-note drafts and never-saved notes.
- [ ] Recover into a new note: the original saved note stays intact, and the recovered
  note contains the complete pending body. A completed draft is not repeatedly offered.
- [ ] Recovery/export refuses an actively owned editor draft rather than racing it.
- [ ] Simulate a conflicting saved version: neither original nor conflicting draft is
  silently overwritten; recovery remains accessible.
- [ ] Do not expect unlimited undo: the current piece-table operation stack is bounded
  to 100 operations. Test beyond that and verify the retained history is consistent.

## 12. Saved history and delta storage

Live Undo and saved-version history are different mechanisms.

- [ ] Save multiple small/scattered changes; open history and export older versions.
- [ ] Restore an older version, reopen, and compare its complete body to the original fixture.
- [ ] Record storage before editing, after saving, and after background compaction settles.
- [ ] Do not require every saved version to be a tiny delta: the current file remains
  complete; anchors/full copies may remain when compaction is unsafe or ineffective.
- [ ] Export/restore a delta-backed version offline; reconstructed content is exact.
- [ ] Cleanup protects anchors required by retained versions and active readers/drafts.
- [ ] Interrupt optional compaction: current notes remain readable and a failed
  compaction does not turn a successful Save into data loss.
- [ ] Test ten revisions of several large notes, not just one. Record physical bytes
  and distinguish current bodies, history, drafts, temporary/cache data and database.

## 13. Storage, Trash and cleanup

Changes: `f81b5fe`, `7d2c1cf`, `0df2b3e` and retained-file safeguards.

- [ ] Usage bar/legend show labelled, readable categories and plausible sizes;
  app-data usage is not confused with phone free space.
- [ ] Open Manage notes, Draft recovery, Trash, Version history, Unlinked files and
  maintenance pages; actions lead to the intended page and back navigation works.
- [ ] Note titles stay one line/ellipsized; sizes are rounded KB/MB/GB, not raw byte strings.
- [ ] Ordinary note recovery is not duplicated unnecessarily in Migration and Storage.
- [ ] Delete with Trash enabled: item leaves normal lists/search/reminders/widgets
  but appears in Trash with its body and versions recoverable.
- [ ] Restore: title, folders, dates and body return; a preview never overwrites the body.
- [ ] Empty Trash/permanently delete: owned body, versions, drafts and unshared media
  disappear from storage accounting after refresh; stale autosave cannot resurrect it.
- [ ] Shared media is retained while another live item still owns it.
- [ ] Disable Trash: new deletions still have the short Undo window; existing Trash
  deadlines are not silently changed. Current policy is 30 days / 30-second fallback Undo.
- [ ] Test expiration with a developer-controlled clock/fixture, not by changing the
  personal phone's date. WorkManager may defer automatic expiry cleanup.
- [ ] Interrupt permanent deletion: retry completes it safely; half-deleted items
  are not offered as normally restorable.
- [ ] Review/export/recover/remove unlinked files; active recovery work and required
  history anchors are protected. Review confirmation does not delete newly appeared files.
- [ ] Clear cache: saved notes, draft journals, original recordings and retained history survive.
- [ ] Database compaction does not change note count/content and handles low space gracefully.
- [ ] Cancelling a large paste does not leave its rejected append tail as retained history.

## 14. Home and scrollbar — highest priority

Changes: entry-animation reductions, bounded previews, `c66aeba`.

- [ ] Compare 10, 100 and 1,000 notes in list and two-column layouts, cold and warm.
- [ ] Fling both directions repeatedly: no accumulating slowdown, frozen input or wrong-card taps.
- [ ] Let the scrollbar appear, drag slowly, quickly and reverse direction: it tracks
  the finger without bouncing to targets based on newly visible card heights.
- [ ] Hold the finger stationary: content stays stationary rather than repeatedly jumping.
- [ ] Release after a quick final move: the last requested position is applied.
- [ ] Reach both start and end, including with uneven card heights and mixed media.
- [ ] Switch list/grid, then drag: it controls the visible layout, not a stale adapter.
- [ ] Verify scrollbar enable/location settings and existing top/bottom buttons.
- [ ] Open a card after fast scrolling and return: shared transitions and position remain correct.
- [ ] Delete/Undo, archive/unarchive, long-press selection and folder assignment still work.
- [ ] Scroll with blur on/off as a diagnostic comparison; record the setting and
  actual jank rather than treating blur as a measured cause without profiling.
- [ ] Check titles/previews never expose `/data/user/...`, cache paths, raw `content://`
  URIs or generated temporary filenames after import/share.

## 15. Search, folders, Side Dock, widgets and learning

- [ ] Global search finds title, preview and indexed beginning-of-body text without
  loading complete large notes. File-body indexing currently uses a bounded 32 KiB prefix.
- [ ] A match only near the end of a huge body is found through in-note Find; do not
  assume full-document global search indexing has been implemented.
- [ ] Folder filtering, rankings/suggestions, archive/unarchive and folder detail
  show correct lightweight previews; opening a result loads the correct complete note.
- [ ] Renaming/moving/tagging from a projected item never blanks its real body.
- [ ] Side Dock opens quickly with huge notes in the library: recent-note querying
  is bounded (15 items), and previews are limited to two lines.
- [ ] Dock drag/expand/collapse, theme changes, paste, sharing and OCR capture still work.
- [ ] Widget previews remain short; tapping opens the correct note; deleted items disappear.
- [ ] Reminder delivery respects enabled state, custom time and permissions; learning
  and suggestions handle the large library without materializing all bodies.
- [ ] Heatmap/activity records remain correct after capture, edit, migration, deletion and restore.
- [ ] OCR selected-link saving remains lifecycle-bound/serialized; a large result
  does not create thousands of unbounded concurrent saves.

## 16. Fonts, themes and UI consistency

Changes: bundled local fonts, typography provider/settings and native font integration.

- [ ] Appearance → Text & fonts opens with live preview and no unexpected top gap.
- [ ] Test Inter, Literata, Source Serif 4, Atkinson Hyperlegible, Lexend and System.
- [ ] Test JetBrains Mono/System monospace for code; code never becomes proportional.
- [ ] Change size, weight, line height, letter/paragraph spacing and presets; values
  persist across restart and apply to the intended reader/editor/UI scope.
- [ ] Disable custom fonts / toggle applying fonts to app UI; notes remain readable.
- [ ] Change Android font scale/display density and rotate: native editor metrics update.
- [ ] Fonts work offline and do not initiate a font download.
- [ ] Light, dark, system and dynamic color work on migration, storage, editor, search,
  dialogs and overflow menus; icons maintain contrast and meaning.
- [ ] Existing back/predictive-back transitions, keyboard insets and safe areas remain
  consistent. Verify gesture navigation and large fonts on an actual device.

## 17. Startup safe mode and failures — developer-assisted

- [ ] Two real uncaught crashes during startup trigger safe mode. Force-stop, swiping
  Recents away and normal OS process death must not be counted as startup crashes.
- [ ] A healthy startup resets the applicable consecutive-crash count; the current
  startup window is 30 seconds and the main thread must actually complete it.
- [ ] Safe mode pauses background learning/work and link previews and provides note recovery.
- [ ] Locate the offending large/missing note, read/export it, and exit safe mode intentionally.
- [ ] Missing/corrupt body files retain metadata and show a recoverable error; no
  automatic deletion or endless startup/open loop.
- [ ] Test loss of export permission/provider, disk-full, failed rename/fsync, cancelled
  import, interrupted migration and process death at durable-write boundaries.
- [ ] Deleting while an editor/import is saving cannot resurrect the deleted identity.
- [ ] Error text is understandable and does not expose note contents, tokens or private paths.

Use isolated debug fault injection/fixtures for these checks. Do not corrupt or fill
the storage of a personal production installation to simulate them.

## 18. Export, security and retained integrations

- [ ] Explicit Markdown export creates a user-selected file/folder/zip as offered by
  the UI; title/tags/dates frontmatter and full body survive an export/import round trip.
- [ ] Compare body separately from YAML metadata; a raw file SHA is not expected to
  match when the export adds frontmatter.
- [ ] Notes are app-private unless explicitly exported/shared. No Cobalt cloud account,
  automatic cloud backup or note-sync backend is required.
- [ ] Crash reporting creates a local report; Send opens an external email/share flow,
  and nothing uploads automatically.
- [ ] Hide content in Recents works; check the documented screenshot/recording side effect.
- [ ] Gemini/TMDb keys and Trakt tokens persist securely and are absent from diagnostics.
  Developer checks are required to verify storage/log implementation, not just the UI.
- [ ] Retained Gemini OCR/transcription, media/TMDb/anime search, audio/video playback,
  trailers and provider links still work when explicitly configured/used.
- [ ] Trakt connect/cancel/callback, manual sync, watch/rating/watchlist updates and
  disconnect work; offline disconnect still clears local credentials and explains
  any failed server revocation.
- [ ] GitHub manual release checks work; automatic checks remain controllable and
  downloads/installations are not silently started.
- [ ] Review NETWORK.md when testing offline/network expectations: saved images,
  trailers, URL metadata and retained integrations have automatic fetch paths;
  “local-first” is not a promise of zero network traffic.

## 19. Concurrent/uncommitted UI work

The working tree also contains changes from other work, including filter/settings,
icon and Capture adjustments. Presence in source does not prove that the APK you
installed contains them. Verify the APK first; keep their results separate from the
committed baseline above.

- [ ] If present, Home and Media Hub filter/sort sheets open, apply, reset and retain
  their settings without losing notes or changing the wrong screen's filter.
- [ ] If present, updated icons remain legible/tappable in dark/light mode and long-press menus.
- [ ] Retest any concurrent Capture/search-bar UI changes with the keyboard, preview,
  Save, search focus and back gesture. Report the precise build containing them.

## 20. Performance evidence and release gate

Record numbers rather than “feels fast” alone. Current targets are acceptance goals,
**not achieved measurements**:

| Scenario | Desired evidence / original target |
|---|---|
| 5 MB first content | <1 second, cold and warm separately |
| 50 MB first content | <2 seconds; record any stricter goal separately |
| Typing | p95 <50 ms |
| Scrolling | Near display rate; jank <1%, measured |
| Main-thread frames | <16 ms for a 60 Hz budget |
| 50 MB peak heap | <150 MB original goal; record PSS/native memory separately |
| Paste | No uninterrupted UI stall above roughly 300 ms; progress/cancel remains usable |
| Stability | No OOM, Constraints crash, crash loop, truncation or accepted-edit loss |

- [ ] Measure one large note and the roughly 1 GB library, with 10+ edits/Undo/Redo per
  selected note; record RAM, native/PSS, CPU, disk growth and time after cleanup settles.
- [ ] Test physical mid-range/low-end hardware; separate emulator overhead from device results.
- [ ] Run API 29, 31/32, 34 and 36, plus high-density and 2 GB low-RAM profiles.
- [ ] Repeat critical smoke tests on the signed/R8 release APK. Passing debug does
  not certify release behavior.
- [ ] Developer gate: full tests, Room migration/instrumented tests, lint, static
  quality, release dependency audit and baseline/Macrobenchmark results are recorded
  as actually run/passed/failed/not run. Compiling test APKs is not executing them.

## Known items to record, not assume resolved

1. A current large-note warning says live editing above 50 MB is locked, while
   NoteSizePolicy accepts imports/edits up to 55 MB (with existing-note allowances).
   Verify real behavior and report the copy/behavior mismatch. Do not infer a lock
   solely from that warning or certify full 55 MB editing from policy alone.
2. Arbitrary Markdown fidelity, cross-block references, full IME/grapheme/selection
   behavior and accessibility still require device cases beyond host tests.
3. Cold reader preparation and background compaction can take time. Neither zero
   loading indicators everywhere nor near-zero RAM/CPU is a measured guarantee.
4. Saved deltas can coexist with full files/anchors and interrupted work. Storage
   growth must be attributed before calling every extra file a leak.
5. The completed 148-test run and debug build do not substitute for the release,
   real-migration, process-death, API-matrix and 1 GB performance acceptance runs.

## Result template

```text
Check/section:
Build/APK:
Device / Android / keyboard:
Fixture name and exact bytes:
Theme / font / list or grid / blur:
Steps:
Expected:
Actual:
Reproduces: __ / __ attempts
Cold/warm timing, frame data, RAM/PSS, CPU, disk (if measured):
Screen recording / redacted log reference:
PASS / FAIL / BLOCKED / NOT TESTED:
```

Start with sections 4–11 and 14 on small and 5 MB notes, then migration/recovery
and storage, then 50–55 MB/1 GB stress. Keep ordinary functionality and destructive
failure testing as separate runs so failures are attributable.
