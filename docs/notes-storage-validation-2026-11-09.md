# Notes and storage deep test — November 9, 2026

The requested behavior is **not fully met**. Rapid injected typing in Quick Note loses characters; editing a 50 MB note initially retains another complete 50 MB body; restoring delta history adds another full body; and short-note saved Undo is unavailable. A reader component test also fails repeatedly. Storage integrity, draft recovery, migration, deletion/restoration, and protected-cache checks passed for the inputs those tests supplied.

Final distinct automated checks: **202 passed, 1 failed**. The selection-menu check additionally flaked once before passing separately. Manual UI probes exposed behavior gaps that these existing automated checks do not cover.

Test environment: running `emulator-5554`, Android 17/API 37, x86_64, Cobalt 2.2.3/version 19, repository revision `00ff03e`. The installed stable package was exercised through its UI. A freshly built debug package was used for exact filesystem measurements and Android instrumentation. The stable APK and existing library were preserved. The debug data was backed up before replacement. No production implementation was changed.

## Storage measurements

The device fixture used **50,000,000 bytes of printable pseudo-random text**, streamed from disk. Each edit changed another byte near the beginning, middle, or end. This avoids measuring only easily compressed repeated text. Every one of the 11 generations was independently hashed and reconstructed through the store, including the nine delta generations.

| Stage | Current body | Physical history | Combined body/history | Full `.md` files | Deltas |
| --- | ---: | ---: | ---: | ---: | ---: |
| Import | 50,000,000 B | 0 B | 50,000,000 B | 1 | 0 |
| First edit | 50,000,000 B | 50,000,000 B | 100,000,000 B | 2 | 0 |
| Second edit | 50,000,000 B | 50,055,845 B | 100,055,845 B | 2 | 1 |
| Tenth edit | 50,000,000 B | 50,575,231 B | 100,575,231 B | 2 | 9 |
| Rename only | 50,000,000 B | 50,575,231 B | 100,575,231 B | 2 | 9 |
| Restore a delta | 50,000,000 B | 100,503,925 B | 150,503,925 B | 3 | 8 |
| Restore latest again | 50,000,000 B | 100,503,925 B | 150,503,925 B | 3 | 8 |

Nine retained deltas together used **575,231 bytes**, rather than nine more 50 MB files. The first edit still doubled body storage. Restoring a delta added **49,928,694 bytes** after replacing its compressed representation with a complete body; switching back did not reclaim that space. Metadata-only saving added zero body bytes.

The stable app was also measured through Storage and Saved versions. Its initial breakdown was 1.1 GB notes/media, 1.7 kB history, 0 B Trash, 244 B unlinked, 1.4 kB recovery, 14 MB cache, and 2.4 MB database/settings. After three editor saves with saved undo/redo mixed into the flow, refreshed history showed **100 MB**. Saved versions listed two full 50 MB copies and a **595 B delta** for the test note. After the subsequent restart/recovery/save flow and remaining checks, the final refreshed stable screen reported **50 MB history**, 1.2 GB notes/media, 0 B Trash, 488 B unlinked, 1.5 kB recovery, 14 MB cache, and 2.6 MB database/settings. Thus the extra full history seen in the mixed editor flow was eventually compacted; it should not be described as permanently stuck at 100 MB. UI amounts are rounded; the table above uses exact physical file lengths.

Sources: [large measurements](../build/notes-deep-test-20261109/large-storage-measurements.json), [stable settled storage](../build/notes-deep-test-20261109/stable-storage-large-settled.png), [stable saved versions](../build/notes-deep-test-20261109/stable-history-versions-large.png).

## Findings requiring work

1. **Rapid Quick Note input loses text.** After focusing the body and taking a fresh UI snapshot, ADB keyboard injection submitted 125 characters. The field retained only **104 characters**, and saving persisted that incomplete 104-character body rather than the intended text. This reproduced the first quick note's earlier missing `p` in `Alpha`. A control submitting the same characters individually with 100 ms pauses retained all 125 and saved them exactly. This establishes a failure under rapid injected hardware-key events, rather than proving that every software keyboard behaves the same way. The local `TextFieldValue` and `LaunchedEffect(captureContent)` feedback in `QuickNoteBottomSheet` are a likely investigation point; the exact cause has not been fixed. [Typing measurements](../build/notes-deep-test-20261109/typing-measurements.json), [rapid input](../build/notes-deep-test-20261109/stable-rapid-typing.png), [slow control](../build/notes-deep-test-20261109/stable-slow-typing-control.png).
2. **First-edit duplication.** The current scheme requires a separate complete history anchor. `PrivateMarkdownStore.prepareArchivePrevious` needs a third generation to choose a base, excluding both previous and current. Therefore a single small edit of a 50 MB note retains two full bodies. This is a design limitation against the requested storage goal, rather than a corrupt-version failure.
3. **Restoration increases retained full storage.** `NotePersistence.restoreGeneration` materializes the selected delta, updates the database reference, and keeps the outgoing version. It does not schedule compaction of the outgoing generation. The verified restore fixture grew from 100.58 MB to 150.50 MB and stayed there after restoring latest. The stable mixed editor flow temporarily retained two full history copies, then reached 50 MB history after further activity.
4. **Short-note saved undo is unavailable.** A quick note was created, edited, and saved. Its old 15 B body appeared in Saved versions, but the detail menu reported “No earlier version” and disabled Undo. The large-note saved Undo and Redo worked. `CobaltViewModel.saveActiveItem` registers `NoteGenerationHistory` only in its file-editor branch; ordinary short-note repository saves do not register that change. Screenshot: [previous short body available](../build/notes-deep-test-20261109/stable-history-after-small-edit.png).
5. **Large editor save latency needs attention.** Two stable 50 MB saves took 24.61 and 22.29 seconds from initiating the UI save probe until the saved detail state appeared. These are observation upper bounds, including UI polling, while builds were active. Direct streaming save plus completed compaction took 1.06 seconds for the first edit and 7.18–19.49 seconds for subsequent edits. Repeat performance measurements without build load before treating these as production timings.
6. **The lazy reader fixture opens away from the beginning.** Its assertion for Paragraph 1 failed on two original runs and again after replacing the outdated paragraph-as-row assumptions with complete-line matching and byte navigation. Captured semantics show a 63-row collection scrolled to value 11,000, with **Paragraph 1057** as its first rendered text. The failure occurs before the distant-navigation step. This is a reproducible failure in the isolated `LazyMarkdownNote` fixture on API 37; the live 50 MB detail/editor opening checks do not prove the same fault across every reader path. [Final failure](../build/notes-deep-test-20261109/device-reader-final.log), [captured semantics](../build/notes-deep-test-20261109/reader-failure-semantics.txt).

The next implementation work should fix rapid Quick Note input loss first, register short-note generations consistently, reduce complete-body retention on first edit and restore, and investigate the reader's initial scroll position. No production fixes to these behaviors were made during this testing task.

## Coverage and observed outcomes

| Area | Checks and outcome |
| --- | --- |
| Creation and persistence | Quick note creation, short-body edit, reopening, 5 MB and 50 MB import/save worked. Device tests verified empty, one-line, Unicode, and file-backed bodies. |
| Rapid typing | A 125-character rapid injection retained and saved 104 characters; a 100 ms-per-character control retained and saved all 125. This is a failed manual check, separate from the automated totals. |
| Large-note history | Ten cumulative edits; all 11 hashes and reconstructed bodies verified. Current body was unchanged by reading history. |
| Metadata changes | Renaming preserved the content hash and exact body/history bytes. Existing projection-save tests confirmed that a preview cannot overwrite the real body. |
| Saved undo/redo | Stable large-note saved Undo removed the edit and Redo restored it. Short-note saved Undo failed as described above. |
| Native editor undo/redo | Host tests covered typed input, global selection/replacement, large deletion, formatting, table changes, rapid hardware undo/redo, durable descriptor history, and stale IME rejection. The manual ADB keyboard probe was not used as proof of whole-string undo grouping. |
| Recovery after process death | Added `RecoveryProbe` to the unsaved 50 MB editor, backgrounded and force-stopped the stable process, then reopened it. Saved detail did not contain the unsaved marker; reopening the editor recovered it. The recovered edit then saved successfully. |
| Unicode and export | Stable export preserved the complete **121 B** body, including emoji, Urdu, CJK, and CRLF. The exported file was 262 B because Cobalt intentionally adds 141 B of metadata. Body SHA-256: `c4603ba35dc43d3d207b8b2f2db6f60880cf457a6ad099d241523a4feaa9c697`. |
| Size limits and splitting | A 56 MB Open-with document was refused with the 55 MB limit message. Add → Import offered splitting; it saved two parts totaling 56 MB. Both clearly labeled parts appeared in Search. Boundary policies and UTF-8 split preservation also passed host tests. |
| Trash and restoration | Deleted only the synthetic short note. Trash reported **37 B**, including its current body and older version. Restored it and checked the saved text; then deleted it again and purged only that item. Trash returned to empty. |
| Explicit history cleanup | Host tests verified retention, current-body protection, draft/journal protection, missing-current handling, open file leases, and safe cleanup of unlinked files. Global stable history was not cleared. |
| Cache protection | Real Android manager calls retained leased scratch, saved media in cache, recent shares, and unknown recording files. Old exports were removed; idle known scratch was removed after releasing its lease. |
| Database optimization | On an isolated disk database, VACUUM reduced the main file from **1,462,272 B to 135,168 B**, returned free pages to zero, and preserved note metadata and verified body bytes. |
| Migration and concurrent edits | Android Room validated historical schema migration. Empty databases did not invent notes. Legacy large bodies, Unicode, stale section edits, and recover-as-new-note behavior passed. |
| Failed writes and interruptions | Android tests injected faults at atomic stages and verified that committed originals remained intact. Host tests covered cancellation, journal corruption, failed state publication, and stable snapshots during further edits. |
| Markdown tools | Device creation/editing/rendering checks passed for formatting controls and tables, including row/column changes, alignment, cancellation, and literal source versus preview. |
| Reader and selection UI | Reader initial-position assertion failed twice originally and again with complete-line matching and byte navigation. Captured semantics start at Paragraph 1057. The selection “More” test failed once in the combined suite and passed separately; it remains a flaky test. |

## Automated results and reproducibility

The host command `./tools/check-notes-low-memory.sh` passed **180 tests in 39 suites**, with no failures or skips. This included the notes package, resident/disk editor views, viewport spans, and Markdown reader sessions.

Android storage checks passed: **15 existing tests**, the new ten-edit 50 MB measurement, and the new cache/VACUUM test. The cache fixture initially assumed Android left free pages after DROP; Android auto-vacuum removed them immediately. The isolated test setup was corrected to create reclaimable pages explicitly, and the manager check then passed. This was a fixture correction, not an app defect.

Editor UI first run: four of six passed. The selection check passed on a separate rerun, giving five distinct passing UI checks. The improved reader check still failed before navigation. Together with the host and Android storage checks, the final distinct result is **202 passed / 1 failed**, with the separately noted selection flake. No failed test was deleted, skipped, or relabeled as a pass.

New reproducible checks are in [NoteLargeStorageDeepTest](../app/src/androidTest/kotlin/com/example/notes/NoteLargeStorageDeepTest.kt). The reader check is in [LazyNoteReaderTest](../app/src/androidTest/kotlin/com/example/notes/LazyNoteReaderTest.kt). Instrumentation uses `com.hanan_bhatti.cobalt.debug.test/androidx.test.runner.AndroidJUnitRunner`; the large fixture and maintenance fixture run in randomized sandboxes and clean up their own notes, files, databases, and preferences.

Evidence:

- [Host summary](../build/notes-deep-test-20261109/host-results.json) and [host log](../build/notes-deep-test-20261109/host-tests.log)
- [Large Android test](../build/notes-deep-test-20261109/device-large-test.log)
- [Existing Android storage tests](../build/notes-deep-test-20261109/device-storage-suite.log)
- [Maintenance test](../build/notes-deep-test-20261109/device-maintenance-test.log) and [measurements](../build/notes-deep-test-20261109/maintenance-measurements.json)
- [Editor UI first run](../build/notes-deep-test-20261109/device-editor-ui-suite.log) and [failure recheck](../build/notes-deep-test-20261109/device-ui-failure-recheck.log)
- [Final reader failure](../build/notes-deep-test-20261109/device-reader-final.log) and [reader semantics](../build/notes-deep-test-20261109/reader-failure-semantics.txt)
- [Unicode export verification](../build/notes-deep-test-20261109/unicode-export-verification.json)
- [Split completion](../build/notes-deep-test-20261109/stable-split-complete.png) and [both parts in Search](../build/notes-deep-test-20261109/stable-split-search.png)
- [Recovered editor](../build/notes-deep-test-20261109/stable-editor-recovered-after-crash.png), [restored short note](../build/notes-deep-test-20261109/stable-small-restored-body.png), and [empty Trash after selected purge](../build/notes-deep-test-20261109/stable-trash-purged.png)
- [Final stable Storage screen](../build/notes-deep-test-20261109/stable-storage-final.png)
- [UI event measurements](../build/notes-deep-test-20261109/ui-events.jsonl)

These are emulator, source, and fault-injection results. They do not establish real power-loss behavior, physical-device IME behavior, a disk-full stress result, or long-term memory stability. No Cobalt crash or ANR was observed during these checks. Seven synthetic test notes remain for review: the Unicode note, 5 MB note, edited 50 MB note, two split parts, rapid-typing reproduction, and slower typing control. The short deletion fixture was purged. Five external source fixtures were hash-checked unchanged and removed from Downloads after import; the Unicode export was kept. Existing notes and pre-existing recovery/history were retained. The emulator was left on the stable Storage screen.
