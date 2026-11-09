# Shared note storage implementation and validation

Implementation and device/release validation completed on November 9, 2026; final snapshot restoration is being verified.

This implements the [researched architecture](notes-storage-architecture-research-2026-11-09.md) after the [original audit](notes-storage-validation-2026-11-09.md). The research prototype measurements are not Android application measurements.

## What changed

Room schema 15 adds immutable 64 KiB text payloads, persistent AVL range trees, content roots, and durable saved Undo/Redo navigation. Existing Markdown files and delta history remain readable. Existing file bodies migrate lazily when reused by a shared checkpoint, with independent SHA verification before the verified duplicate is unlinked. There is no startup wipe or destructive migration.

Changed text and tree paths are stored once; generations reference unchanged ranges/subtrees. Bounded objects use integer references and SHA-256 digests. Current-head metadata, search derivatives and saved Undo navigation commit together. Saved metadata epochs advance monotonically, stale title/folder snapshots are rejected, and publication rechecks the observed head inside the commit transaction. Content stages durably before publication; an interrupted stage does not replace the saved head. Staged roots, recovery references, saved Undo, Trash and active reader leases protect retained data.

The full-body SHA contract is preserved. `FILE`/format 1 remains an adapter-level reference rather than changing existing callers to a tree digest. Schema 15 prevents an old APK from opening the upgraded database without a compatible migration. Downgrading requires restoring the matching complete backup.

Disk editor checkpoints provide range descriptors. Subsequent checkpoints share previously accepted additions rather than repeatedly storing growing typing buffers. Grouped live Undo retains only the changed descriptor interval. Stream and resident replacements use a bounded disposable spool and compare unchanged prefixes/suffixes, so insertions that shift downstream bytes still share text. External draft ranges stream as additions under the destination owner rather than assuming another note's hash belongs to it.

Quick Note retains live text/selection locally. Draft persistence completion updates durability metadata without replacing newer input. Debounced writes are revision-fenced and serialized, including clear/title/save. Draft stores use the same injected persistence database. The large reader exposes its list after the initial content batch, preventing the keyed loading footer from becoming the initial reading anchor.

## Durability and tradeoffs

Production uses framework SQLite rollback journaling (`TRUNCATE`) with `synchronous=EXTRA`. Authoritative shared-object writes assert synchronized commits on the actual writer. This avoids relying on an unknown vendor WAL-reset patch on API 24+ SQLite engines. Room and raw SupportSQLite access remain on one framework-managed writer. A future bundled-driver migration must cover every existing openHelper consumer before switching to concurrent WAL.

Full-document SHA verification remains synchronous on Save. Storage sharing improves retained bytes, but Save is still proportional to document size; the prototype's tree-only commit timings are not application Save latency. Stream-only fallback saves temporarily require a full incoming-body spool. Direct disk range checkpoints avoid that spool. Initial imports, exports and VACUUM still need substantial free space.

Live disk edits preserve the existing synced append data and checksummed atomic state publication. Saved Undo/Redo metadata commits with the head. Process-kill tests establish recovery of acknowledged durable state, not survival of every just-rendered keystroke or real hardware power loss. Filesystem/VFS/device sync guarantees and independent backups remain necessary. Shared history is not an independent backup against a corrupted shared payload.

The accepted-checkpoint descriptor cache is currently session-local. After a process restart, the first disk checkpoint rebuilds its sharing plan from the recovered descriptors; later checkpoints share accepted additions again. Storage object collection is explicit maintenance, not a per-keystroke database rewrite.

## Protected red cleanup

Location: Storage → Cache & database → Advanced cleanup (collapsed) → Clear disposable storage.

A review dialog explains the irreversible removal of disposable versions and requires a continuous five-second hold. Releasing, leaving bounds, focus loss, backgrounding or disposal cancels the hold. Keyboard Enter/Space also requires a continuous fresh hold. A regular click cannot bypass it. If note metadata changes while the dialog is open, cleanup refuses and requires another review.

The user's explicit policy is **protect unsaved writing**. Cleanup verifies every saved file head before deletion, recalculates eligible reviewed entries under the writer lock, clears saved navigation, removes eligible old versions/retired journals, collects unreachable shared objects, clears only known disposable cache, and shrinks the database if space permits. Current notes, attachments, folders/settings, Trash, active/recovery drafts, unknown unlinked data, unknown recordings, active scratch and recent shares remain protected. It does not call Android Clear Data or reset preferences. Protected historical versions can remain when needed by recovery or readers.

Storage accounting attributes each shared node/payload once, prioritizing current roots. Historical versions show their full referenced size separately from additional storage. Every physical representation of the current hash stays protected even when an obsolete legacy duplicate remains. Immutable chunks can retain unused subranges; this implementation does not yet repack partially referenced chunks during cleanup. Remaining database pages, indexes, free space and journal overhead are reported separately. Version-removal counts do not promise that their summed logical sizes equal physically reclaimed space.

## Verification

The original audit remains a historical record of the old system. The following results apply to this implementation.

- **193 distinct host tests passed.** The full scoped suite passed 192 tests; the subsequent final fixture run added the metadata epoch regression. The complete XML snapshot is retained separately from filtered reruns.
- Exactly **1,001 versions** of a 512 KB high-entropy body reconstructed after 1,000 small edits. Native 200-checkpoint sharing, shifted resident/stream insertions, Unicode preview boundaries, durable saved navigation, corruption detection, staged failure handling, stale/concurrent metadata rejection, reader leases and cleanup protection passed.
- **26 distinct Android checks passed:** 22 baseline storage/editor/reader/input/hold checks, plus corrected real-Android publication-fault and cleanup-preservation fixtures, the final disk-full checkpoint, and the 50 MB/100-edit fixture. Three initial fixture failures are documented below; they are not counted as successes.
- Actual Android cleanup retained Quick Note and section drafts, Trash, saved media in cache, an active reader, preferences and an unknown recording. Applying the protected section draft and restoring Trash verified exact contents afterward. Database integrity checked `ok`.
- Reader assertions for Paragraph 1 and distant Paragraph 2001 passed. Keyboard and pointer hold tests covered short hold, leaving bounds, background cancellation, successful five-second hold exactly once, and keyboard hold. Quick Note tests covered delayed draft echoes, selection ownership and 125 rapid inputs.
- **Real process termination passed at two deterministic frontiers:** during a partial live-editor insertion and after shared-object staging before head publication. The host force-stopped the debug process. Both reopened with the exact saved head and previously durable unsaved writing, working Undo/Redo, and SQLite integrity `ok`. These are real process kills, not simulated exceptions.
- **Installed minified release UI:** rapid hardware injection retained and saved all **125/125 characters**; the old audit retained 104/125. A short edit saved exactly; saved Undo restored the original; after force-stop and reopening, saved Redo restored the exact edited text. This verifies durable navigation through the release UI as well as through storage tests.

The first device fixtures failed because the sandbox prefixed an absolute database path twice and the editor token was not a UUID. Those fixture errors were corrected. The disk-full fixture initially closed a lazy PRAGMA cursor without executing it; reading and checking the returned cap triggered genuine `SQLITE_FULL` on the chunk INSERT. Android then auto-rolled back and its transaction-ending error masked the original failure. Shared transactions now preserve the original exception and attach a secondary rollback error. The corrected final Android fixture passed: a real SQLiteFullException left the saved head and durable live writing intact, editor reopening recovered exact writing, and retry succeeded after resetting the cap. Database integrity checked `ok`.

The installed release also recovered the unsaved `SHARED-RELEASE-V2` marker in the 50 MB native editor after force-stop. The saved detail retained the original before Save; the reopened editor retained the draft. Saving the recovered draft, saved Undo and saved Redo all worked. The release Storage screen remained at rounded **50 MB legacy history** before/after this edit; it did not add another full-body historical copy. Lazy conversion reduced rounded current notes/media from 1.2 GB to 1.1 GB; this repeated-text fixture deduplicates many identical chunks, so that reduction is not a high-entropy storage benchmark. Database/settings rose from rounded 2.0 MB to 2.4 MB. Exact measurements come from the independent random-data fixture.

### Production Android: 50 MB and 100 edits

The random printable **50,000,000 B** fixture exercised shifted insertions at the start, middle and end using production Android storage code. Every edit was compared with an independently updated file hash. All **101 versions** reconstructed before and after database reopen. Restoring an earlier version and the latest reused existing text with **zero payload growth**. Integrity checked `ok`.

| Measurement | Actual result |
| --- | ---: |
| Initial physical database | 51,499,008 B |
| Physical database after 100 edits | 51,748,864 B |
| **Incremental physical database** | **249,856 B (244 KiB)** |
| Incremental unique text payload | 2,190 B |
| Storage-screen history attribution | 140,658 B |
| Current shared payload/tree attribution | 50,153,066 B |
| Database/index/page overhead | 1,455,140 B |
| Save median / maximum | 7,144 ms / 65,046 ms |

This run took 2,226 seconds, including independent expected-file updates, full reconstruction of all versions twice, reopen/restore and integrity/accounting. Builds and release UI tests ran concurrently on the same host/emulator. Save timings therefore describe this loaded environment, not isolated hardware performance. No instant-save or performance acceptance claim is made; the preserved full-body SHA and verification scans still dominate large saves.

Runtime: SQLite **3.50.6**, journal `truncate`, synchronous **3 (EXTRA)**, page size 4,096 B. The older framework SQLite version reinforces the documented choice of rollback journaling over concurrent WAL.

### Final release cleanup

The final minified, non-debuggable APK was installed in place and its installed bytes matched the built artifact. A short tap left the review dialog open and did not clean anything. A continuous 5,300 ms pointer hold ran cleanup once and removed **7 disposable versions** plus safe cache.

The post-cleanup release library remained **67 items / 44 text**, including the one new synthetic Quick Note. Recovery remained **3.1 kB**, current notes/media remained rounded **1.1 GB**, and media amounts remained unchanged. History fell from **50 MB to 1.7 kB**; the remaining protected history stayed retained for recovery. Cache showed 6.2 MB immediately in maintenance, then 5.5 MB on the later storage scan as ordinary cache work settled. Database/settings fell from rounded 2.4 MB to 2.0 MB. The 50 MB saved body remained readable with its exact test marker after cleanup and process restart.

The Android crash buffer contained no Cobalt process-crash events, and the last-ANR report did not mention Cobalt. These checks are scoped observations of available buffers, not an absence-of-crashes guarantee across all devices.

The complete pre-test snapshot is being restored before final delivery so reviewed cleanup does not permanently remove the original library's historical versions or drafts. Final library/artifact verification is recorded below.

## Artifacts and preservation

The emulator is `emulator-5554`, API 37/x86_64, with a production image that does not permit root. Before upgrading, a complete AVD snapshot was saved as `cobalt-before-shared-storage-20261109`. A second snapshot, `cobalt-shared-release-before-tests-20261109`, preserves the upgraded release and the original 66-item library before new synthetic release notes or cleanup. Stable was updated in place with the matching signing certificate; no uninstall or Clear Data was used.

Private debug backups, original APK, release copy, logs, screenshots and measurement JSON are retained under ignored `build/notes-shared-validation-20261109/`. They are local evidence, not publishable fixture data.

- [Full host run and initial release build](../build/notes-shared-build-validation-final.log)
- [Final host fixture verification](../build/notes-shared-final-fixtures.log)
- [Baseline Android suite](../build/notes-shared-validation-20261109/device-basics.log)
- [Android publication and cleanup checks](../build/notes-shared-validation-20261109/device-storage-faults.log)
- [Final disk-full recovery/retry](../build/notes-shared-validation-20261109/device-sqlite-full-fixed.log)
- [50 MB/100-edit measurements](../build/notes-shared-validation-20261109/device-50mb-100-edits.json)
- [50 MB/100-edit Android log](../build/notes-shared-validation-20261109/device-50mb-100-edits.log)
- [Actual process-kill evidence after the final fix](../build/notes-shared-validation-20261109/process-death-final/)
- [Release large-note recovery/save/navigation](../build/notes-shared-validation-20261109/release-large-ui.json)
- [Release typing and saved navigation](../build/notes-shared-validation-20261109/release-typing.json)
- [Final release cleanup results](../build/notes-shared-validation-20261109/release-cleanup.json)
- [Final release build including vital lint](../build/notes-shared-release-final-retry.log)
- [Installed release artifact metadata](../build/notes-shared-validation-20261109/release-artifact.json)

No claim of hardware power-loss certification is made. Reliability depends on acknowledged synced writes, validation, recovery and independent backups; shared version history is deliberately not presented as an independent corruption backup.

Final APK SHA-256: `02cc3a1f7cb616c1167ee50e626e90b85fa50ea2c63f0bd9c1b15def3399c002`. The earlier release used for typing and large editor probes is retained separately; the final APK adds preservation of primary SQLite write failures. The 50 MB/100-edit debug measurement ran before that error-path-only fix; final debug verified SQLite-full recovery/retry and both actual process-kill phases afterward. The final release verified cleanup and retained large-body reading after restart. No benchmark from the separate research prototype is substituted for application measurements.
